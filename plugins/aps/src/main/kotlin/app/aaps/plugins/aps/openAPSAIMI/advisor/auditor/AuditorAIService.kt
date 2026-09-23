package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor

import android.content.Context
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * AIMI AI Decision Auditor - AI Service
 * ============================================================================
 * 
 * Handles communication with AI providers (OpenAI, Gemini, DeepSeek, Claude)
 * to get audit verdicts. Reuses existing infrastructure from AiCoachingService.
 */
@Singleton
class AuditorAIService @Inject constructor(
    private val preferences: Preferences,
    private val context: Context,
    private val auditorStatusLiveData: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStatusLiveData,
    private val geminiOAuthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
) {
    
    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

        private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        
        // Timeout for API calls (45s max per security audit to avoid blocking loop)
        private const val DEFAULT_TIMEOUT_MS = 45_000L
    }
    
    enum class Provider(val id: String, val displayName: String) {
        OPENAI("openai", "OpenAI (GPT-5.4 / GPT-5.2)"),
        GEMINI_37_FLASH("gemini-3.7-flash", "Gemini 3.7 Flash (High Speed)"),
        GEMINI_36_FLASH("gemini-3.6-flash", "Gemini 3.6 Flash (Fast)"),
        GEMINI_31_PRO("gemini-3.1-pro", "Gemini 3.1 Pro (Heavy Lifter)"),
        GEMINI_35_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash (Balanced)"),
        GEMINI_35_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite (Cost Efficient)"),
        GEMINI_20_FLASH("gemini-2.0-flash", "Gemini 2.0 Flash (Experimental)"),
        DEEPSEEK("deepseek", "DeepSeek (V4 / R2 / Chat)"),
        CLAUDE("claude", "Claude (Sonnet 5 / Opus 5 / 4.5)"),
        GLM("glm", "GLM (Zhipu AI GLM-5.2 / GLM-5)"),
        GROK("grok", "xAI Grok (Grok-4 / Grok-3)"),
        OPENROUTER("openrouter", "OpenRouter (Universal)"),
        GROQ("groq", "Groq (Ultra-Fast)"),
        CUSTOM("custom", "Custom (OpenAI-compatible)")
    }
    
    /**
     * Get audit verdict from AI provider
     */
    suspend fun getVerdict(
        input: AuditorInput,
        provider: Provider,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        useHighPerf: Boolean = false
    ): AuditorVerdict? = withContext(Dispatchers.IO) {
        
        // Get API key or OAuth Token
        val apiKey = getApiKey(provider)
        val useOAuth = preferences.get(app.aaps.core.keys.BooleanKey.OApsAIMIGeminiUseOAuth)
        val isGemini = provider in listOf(Provider.GEMINI_37_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH)
        val oauthToken = if (isGemini && useOAuth && geminiOAuthManager.isOAuthEnabled()) {
            geminiOAuthManager.getValidAccessToken()
        } else null

        if (apiKey.isBlank() && oauthToken == null && provider != Provider.CUSTOM) {
            AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.OFFLINE_NO_APIKEY)
            auditorStatusLiveData.notifyUpdate()
            return@withContext null
        }
        
        // Build prompt
        val prompt = AuditorPromptBuilder.buildPrompt(input)
        
        // --- ROCKET SAUVAGE: RETRY LOGIC (3 attempts) ---
        var lastException: Exception? = null
        val maxRetries = 3
        
        for (attempt in 1..maxRetries) {
            try {
                // Call AI with timeout
                val responseJson = withTimeoutOrNull(timeoutMs) {
                    when (provider) {
                        Provider.OPENAI -> callOpenAICompatible(apiKey, "https://api.openai.com/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH -> callGemini(apiKey, oauthToken, prompt, useHighPerf, provider)
                        Provider.DEEPSEEK -> callOpenAICompatible(apiKey, "https://api.deepseek.com/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.CLAUDE -> callClaude(apiKey, prompt, useHighPerf)
                        Provider.GLM -> callOpenAICompatible(apiKey, "https://open.bigmodel.cn/api/paas/v4/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.GROK -> callOpenAICompatible(apiKey, "https://api.x.ai/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.OPENROUTER -> callOpenAICompatible(apiKey, "https://openrouter.ai/api/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.GROQ -> callOpenAICompatible(apiKey, "https://api.groq.com/openai/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                        Provider.CUSTOM -> {
                            val url = preferences.get(StringKey.AimiAdvisorCustomUrl)
                            val model = preferences.get(StringKey.AimiAdvisorModel).ifBlank { "custom-model" }
                            callOpenAICompatible(apiKey, url, model, prompt)
                        }
                    }
                }
                
                if (responseJson != null) {
                    // Success!
                    try {
                        return@withContext parseVerdict(responseJson, provider)
                    } catch (e: Exception) {
                        AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.ERROR_PARSE)
                        auditorStatusLiveData.notifyUpdate()
                        return@withContext null
                    }
                } else {
                    throw java.net.SocketTimeoutException("Coroutine timeout after ${timeoutMs}ms")
                }

            } catch (e: Exception) {
                lastException = e
                val isRetryable = e is java.net.SocketTimeoutException || 
                                  e is java.io.IOException || 
                                  e is java.net.UnknownHostException
                                  
                if (attempt < maxRetries && isRetryable) {
                    val backoff = attempt * 2000L
                    kotlinx.coroutines.delay(backoff)
                } else {
                    break
                }
            }
        }
        
        when (lastException) {
            is java.net.UnknownHostException -> AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.OFFLINE_NO_NETWORK)
            is java.net.SocketTimeoutException -> AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.ERROR_TIMEOUT)
            is java.io.IOException -> AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.OFFLINE_NO_NETWORK)
            else -> AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.ERROR_EXCEPTION)
        }
        auditorStatusLiveData.notifyUpdate()
        return@withContext null
    }

    /**
     * Generic Chat Completion for AI Coaching and reasoning
     */
    suspend fun getChatCompletion(
        prompt: String,
        provider: Provider,
        useHighPerf: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(provider)
        val useOAuth = preferences.get(app.aaps.core.keys.BooleanKey.OApsAIMIGeminiUseOAuth)
        val isGemini = provider in listOf(Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH)
        val oauthToken = if (isGemini && useOAuth && geminiOAuthManager.isOAuthEnabled()) {
            geminiOAuthManager.getValidAccessToken()
        } else null

        try {
            val responseJson = when (provider) {
                Provider.OPENAI -> callOpenAICompatible(apiKey, "https://api.openai.com/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH -> callGemini(apiKey, oauthToken, prompt, useHighPerf, provider)
                Provider.DEEPSEEK -> callOpenAICompatible(apiKey, "https://api.deepseek.com/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.CLAUDE -> callClaude(apiKey, prompt, useHighPerf)
                Provider.GLM -> callOpenAICompatible(apiKey, "https://open.bigmodel.cn/api/paas/v4/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.GROK -> callOpenAICompatible(apiKey, "https://api.x.ai/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.OPENROUTER -> callOpenAICompatible(apiKey, "https://openrouter.ai/api/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.GROQ -> callOpenAICompatible(apiKey, "https://api.groq.com/openai/v1/chat/completions", getModel(provider, useHighPerf), prompt)
                Provider.CUSTOM -> {
                    val url = preferences.get(StringKey.AimiAdvisorCustomUrl)
                    val model = preferences.get(StringKey.AimiAdvisorModel).ifBlank { "custom-model" }
                    callOpenAICompatible(apiKey, url, model, prompt)
                }
            }

            val root = JSONObject(responseJson)
            return@withContext when (provider) {
                Provider.OPENAI, Provider.DEEPSEEK, Provider.GLM, Provider.GROK, Provider.OPENROUTER, Provider.GROQ, Provider.CUSTOM -> {
                    root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
                Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH -> {
                    root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                }
                Provider.CLAUDE -> {
                    root.getJSONArray("content").getJSONObject(0).getString("text")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    fun getApiKey(provider: Provider): String {
        return when (provider) {
            Provider.OPENAI -> preferences.get(StringKey.AimiAdvisorOpenAIKey)
            Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH ->
                preferences.get(StringKey.AimiAdvisorGeminiKey)
            Provider.DEEPSEEK -> preferences.get(StringKey.AimiAdvisorDeepSeekKey)
            Provider.CLAUDE -> preferences.get(StringKey.AimiAdvisorClaudeKey)
            Provider.GLM -> preferences.get(StringKey.AimiAdvisorGlmKey)
            Provider.GROK -> preferences.get(StringKey.AimiAdvisorGrokKey)
            Provider.OPENROUTER -> preferences.get(StringKey.AimiAdvisorOpenRouterKey)
            Provider.GROQ -> preferences.get(StringKey.AimiAdvisorGroqKey)
            Provider.CUSTOM -> preferences.get(StringKey.AimiAdvisorCustomKey)
        }
    }
    
    private fun getModel(provider: Provider, useHighPerf: Boolean): String {
        val userModel = preferences.get(StringKey.AimiAdvisorModel)
        val isGemini = provider in listOf(Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH)
        if (userModel.isNotBlank() && !isGemini) return userModel
        
        return when (provider) {
            Provider.OPENAI -> "gpt-5.4" 
            Provider.GEMINI_37_FLASH -> "gemini-3.7-flash"
            Provider.GEMINI_36_FLASH -> "gemini-3.6-flash"
            Provider.GEMINI_31_PRO -> "gemini-3.1-pro-preview"
            Provider.GEMINI_35_FLASH -> "gemini-3.5-flash"
            Provider.GEMINI_35_FLASH_LITE -> "gemini-3.5-flash-lite"
            Provider.GEMINI_20_FLASH -> "gemini-2.0-flash"
            Provider.DEEPSEEK -> "deepseek-v4"
            Provider.CLAUDE -> if (useHighPerf) "claude-sonnet-5" else "claude-sonnet-4-5"
            Provider.GLM -> "glm-5.2"
            Provider.GROK -> "grok-3"
            Provider.OPENROUTER -> "google/gemini-2.0-flash-exp:free"
            Provider.GROQ -> if (useHighPerf) "llama-3.3-70b-versatile" else "llama-3.1-8b-instant"
            Provider.CUSTOM -> ""
        }
    }

    private fun callOpenAICompatible(apiKey: String, baseUrl: String, model: String, prompt: String): String {
        val url = URL(baseUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            
            if (baseUrl.contains("openrouter.ai")) {
                setRequestProperty("HTTP-Referer", "https://aaps.app")
                setRequestProperty("X-Title", "AAPS AIMI Advisor")
            }
            
            connectTimeout = 15_000
            readTimeout = DEFAULT_TIMEOUT_MS.toInt()
        }
        
        val requestBody = JSONObject().apply {
            put("model", model) 
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 2048)
            put("temperature", 0.3)
            if (!baseUrl.contains("groq") && !baseUrl.contains("openrouter")) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
        
        OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
             val errorStream = connection.errorStream ?: connection.inputStream
             val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
             throw Exception("HTTP $responseCode: $errorResponse")
        }
        
        val response = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            var charsRead: Int
            while (reader.read(buffer).also { charsRead = it } != -1) {
                response.append(buffer, 0, charsRead)
            }
        }
        return response.toString()
    }
    
    private val geminiResolver = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver(context)

    private fun callGemini(apiKey: String?, oauthToken: String?, prompt: String, useHighPerf: Boolean, provider: Provider): String {
        val modelName = getModel(provider, useHighPerf)
        val primaryModel = geminiResolver.resolveGenerateContentModel(apiKey, oauthToken, modelName)
        
        try {
            return executeGeminiRequest(apiKey, oauthToken, prompt, primaryModel)
        } catch (e: Exception) {
            val fallbackModel = if (provider == Provider.GEMINI_35_FLASH_LITE) "gemini-3.5-flash-lite" else "gemini-1.5-flash"
            return executeGeminiRequest(apiKey, oauthToken, prompt, fallbackModel)
        }
    }

    private fun executeGeminiRequest(apiKey: String?, oauthToken: String?, prompt: String, modelId: String): String {
        val urlStr = if (oauthToken != null) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        } else {
            geminiResolver.getGenerateContentUrl(modelId, apiKey)
        }
        
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (oauthToken != null) {
                setRequestProperty("Authorization", "Bearer $oauthToken")
                geminiOAuthManager.getProjectId()?.let { 
                    if (it.isNotEmpty()) setRequestProperty("x-goog-user-project", it) 
                }
            }
            connectTimeout = 15_000
            readTimeout = 45_000
        }
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 8192)
                put("responseMimeType", "application/json")
            })
        }
        
        OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
             val response = StringBuilder()
             connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                 val buffer = CharArray(8192)
                 var charsRead: Int
                 while (reader.read(buffer).also { charsRead = it } != -1) {
                     response.append(buffer, 0, charsRead)
                 }
             }
             return response.toString()
        } else {
             val errorStream = connection.errorStream ?: connection.inputStream
             val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
             throw Exception("HTTP $responseCode: $errorResponse")
        }
    }
    
    private fun callClaude(apiKey: String, prompt: String, useHighPerf: Boolean): String {
        val model = getModel(Provider.CLAUDE, useHighPerf)
        val url = URL(CLAUDE_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 15_000
            readTimeout = DEFAULT_TIMEOUT_MS.toInt()
        }
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 2048)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        
        OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP $responseCode")
        }
        
        val response = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            var charsRead: Int
            while (reader.read(buffer).also { charsRead = it } != -1) {
                response.append(buffer, 0, charsRead)
            }
        }
        return response.toString()
    }
    
    private fun parseVerdict(responseJson: String, provider: Provider): AuditorVerdict {
        val root = JSONObject(responseJson)
        
        val contentJson = when (provider) {
            Provider.OPENAI, Provider.DEEPSEEK, Provider.GLM, Provider.GROK, Provider.OPENROUTER, Provider.GROQ, Provider.CUSTOM -> {
                root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
            Provider.GEMINI_37_FLASH, Provider.GEMINI_36_FLASH, Provider.GEMINI_31_PRO, Provider.GEMINI_35_FLASH, Provider.GEMINI_35_FLASH_LITE, Provider.GEMINI_20_FLASH -> {
                root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
            Provider.CLAUDE -> {
                root.getJSONArray("content").getJSONObject(0).getString("text")
            }
        }
        
        val jsonStr = if (contentJson.contains("```json")) {
            contentJson.substringAfter("```json").substringBefore("```").trim()
        } else if (contentJson.contains("```")) {
            contentJson.substringAfter("```").substringBefore("```").trim()
        } else {
            contentJson.trim()
        }
        
        val verdictJson = JSONObject(jsonStr)
        return AuditorVerdict.fromJSON(verdictJson)
    }

    suspend fun fetchAvailableModels(provider: Provider): List<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(provider)
        val baseUrl = when (provider) {
            Provider.OPENAI -> "https://api.openai.com/v1/models"
            Provider.DEEPSEEK -> "https://api.deepseek.com/v1/models"
            Provider.GLM -> "https://open.bigmodel.cn/api/paas/v4/models"
            Provider.GROK -> "https://api.x.ai/v1/models"
            Provider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            Provider.GROQ -> "https://api.groq.com/openai/v1/models"
            Provider.CUSTOM -> {
                val url = preferences.get(StringKey.AimiAdvisorCustomUrl).trim()
                if (url.isBlank()) return@withContext emptyList()
                if (url.contains("/chat/completions")) url.replace("/chat/completions", "/models")
                else if (url.endsWith("/")) "${url}models"
                else "$url/models"
            }
            else -> "https://api.openai.com/v1/models"
        }

        try {
            val url = URL(baseUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 8000
                readTimeout = 12000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(response)
                val models = mutableListOf<String>()
                if (root.has("data")) {
                    val data = root.getJSONArray("data")
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        if (item.has("id")) {
                            models.add(item.getString("id"))
                        }
                    }
                }
                return@withContext models.sorted()
            } else {
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}
