package app.aaps.plugins.aps.openAPSAIMI.llm.gemini

import android.content.Context
import android.net.Uri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.core.keys.StringKey

@Singleton
class GeminiOAuthManager @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val context: Context
) {
    
    companion object {
        private const val DEFAULT_CLIENT_ID = "705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com"
        private const val REDIRECT_PORT = 8081 // Different port than Drive
        private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/oauth/callback"
        // Using the specifically enabled .peruserquota scope as seen in the user's screenshot.
        // The base generative-language scope was rejected as invalid for this project type.
        private const val SCOPE = "https://www.googleapis.com/auth/generative-language.peruserquota https://www.googleapis.com/auth/cloud-platform email openid"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        
        // SharedPreferences keys
        private const val PREF_GEMINI_REFRESH_TOKEN = "gemini_oauth_refresh_token"
        private const val PREF_GEMINI_ACCESS_TOKEN = "gemini_oauth_access_token"
        private const val PREF_GEMINI_TOKEN_EXPIRY = "gemini_oauth_token_expiry"
        private const val PREF_GEMINI_AUTH_STATE = "gemini_oauth_state"
        private const val PREF_GEMINI_CODE_VERIFIER = "gemini_oauth_code_verifier"
    }

    private fun getClientId(): String {
        val custom = sp.getString(StringKey.AimiGeminiOAuthClientId.key, "")
        return if (custom.isNotBlank()) custom else DEFAULT_CLIENT_ID
    }

    private fun getClientSecret(): String? {
        val custom = sp.getString(StringKey.AimiGeminiOAuthClientSecret.key, "")
        return if (custom.isNotBlank()) custom else null
    }
    
    private val client = OkHttpClient()
    private var localServer: ServerSocket? = null
    private var authCodeReceived: String? = null
    private var serverJob: Job? = null
    
    fun hasValidRefreshToken(): Boolean {
        return sp.getString(PREF_GEMINI_REFRESH_TOKEN, "").isNotBlank()
    }
    
    fun isOAuthEnabled(): Boolean {
        return sp.getBoolean("gemini_oauth_enabled", false)
    }

    fun setOAuthEnabled(enabled: Boolean) {
        sp.putBoolean("gemini_oauth_enabled", enabled)
    }

    suspend fun startPKCEAuth(): String {
        return withContext(Dispatchers.IO) {
            try {
                startLocalServer()
                val codeVerifier = generateCodeVerifier()
                val codeChallenge = generateCodeChallenge(codeVerifier)
                sp.putString(PREF_GEMINI_CODE_VERIFIER, codeVerifier)
                val authUrl = buildAuthUrl(codeChallenge)
                authUrl
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "GeminiAuth: Error starting PKCE", e)
                throw e
            }
        }
    }
    
    suspend fun exchangeCodeForTokens(authCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val codeVerifier = sp.getString(PREF_GEMINI_CODE_VERIFIER, "")
                val requestBody = FormBody.Builder()
                    .add("client_id", getClientId())
                    .also { builder ->
                         getClientSecret()?.let { builder.add("client_secret", it) }
                    }
                    .add("code", authCode)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", REDIRECT_URI)
                    .build()
                
                val request = Request.Builder().url(TOKEN_URL).post(requestBody).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    sp.putString(PREF_GEMINI_REFRESH_TOKEN, json.optString("refresh_token"))
                    sp.putString(PREF_GEMINI_ACCESS_TOKEN, json.optString("access_token"))
                    sp.putLong(PREF_GEMINI_TOKEN_EXPIRY, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                    sp.remove(PREF_GEMINI_CODE_VERIFIER)
                    setOAuthEnabled(true)
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "GeminiAuth: Token exchange failed", e)
                false
            }
        }
    }
    
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val cachedToken = sp.getString(PREF_GEMINI_ACCESS_TOKEN, "")
            val expiry = sp.getLong(PREF_GEMINI_TOKEN_EXPIRY, 0)

            if (cachedToken.isNotEmpty() && System.currentTimeMillis() < expiry - 300_000) {
                return@withContext cachedToken
            }

            val refreshToken = sp.getString(PREF_GEMINI_REFRESH_TOKEN, "")
            if (refreshToken.isEmpty()) return@withContext null

            val requestBody = FormBody.Builder()
                .add("client_id", getClientId())
                .also { builder ->
                     getClientSecret()?.let { builder.add("client_secret", it) }
                }
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder().url(TOKEN_URL).post(requestBody).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val newAccessToken = json.optString("access_token")
                sp.putString(PREF_GEMINI_ACCESS_TOKEN, newAccessToken)
                sp.putLong(PREF_GEMINI_TOKEN_EXPIRY, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                return@withContext newAccessToken
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun getProjectId(): String? {
        val custom = sp.getString(StringKey.AimiGeminiOAuthProjectId.key, "")
        if (custom.isNotEmpty()) return custom
        return null
    }
    
    fun logout() {
        sp.remove(PREF_GEMINI_REFRESH_TOKEN)
        sp.remove(PREF_GEMINI_ACCESS_TOKEN)
        sp.remove(PREF_GEMINI_TOKEN_EXPIRY)
        setOAuthEnabled(false)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
    
    private fun buildAuthUrl(codeChallenge: String): String {
        val state = UUID.randomUUID().toString()
        sp.putString(PREF_GEMINI_AUTH_STATE, state)
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", getClientId())
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build().toString()
    }
    
    private fun startLocalServer() {
        stopLocalServer()
        localServer = ServerSocket(REDIRECT_PORT)
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            val server = localServer ?: return@launch
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    launch { handleHttpRequest(socket) }
                } catch (_: Exception) {}
            }
        }
    }
    
    private fun stopLocalServer() {
        serverJob?.cancel()
        localServer?.close()
        localServer = null
    }
    
    private suspend fun handleHttpRequest(socket: Socket) {
        socket.use {
            val input = it.getInputStream().bufferedReader()
            val output = it.getOutputStream()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size >= 2 && parts[1].startsWith("/oauth/callback")) {
                val fullUrl = "http://localhost" + parts[1]
                val uri = Uri.parse(fullUrl)
                val code = uri.getQueryParameter("code")
                val state = uri.getQueryParameter("state")
                
                val savedState = sp.getString(PREF_GEMINI_AUTH_STATE, "")
                aapsLogger.debug(LTag.CORE, "GeminiAuth: Received state: $state, Expected: $savedState")
                
                if (code != null && state == savedState) {
                    authCodeReceived = code
                    sendHttpResponse(output, "Auth successful! return to AAPS.")
                } else {
                    aapsLogger.error(LTag.CORE, "GeminiAuth: State mismatch or missing code. code=$code, state=$state, saved=$savedState")
                    sendHttpResponse(output, "Auth failed.")
                }
            }
        }
    }
    
    private fun sendHttpResponse(output: OutputStream, message: String) {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body>$message</body></html>"
        output.write(response.toByteArray())
    }

    suspend fun waitForAuthCode(timeoutMs: Long = 60000): String? {
        val start = System.currentTimeMillis()
        while (authCodeReceived == null && System.currentTimeMillis() - start < timeoutMs) {
            delay(500)
        }
        return authCodeReceived.also { authCodeReceived = null; stopLocalServer() }
    }
}
