package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * AI COACHING SERVICE
 * =============================================================================
 * 
 * Interacts with OpenAI API to generate natural language coaching advice.
 * Uses robust HttpURLConnection (zero dependency).
 * =============================================================================
 */
@SingleIn(AppScope::class)
class AiCoachingService @Inject constructor() {

    enum class Provider(val id: String, val displayName: String) {
        OPENAI("openai", "OpenAI"),
        GEMINI("gemini", "Google Gemini"),
        DEEPSEEK("deepseek", "DeepSeek"),
        CLAUDE("claude", "Anthropic Claude"),
        GLM("glm", "Zhipu AI GLM"),
        GROK("grok", "xAI Grok"),
        OPENROUTER("openrouter", "OpenRouter"),
        GROQ("groq", "Groq"),
        CUSTOM("custom", "Custom Endpoint");
    }

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-6.1-sol"
        
        // DeepSeek Chat (OpenAI-compatible)
        private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_MODEL = "deepseek-v4-pro"
        
        // Claude Haiku (Fast & Cheap)
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_MODEL = "claude-haiku-5-5"

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

    /**
     * Fetch advice asynchronously.
     */
    suspend fun fetchAdvice(
        androidContext: Context,
        context: AdvisorContext, 
        report: AdvisorReport, 
        apiKey: String,
        oauthToken: String? = null,
        useOAuth: Boolean = false,
        provider: Provider,
        history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog> = emptyList(),
        customUrl: String? = null,
        customModel: String? = null
    ): String = withContext(Dispatchers.IO) {
        val tokenToUse = if (useOAuth) oauthToken else null
        if (apiKey.isBlank() && tokenToUse == null && provider != Provider.CUSTOM) return@withContext "API Key or OAuth token missing."
        if (provider == Provider.CUSTOM) {
            val urlTrimmed = customUrl?.trim() ?: ""
            if (urlTrimmed.isBlank() || (!urlTrimmed.startsWith("http://") && !urlTrimmed.startsWith("https://"))) {
                return@withContext "Ошибка конфигурации: Кастомный Endpoint URL не задан в настройках AIMI"
            }
        }

        try {
            val prompt = buildPrompt(androidContext, context, report, history)
            
            return@withContext when (provider) {
                Provider.GEMINI -> callGemini(androidContext, apiKey, tokenToUse, prompt, customModel)
                Provider.CLAUDE -> callClaude(apiKey, prompt, customModel)
                Provider.OPENAI -> callOpenAICompatible(OPENAI_URL, apiKey, customModel?.ifBlank { null } ?: OPENAI_MODEL, prompt, "OpenAI")
                Provider.DEEPSEEK -> callOpenAICompatible(DEEPSEEK_URL, apiKey, customModel?.ifBlank { null } ?: DEEPSEEK_MODEL, prompt, "DeepSeek")
                Provider.GLM -> callOpenAICompatible("https://open.bigmodel.cn/api/paas/v4/chat/completions", apiKey, customModel?.ifBlank { null } ?: "glm-5.3-flash", prompt, "GLM")
                Provider.GROK -> callOpenAICompatible("https://api.x.ai/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "grok-4.7", prompt, "Grok")
                Provider.OPENROUTER -> callOpenAICompatible("https://openrouter.ai/api/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "anthropic/claude-sonnet-4-6", prompt, "OpenRouter", mapOf("HTTP-Referer" to "https://androidaps.org", "X-Title" to "AndroidAPS AIMI"))
                Provider.GROQ -> callOpenAICompatible("https://api.groq.com/openai/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "llama-3.3-70b-versatile", prompt, "Groq")
                Provider.CUSTOM -> {
                    val url = normalizeEndpointUrl(customUrl ?: "")
                    val model = customModel?.ifBlank { null } ?: "custom-model"
                    callOpenAICompatible(url, apiKey, model, prompt, "Custom")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Connection Error (${provider.name}) : ${e.localizedMessage}"
        }
    }
    
    /**
     * Simple text generation for Context Module.
     */
    suspend fun fetchText(
        context: Context,
        prompt: String,
        apiKey: String,
        oauthToken: String? = null,
        useOAuth: Boolean = false,
        provider: Provider,
        customUrl: String? = null,
        customModel: String? = null
    ): String = withContext(Dispatchers.IO) {
        val tokenToUse = if (useOAuth) oauthToken else null
        if (apiKey.isBlank() && tokenToUse == null && provider != Provider.CUSTOM) return@withContext "API Key missing."
        if (prompt.isBlank()) return@withContext "Empty prompt."
        if (provider == Provider.CUSTOM) {
            val urlTrimmed = customUrl?.trim() ?: ""
            if (urlTrimmed.isBlank() || (!urlTrimmed.startsWith("http://") && !urlTrimmed.startsWith("https://"))) {
                return@withContext "Ошибка конфигурации: Кастомный Endpoint URL не задан в настройках AIMI"
            }
        }
        
        try {
            return@withContext when (provider) {
                Provider.GEMINI -> callGemini(context, apiKey, tokenToUse, prompt, customModel)
                Provider.CLAUDE -> callClaude(apiKey, prompt, customModel)
                Provider.OPENAI -> callOpenAICompatible(OPENAI_URL, apiKey, customModel?.ifBlank { null } ?: OPENAI_MODEL, prompt, "OpenAI")
                Provider.DEEPSEEK -> callOpenAICompatible(DEEPSEEK_URL, apiKey, customModel?.ifBlank { null } ?: DEEPSEEK_MODEL, prompt, "DeepSeek")
                Provider.GLM -> callOpenAICompatible("https://open.bigmodel.cn/api/paas/v4/chat/completions", apiKey, customModel?.ifBlank { null } ?: "glm-5.3-flash", prompt, "GLM")
                Provider.GROK -> callOpenAICompatible("https://api.x.ai/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "grok-4.7", prompt, "Grok")
                Provider.OPENROUTER -> callOpenAICompatible("https://openrouter.ai/api/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "anthropic/claude-sonnet-4-6", prompt, "OpenRouter", mapOf("HTTP-Referer" to "https://androidaps.org", "X-Title" to "AndroidAPS AIMI"))
                Provider.GROQ -> callOpenAICompatible("https://api.groq.com/openai/v1/chat/completions", apiKey, customModel?.ifBlank { null } ?: "llama-3.3-70b-versatile", prompt, "Groq")
                Provider.CUSTOM -> {
                    val url = normalizeEndpointUrl(customUrl ?: "")
                    val model = customModel?.ifBlank { null } ?: "custom-model"
                    callOpenAICompatible(url, apiKey, model, prompt, "Custom")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("[Provider: ${provider.name} | Error: ${e.message}]", e)
        }
    }

    // ... (keep private methods)


    private fun callOpenAICompatible(
        urlStr: String,
        apiKey: String,
        model: String,
        prompt: String,
        providerName: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val root = JSONObject()
        root.put("model", model)
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        root.put("messages", messages)
        root.put("max_tokens", 4096)

        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
            connectTimeout = 15000
            readTimeout = 45000
        }

        OutputStreamWriter(connection.outputStream, java.nio.charset.StandardCharsets.UTF_8).use {
            it.write(root.toString())
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }
            return parseOpenAiResponse(response)
        } else {
            val err = (connection.errorStream ?: connection.inputStream)?.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)?.use { it.readText() } ?: "Empty error"
            throw Exception("[Provider: $providerName | URL: $urlStr | HTTP $responseCode: $err]")
        }
    }

    private fun callGemini(
        context: Context, 
        apiKey: String?, 
        oauthToken: String?, 
        prompt: String,
        customModel: String? = null
    ): String {
        val resolver = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver(context)
        val defaultModel = customModel?.ifBlank { null } ?: "gemini-3.8-flash"
        val primaryModel = resolver.resolveGenerateContentModel(apiKey, oauthToken, defaultModel)
        
        try {
            return executeGeminiRequest(resolver, apiKey, oauthToken, prompt, primaryModel)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota")) {
                val fallbackModel = "gemini-3.5-flash-lite"
                return executeGeminiRequest(resolver, apiKey, oauthToken, prompt, fallbackModel)
            }
            throw e
        }
    }

    private fun executeGeminiRequest(
        resolver: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver,
        apiKey: String?, 
        oauthToken: String?,
        prompt: String, 
        modelId: String
    ): String {
        val urlStr = if (oauthToken != null) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        } else {
            resolver.getGenerateContentUrl(modelId, apiKey)
        }
        
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        if (oauthToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $oauthToken")
        }
        
        val jsonBody = JSONObject()
        val parts = JSONArray()
        val part = JSONObject()
        part.put("text", prompt)
        parts.put(part)
        
        val content = JSONObject()
        content.put("parts", parts)
        content.put("role", "user")
        
        val contents = JSONArray()
        contents.put(content)
        
        val root = JSONObject()
        root.put("contents", contents)
        
        val config = JSONObject()
        config.put("temperature", 0.7)
        config.put("maxOutputTokens", 4096)
        root.put("generationConfig", config)

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
        }

        OutputStreamWriter(connection.outputStream).use { it.write(root.toString()) }

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val response = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream)).use {
                var line: String? = ""
                while (it.readLine().also { line = it } != null) {
                    response.append(line)
                }
            }
            return parseGeminiResponse(response.toString())
        } else {
            val errorResponse = StringBuilder()
            BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                var line: String? = ""
                while (it.readLine().also { line = it } != null) {
                    errorResponse.append(line)
                }
            }
            val sanitized = if (urlStr.contains("?key=")) urlStr.substringBefore("?key=") + "?key=REDACTED" else urlStr
            throw Exception("[Provider: Gemini | URL: $sanitized | HTTP $responseCode: $errorResponse]")
        }
    }

    private fun buildPrompt(
        androidContext: Context, 
        ctx: AdvisorContext, 
        report: AdvisorReport,
        history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog>
    ): String {
        val sb = StringBuilder()
        val deviceLang = java.util.Locale.getDefault().displayLanguage
        
        // Persona
        sb.append("You are AIMI, an expert 'Certified Diabetes Educator' specializing in Automated Insulin Delivery (AID).\n")
        sb.append("Your Goal: Analyze the patient's 7-day glucose & insulin data to identify patterns and suggest specific algorithm tuning.\n")
        sb.append("Tone: Professional, encouraging, precise, and safety-first.\n\n")

        // 0.5 STABILITY CONTEXT (History)
        sb.append("--- HISTORY & STABILITY CONTEXT ---\n")
        if (history.isNotEmpty()) {
            sb.append("Recent changes made by the user:\n")
            history.take(5).forEach { 
                val date = java.text.SimpleDateFormat("dd/MM", java.util.Locale.US).format(java.util.Date(it.timestamp))
                sb.append("- [$date] ${it.description} (${it.oldValue} -> ${it.newValue})\n")
            }
            sb.append("CRITICAL: If a structured parameter was recently changed (last 3-5 days), AVOID suggesting further contradictory changes to it unless safety is at risk. Allow time for the change to work.\n\n")
        } else {
            sb.append("No recent changes recorded. You may suggest bold adjustments if necessary.\n\n")
        }

        // 1. Context: Metrics
        sb.append("--- PATIENT METRICS (7 Days) ---\n")
        sb.append("Score: ${report.overallScore}/10 | GMI: ${ctx.metrics.gmi}%\n")
        sb.append("TIR (70-180): ${(ctx.metrics.tir70_180 * 100).roundToInt()}%\n")
        sb.append("Hypo (<70): ${(ctx.metrics.timeBelow70 * 100).roundToInt()}% | Severe (<54): ${(ctx.metrics.timeBelow54 * 100).roundToInt()}%\n")
        sb.append("Hyper (>180): ${(ctx.metrics.timeAbove180 * 100).roundToInt()}%\n")
        sb.append("Mean Glucose: ${ctx.metrics.meanBg.roundToInt()} mg/dL\n")
        sb.append("Total Daily Dose (TDD): ${ctx.metrics.tdd.roundToInt()} U\n")
        sb.append("Basal/Bolus Split: ${(ctx.metrics.basalPercent * 100).roundToInt()}% Basal | ${(100 - (ctx.metrics.basalPercent * 100).roundToInt())}% Bolus\n\n")

        // 1.5 Context: Active Profile & Preferences
        sb.append("--- ACTIVE PROFILE & SETTINGS ---\n")
        sb.append("Max SMB: ${ctx.prefs.maxSmb} U\n")
        sb.append("ISF: ${ctx.profile.isf} mg/dL/U\n")
        sb.append("IC Ratio: ${ctx.profile.icRatio} g/U\n")
        sb.append("Basal (Night): ${ctx.profile.nightBasal} U/h\n")
        sb.append("Total Basal (Profile): ${ctx.profile.totalBasal} U/day\n")
        sb.append("DIA (Profile): ${ctx.profile.dia} h\n")
        sb.append("Target BG: ${ctx.profile.targetBg} mg/dL\n\n")

        // 2. PKPD Context
        if (ctx.pkpdPrefs.pkpdEnabled) {
             sb.append("--- PARAMETRES PKPD (Adaptatif) ---\n")
             sb.append("DIA: ${ctx.pkpdPrefs.initialDiaH}h\n")
             sb.append("Peak Time: ${ctx.pkpdPrefs.initialPeakMin}min\n")
             sb.append("IsfFusionMax: x${ctx.pkpdPrefs.isfFusionMaxFactor}\n\n")
        }

        // 3. System Observations (Recommendations + PKPD)
        sb.append("--- SYSTEM OBSERVATIONS ---\n")
        if (report.recommendations.isNotEmpty()) {
            report.recommendations.forEach { 
                val title = try { androidContext.getString(it.titleResId) } catch(e:Exception) { "Issue" }
                val desc = try { 
                    if (it.descriptionArgs.isNotEmpty()) {
                        androidContext.getString(it.descriptionResId, *it.descriptionArgs.toTypedArray())
                    } else {
                        androidContext.getString(it.descriptionResId)
                    }
                } catch (e: Exception) { "" }
                sb.append("- [Priority ${it.priority}] $title: $desc\n") 
            }
        } else {
            sb.append("- No specific algorithmic issues detected.\n")
        }
        sb.append("\n")

        // 4. Instructions
        sb.append("--- COACHING TASK ---\n")
        sb.append("Respond in '$deviceLang'. Structure your answer exactly as follows:\n")
        sb.append("1. 🔍 **Diagnostics**: Summarize the main glycemic patterns (e.g., 'Post-prandial spikes', 'Nighttime hypos', 'Basal heavy').\n")
        sb.append("2. 📉 **Root Cause**: Hypothesize the 'Why' (e.g., 'DIA too short', 'ISF too aggressive', 'Carb ratio needs checking'). Consider the History context.\n")
        sb.append("3. 🛠️ **Action Plan**: Propose 2-3 concrete, actionable steps. If Hypo > 4%, prioritize safety (reduce aggressiveness). If suggestions above exist, evaluate them.\n")
        sb.append("\nConstraint: Keep it under 150 words. Use emojis.")

        return sb.toString()
    }

    private fun buildOpenAiJson(prompt: String): JSONObject {
        val root = JSONObject()
        root.put("model", OPENAI_MODEL)
        val messages = JSONArray()
        // Unified: Prompt contains the full persona and instructions.
        val usr = JSONObject().put("role", "user").put("content", prompt)
        messages.put(usr)
        root.put("messages", messages)
        root.put("messages", messages)
        root.put("max_tokens", 4096)
        
        return root
    }

    private fun parseOpenAiResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            "Erreur lecture OpenAI."
        }
    }

    private fun parseGeminiResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val candidate = root.getJSONArray("candidates").getJSONObject(0)
            val parts = candidate.getJSONObject("content").getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
             // Fallback for safety blocked
             if (jsonStr.contains("finishReason")) "Contenu bloqué par sécurité Gemini." else "Erreur lecture Gemini."
        }
    }
    
    private fun callClaude(apiKey: String, prompt: String, customModel: String? = null): String {
        val url = URL(CLAUDE_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        
        val jsonBody = JSONObject()
        jsonBody.put("model", customModel?.ifBlank { null } ?: CLAUDE_MODEL)
        jsonBody.put("max_tokens", 4096)
        jsonBody.put("temperature", 0.7)
        
        // Claude expects messages array with role/content
        val messages = JSONArray()
        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", prompt)
        messages.put(userMessage)
        jsonBody.put("messages", messages)
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
        }

        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream, java.nio.charset.StandardCharsets.UTF_8))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            return parseClaudeResponse(response.toString())
        } else {
            val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream, java.nio.charset.StandardCharsets.UTF_8))
            val err = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) err.append(line)
            throw Exception("[Provider: Claude | URL: $CLAUDE_URL | HTTP $responseCode: $err]")
        }
    }
    
    private fun parseClaudeResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val content = root.getJSONArray("content")
            content.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            "Erreur lecture Claude: ${e.message}"
        }
    }
}
