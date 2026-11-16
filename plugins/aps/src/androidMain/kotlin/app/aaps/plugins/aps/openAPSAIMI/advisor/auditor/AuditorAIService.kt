package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ============================================================================
 * AIMI AI Decision Auditor - AI Service
 * ============================================================================
 * 
 * Handles communication with AI providers (OpenAI, Gemini, DeepSeek, Claude, etc.)
 * to get audit verdicts and clinical chat completions.
 */
@SingleIn(AppScope::class)
class AuditorAIService @Inject constructor(
    private val preferences: Preferences,
    private val context: Context,
    private val auditorStatusLiveData: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorStatusLiveData
) {
    
    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        
        // Timeout for API calls (45s max per security audit to avoid blocking loop)
        private const val DEFAULT_TIMEOUT_MS = 45_000L

        fun normalizeEndpointUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) {
                throw IllegalArgumentException("Кастомный Endpoint URL не задан в настройках AIMI")
            }
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw IllegalArgumentException("Кастомный Endpoint URL должен начинаться с http:// или https://: $trimmed")
            }
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/v1/chat/completions"
            }
        }
    }
    
    enum class Provider(val id: String, val displayName: String) {
        OPENAI("openai", "OpenAI (GPT-6 Astra / Sol)"),
        GEMINI_38_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash (Быстрый)"),
        GEMINI_31_PRO("gemini-3.1-pro", "Gemini 3.1 Pro (Глубокий)"),
        DEEPSEEK("deepseek", "DeepSeek (V4 / Chat)"),
        CLAUDE("claude", "Claude (Sonnet 5 / 3.7)"),
        GLM("glm", "GLM (Zhipu AI GLM-5.2)"),
        GROK("grok", "xAI Grok (Grok-4)"),
        OPENROUTER("openrouter", "OpenRouter"),
        GROQ("groq", "Groq"),
        CUSTOM("custom", "Кастомный Endpoint")
    }
    
    /**
     * Get audit verdict from AI provider (structured JSON response required)
     */
    suspend fun getVerdict(
        input: AuditorInput,
        provider: Provider,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        useHighPerf: Boolean = false
    ): AuditorVerdict? = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey(provider)
        if (apiKey.isBlank() && provider != Provider.CUSTOM) {
            AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.OFFLINE_NO_APIKEY)
            auditorStatusLiveData.notifyUpdate()
            return@withContext null
        }

        if (provider == Provider.CUSTOM) {
            val rawUrl = preferences.get(StringKey.AimiAdvisorCustomUrl)
            if (rawUrl.isBlank() || (!rawUrl.trim().startsWith("http://") && !rawUrl.trim().startsWith("https://"))) {
                AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.ERROR_EXCEPTION)
                auditorStatusLiveData.notifyUpdate()
                return@withContext null
            }
        }
        
        val prompt = AuditorPromptBuilder.buildPrompt(input)
        
        var lastException: Exception? = null
        val maxRetries = 3
        
        for (attempt in 1..maxRetries) {
            try {
                val responseJson = withTimeoutOrNull(timeoutMs) {
                    when (provider) {
                        Provider.OPENAI -> callOpenAICompatible(apiKey, "https://api.openai.com/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> callGemini(apiKey, prompt, useHighPerf, provider, requireJson = true)
                        Provider.DEEPSEEK -> callOpenAICompatible(apiKey, "https://api.deepseek.com/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.CLAUDE -> callClaude(apiKey, prompt, useHighPerf)
                        Provider.GLM -> callOpenAICompatible(apiKey, "https://open.bigmodel.cn/api/paas/v4/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.GROK -> callOpenAICompatible(apiKey, "https://api.x.ai/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.OPENROUTER -> callOpenAICompatible(apiKey, "https://openrouter.ai/api/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.GROQ -> callOpenAICompatible(apiKey, "https://api.groq.com/openai/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = true)
                        Provider.CUSTOM -> {
                            val rawUrl = preferences.get(StringKey.AimiAdvisorCustomUrl)
                            val url = normalizeEndpointUrl(rawUrl)
                            val model = getActiveModel(Provider.CUSTOM)
                            callOpenAICompatible(apiKey, url, model, prompt, requireJson = true)
                        }
                    }
                }
                
                if (responseJson != null) {
                    try {
                        return@withContext parseVerdict(responseJson, provider)
                    } catch (e: Exception) {
                        AuditorStatusTracker.updateStatus(AuditorStatusTracker.Status.ERROR_PARSE)
                        auditorStatusLiveData.notifyUpdate()
                        return@withContext null
                    }
                } else {
                    throw java.net.SocketTimeoutException("Timeout after ${timeoutMs}ms")
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
     * Generic Chat Completion for AI Coaching and reasoning (natural language text output)
     */
    suspend fun getChatCompletion(
        prompt: String,
        provider: Provider,
        useHighPerf: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(provider)

        if (provider == Provider.CUSTOM) {
            val rawUrl = preferences.get(StringKey.AimiAdvisorCustomUrl)
            if (rawUrl.isBlank() || (!rawUrl.trim().startsWith("http://") && !rawUrl.trim().startsWith("https://"))) {
                return@withContext "⚠️ Ошибка AI (${provider.displayName}): Кастомный Endpoint URL не задан в настройках AIMI"
            }
        }

        try {
            val responseJson = when (provider) {
                Provider.OPENAI -> callOpenAICompatible(apiKey, "https://api.openai.com/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> callGemini(apiKey, prompt, useHighPerf, provider, requireJson = false)
                Provider.DEEPSEEK -> callOpenAICompatible(apiKey, "https://api.deepseek.com/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.CLAUDE -> callClaude(apiKey, prompt, useHighPerf)
                Provider.GLM -> callOpenAICompatible(apiKey, "https://open.bigmodel.cn/api/paas/v4/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.GROK -> callOpenAICompatible(apiKey, "https://api.x.ai/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.OPENROUTER -> callOpenAICompatible(apiKey, "https://openrouter.ai/api/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.GROQ -> callOpenAICompatible(apiKey, "https://api.groq.com/openai/v1/chat/completions", getModel(provider, useHighPerf), prompt, requireJson = false)
                Provider.CUSTOM -> {
                    val rawUrl = preferences.get(StringKey.AimiAdvisorCustomUrl)
                    val url = normalizeEndpointUrl(rawUrl)
                    val model = getActiveModel(Provider.CUSTOM)
                    callOpenAICompatible(apiKey, url, model, prompt, requireJson = false)
                }
            }

            val root = JSONObject(responseJson)
            val extractedText: String = when (provider) {
                Provider.OPENAI, Provider.DEEPSEEK, Provider.GLM, Provider.GROK, Provider.OPENROUTER, Provider.GROQ, Provider.CUSTOM -> {
                    val choices = root.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        choices.getJSONObject(0).getJSONObject("message").getString("content")
                    } else {
                        throw Exception("No choices in AI response: $responseJson")
                    }
                }
                Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> {
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val textBuilder = StringBuilder()
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    val partText = part.getString("text")
                                    if (partText.isNotBlank()) {
                                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                                        textBuilder.append(partText)
                                    }
                                }
                            }
                        }
                        if (textBuilder.isNotEmpty()) textBuilder.toString() else "Ответ получен, но пуст."
                    } else {
                        throw Exception("No candidates in Gemini response: $responseJson")
                    }
                }
                Provider.CLAUDE -> {
                    val contentArr = root.optJSONArray("content")
                    if (contentArr != null && contentArr.length() > 0) {
                        contentArr.getJSONObject(0).getString("text")
                    } else {
                        throw Exception("No content in Claude response: $responseJson")
                    }
                }
            }
            return@withContext extractedText
        } catch (e: Exception) {
            val errorMsg = "Ошибка AI (${provider.displayName}): ${e.message ?: e.javaClass.simpleName}"
            android.util.Log.e("AuditorAIService", errorMsg, e)
            return@withContext "⚠️ $errorMsg"
        }
    }
    
    fun getApiKey(provider: Provider): String {
        return when (provider) {
            Provider.OPENAI -> preferences.get(StringKey.AimiAdvisorOpenAIKey)
            Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO ->
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
    
    fun getProvider(): Provider {
        val providerName = preferences.get(StringKey.AimiAdvisorProvider).uppercase()
        return when {
            providerName.contains("OPENAI") -> Provider.OPENAI
            providerName.contains("GEMINI-3.8-FLASH") -> Provider.GEMINI_38_FLASH
            providerName.contains("GEMINI-3.1-PRO") -> Provider.GEMINI_31_PRO
            providerName.contains("DEEPSEEK") -> Provider.DEEPSEEK
            providerName.contains("CLAUDE") -> Provider.CLAUDE
            providerName.contains("GLM") -> Provider.GLM
            providerName.contains("GROK") -> Provider.GROK
            providerName.contains("OPENROUTER") -> Provider.OPENROUTER
            providerName.contains("GROQ") -> Provider.GROQ
            providerName.contains("CUSTOM") -> Provider.CUSTOM
            else -> Provider.GEMINI_38_FLASH
        }
    }

    fun getActiveModel(provider: Provider = getProvider()): String {
        val model = when (provider) {
            Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> preferences.get(StringKey.AimiAdvisorModelGemini).ifBlank { "gemini-3.8-flash" }
            Provider.OPENAI -> preferences.get(StringKey.AimiAdvisorModelOpenAI).ifBlank { "gpt-6.1-sol" }
            Provider.DEEPSEEK -> preferences.get(StringKey.AimiAdvisorModelDeepSeek).ifBlank { "deepseek-v4-pro" }
            Provider.CLAUDE -> preferences.get(StringKey.AimiAdvisorModelClaude).ifBlank { "claude-sonnet-4-6" }
            Provider.GLM -> preferences.get(StringKey.AimiAdvisorModelGlm).ifBlank { "glm-5.3" }
            Provider.GROK -> preferences.get(StringKey.AimiAdvisorModelGrok).ifBlank { "grok-4.7" }
            Provider.OPENROUTER -> preferences.get(StringKey.AimiAdvisorModelOpenRouter).ifBlank { "anthropic/claude-sonnet-4-6" }
            Provider.GROQ -> preferences.get(StringKey.AimiAdvisorModelGroq).ifBlank { "llama-3.3-70b-versatile" }
            Provider.CUSTOM -> {
                val manual = preferences.get(StringKey.AimiAdvisorModelCustomManual).trim()
                if (manual.isNotBlank()) manual
                else preferences.get(StringKey.AimiAdvisorModelCustom).ifBlank { "custom-model" }
            }
        }
        if (preferences.get(StringKey.AimiAdvisorModel) != model) {
            preferences.put(StringKey.AimiAdvisorModel, model)
        }
        return model
    }
    
    private fun getModel(provider: Provider, useHighPerf: Boolean = false): String {
        return getActiveModel(provider)
    }

    private fun callOpenAICompatible(apiKey: String, baseUrl: String, model: String, prompt: String, requireJson: Boolean): String {
        val url = URL(baseUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
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
            if (requireJson && !baseUrl.contains("groq") && !baseUrl.contains("openrouter")) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
        
        OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
             val errorStream = connection.errorStream ?: connection.inputStream
             val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
             val sanitized = if (baseUrl.contains("?key=")) baseUrl.substringBefore("?key=") + "?key=REDACTED" else baseUrl
             throw Exception("[URL: $sanitized | HTTP $responseCode: $errorResponse]")
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

    private fun callGemini(apiKey: String?, prompt: String, useHighPerf: Boolean, provider: Provider, requireJson: Boolean): String {
        val modelName = getModel(provider, useHighPerf)
        val primaryModel = geminiResolver.resolveGenerateContentModel(apiKey, null, modelName)
        
        return try {
            executeGeminiRequest(apiKey, prompt, primaryModel, requireJson)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota")) {
                val fallbackModel = if (primaryModel != "gemini-3.5-flash-lite") "gemini-3.5-flash-lite" else "gemini-3.8-flash"
                return executeGeminiRequest(apiKey, prompt, fallbackModel, requireJson)
            }
            throw e
        }
    }

    private fun executeGeminiRequest(apiKey: String?, prompt: String, modelId: String, requireJson: Boolean): String {
        val urlStr = geminiResolver.getGenerateContentUrl(modelId, apiKey)
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
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
                if (requireJson) {
                    put("responseMimeType", "application/json")
                }
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
             val sanitized = if (urlStr.contains("?key=")) urlStr.substringBefore("?key=") + "?key=REDACTED" else urlStr
             throw Exception("[Gemini URL: $sanitized | HTTP $responseCode: $errorResponse]")
        }
    }
    
    private fun callClaude(apiKey: String, prompt: String, useHighPerf: Boolean): String {
        val model = getModel(Provider.CLAUDE, useHighPerf)
        val url = URL(CLAUDE_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
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
            val errorStream = connection.errorStream ?: connection.inputStream
            val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            throw Exception("[Claude HTTP $responseCode: $errorResponse]")
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
            Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> {
                val candidates = root.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val textBuilder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                }
                textBuilder.toString()
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

        when (provider) {
            Provider.GEMINI_38_FLASH, Provider.GEMINI_31_PRO -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                val urlStr = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                try {
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(resp)
                        val models = mutableListOf<String>()
                        val modelsArray = root.optJSONArray("models")
                        if (modelsArray != null) {
                            for (i in 0 until modelsArray.length()) {
                                val item = modelsArray.getJSONObject(i)
                                val name = item.optString("name", "")
                                val methods = item.optJSONArray("supportedGenerationMethods")
                                val canGenerate = methods != null && (0 until methods.length()).any {
                                    methods.getString(it) == "generateContent"
                                }
                                if (canGenerate && name.isNotBlank()) {
                                    models.add(name.removePrefix("models/"))
                                }
                            }
                        }
                        if (models.isNotEmpty()) return@withContext models.sorted()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuditorAIService", "Error fetching Gemini models: ${e.message}", e)
                }
                return@withContext listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro")
            }

            Provider.CLAUDE -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                val urlStr = "https://api.anthropic.com/v1/models"
                try {
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        setRequestProperty("x-api-key", apiKey)
                        setRequestProperty("anthropic-version", "2023-06-01")
                        connectTimeout = 10_000
                        readTimeout = 15_000
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(resp)
                        val models = mutableListOf<String>()
                        val dataArray = root.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val id = item.optString("id", "")
                                if (id.isNotBlank()) models.add(id)
                            }
                        }
                        if (models.isNotEmpty()) return@withContext models.sorted()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuditorAIService", "Error fetching Claude models: ${e.message}", e)
                }
                return@withContext listOf("claude-fable-5-2", "claude-opus-5-5", "claude-sonnet-5-5", "claude-sonnet-4-6", "claude-haiku-5-5")
            }

            Provider.DEEPSEEK -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                return@withContext fetchOpenAiCompatibleModels("https://api.deepseek.com/models", apiKey, listOf("deepseek-v4-pro", "deepseek-v4.1-flash-1017", "deepseek-v4.1-flash"))
            }

            Provider.OPENAI -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                return@withContext fetchOpenAiCompatibleModels("https://api.openai.com/v1/models", apiKey, listOf("gpt-6.1-sol", "gpt-6.1-terra", "gpt-5.5-turbo"))
            }

            Provider.GLM -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                return@withContext fetchOpenAiCompatibleModels("https://open.bigmodel.cn/api/paas/v4/models", apiKey, listOf("glm-5.3", "glm-5.3-flash", "glm-4.7-flash"))
            }

            Provider.GROK -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                return@withContext fetchOpenAiCompatibleModels("https://api.x.ai/v1/models", apiKey, listOf("grok-4.7", "grok-4.6", "grok-4.3", "grok-4.20"))
            }

            Provider.OPENROUTER -> {
                return@withContext fetchOpenAiCompatibleModels("https://openrouter.ai/api/v1/models", apiKey, listOf("anthropic/claude-sonnet-4-6", "google/gemini-3.8-flash", "deepseek/deepseek-v4-pro"))
            }

            Provider.GROQ -> {
                if (apiKey.isBlank()) return@withContext emptyList()
                return@withContext fetchOpenAiCompatibleModels("https://api.groq.com/openai/v1/models", apiKey, listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"))
            }

            Provider.CUSTOM -> {
                val rawUrl = preferences.get(StringKey.AimiAdvisorCustomUrl).trim()
                if (rawUrl.isBlank() || (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://"))) return@withContext emptyList()
                val baseUrl = if (rawUrl.contains("/chat/completions")) rawUrl.replace("/chat/completions", "/models")
                else if (rawUrl.endsWith("/")) "${rawUrl}models"
                else "$rawUrl/models"
                return@withContext fetchOpenAiCompatibleModels(baseUrl, apiKey, listOf("custom-model"))
            }
        }
    }

    private fun fetchOpenAiCompatibleModels(urlStr: String, apiKey: String, fallback: List<String>): List<String> {
        try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 15_000
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
                if (models.isNotEmpty()) return models.sorted()
            }
        } catch (e: Exception) {
            android.util.Log.e("AuditorAIService", "Error fetching models from $urlStr: ${e.message}", e)
        }
        return fallback
    }
}
