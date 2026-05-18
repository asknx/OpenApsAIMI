package app.aaps.plugins.aps.openAPSAIMI.physio

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.StringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🤖 AIMI LLM Physiological Analyzer - MTR Implementation
 */
@Singleton
class AIMILLMPhysioAnalyzerMTR @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val context: android.content.Context,
    private val geminiOAuthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
) {
    
    companion object {
        private const val TAG = "LLMPhysioAnalyzer"
        private const val TIMEOUT_MS = 10_000L
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
    }

    private val geminiResolver = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver(context)

    fun analyze(
        features: PhysioFeaturesMTR,
        baseline: PhysioBaselineMTR,
        context: PhysioContextMTR
    ): String {
        val provider = sp.getString(StringKey.AimiPhysioLLMProvider.key, "gpt4")
        val apiKey = getAPIKey(provider)
        
        val useOAuth = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIGeminiUseOAuth.key, false)
        val oauthToken = if (provider == "gemini" && useOAuth && geminiOAuthManager.isOAuthEnabled()) {
            runBlocking { geminiOAuthManager.getValidAccessToken() }
        } else null

        if (apiKey.isBlank() && oauthToken == null) {
            aapsLogger.warn(LTag.APS, "[$TAG] No API key or token configured for $provider")
            return ""
        }
        
        return try {
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        when (provider) {
                            "gpt4" -> analyzeWithGPT(features, baseline, context, apiKey)
                            "gemini" -> analyzeWithGemini(features, baseline, context, apiKey, oauthToken)
                            "claude" -> analyzeWithClaude(features, baseline, context, apiKey)
                            "deepseek" -> analyzeWithDeepSeek(features, baseline, context, apiKey)
                            else -> ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.warn(LTag.APS, "[$TAG] LLM analysis failed", e)
            ""
        }
    }

    private fun analyzeWithGemini(
        features: PhysioFeaturesMTR,
        baseline: PhysioBaselineMTR,
        context: PhysioContextMTR,
        apiKey: String,
        oauthToken: String?
    ): String {
        val prompt = buildPrompt(features, baseline, context)
        val primaryModel = geminiResolver.resolveGenerateContentModel(apiKey, oauthToken, "gemini-3.1-pro")
        
        return try {
            executeGeminiRequest(apiKey, oauthToken, prompt, primaryModel)
        } catch (e: Exception) {
            executeGeminiRequest(apiKey, oauthToken, prompt, "gemini-3.1-flash")
        }
    }

    private fun executeGeminiRequest(apiKey: String?, oauthToken: String?, prompt: String, modelId: String): String {
        val urlStr = if (oauthToken != null) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        } else {
            geminiResolver.getGenerateContentUrl(modelId, apiKey)
        }
        
        val requestBody = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 150)
                put("temperature", 0.3)
            })
        }
        
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        if (oauthToken != null) {
            headers["Authorization"] = "Bearer $oauthToken"
            geminiOAuthManager.getProjectId()?.let { 
                if (it.isNotEmpty()) headers["x-goog-user-project"] = it
            }
        }
        
        val response = makeAPICall(urlStr, requestBody.toString(), headers)
        return parseGeminiResponse(response)
    }

    private fun analyzeWithGPT(features: PhysioFeaturesMTR, baseline: PhysioBaselineMTR, context: PhysioContextMTR, apiKey: String): String {
        val prompt = buildPrompt(features, baseline, context)
        val requestBody = JSONObject().apply {
            put("model", "gpt-4")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an expert diabetes physiologist. Brief insights only.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_completion_tokens", 150)
            put("temperature", 0.3)
        }
        val response = makeAPICall(OPENAI_API_URL, requestBody.toString(), mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json"))
        return parseGPTResponse(response)
    }

    private fun analyzeWithClaude(features: PhysioFeaturesMTR, baseline: PhysioBaselineMTR, context: PhysioContextMTR, apiKey: String): String {
        val prompt = buildPrompt(features, baseline, context)
        val requestBody = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 150)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }
        val response = makeAPICall(CLAUDE_API_URL, requestBody.toString(), mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json"))
        return parseClaudeResponse(response)
    }

    private fun analyzeWithDeepSeek(features: PhysioFeaturesMTR, baseline: PhysioBaselineMTR, context: PhysioContextMTR, apiKey: String): String {
        val prompt = buildPrompt(features, baseline, context)
        val requestBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "Expert physiologist. Brief insights.") })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("max_tokens", 150)
        }
        val response = makeAPICall(DEEPSEEK_API_URL, requestBody.toString(), mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json"))
        return parseGPTResponse(response)
    }

    private fun buildPrompt(features: PhysioFeaturesMTR, baseline: PhysioBaselineMTR, context: PhysioContextMTR): String {
        return "Metrics: Sleep=${features.sleepDurationHours}h, HRV=${features.hrvMeanRMSSD}ms. State: ${context.state}."
    }

    private fun parseGPTResponse(response: String): String {
        return try {
            JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) { "" }
    }

    private fun parseGeminiResponse(response: String): String {
        return try {
            JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        } catch (e: Exception) { "" }
    }

    private fun parseClaudeResponse(response: String): String {
        return try {
            JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text").trim()
        } catch (e: Exception) { "" }
    }

    private fun makeAPICall(url: String, body: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS.toInt()
            connection.readTimeout = TIMEOUT_MS.toInt()
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode != 200) throw Exception("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getAPIKey(provider: String): String {
        return when (provider) {
            "gpt4" -> sp.getString("aimi_openai_api_key", "")
            "gemini" -> sp.getString("aimi_gemini_api_key", "")
            "claude" -> sp.getString("aimi_claude_api_key", "")
            "deepseek" -> sp.getString("aimi_deepseek_api_key", "")
            else -> ""
        }
    }
}
