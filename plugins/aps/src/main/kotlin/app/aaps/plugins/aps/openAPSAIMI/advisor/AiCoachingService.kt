package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
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
@Singleton
class AiCoachingService @Inject constructor() {

    enum class Provider { OPENAI, GEMINI, DEEPSEEK, CLAUDE }

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

        private const val OPENAI_MODEL = "gpt-4o-mini" // Efficient model for coaching logic
        

        
        // DeepSeek Chat (OpenAI-compatible)
        private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_MODEL = "deepseek-chat"
        
        // Claude Haiku (Fast & Cheap)
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_MODEL = "claude-3-haiku-20240307"
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
        history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val tokenToUse = if (useOAuth) oauthToken else null
        if (apiKey.isBlank() && tokenToUse == null) return@withContext "API Key or OAuth token missing."

        try {
            val prompt = buildPrompt(androidContext, context, report, history)
            
            return@withContext when (provider) {
                Provider.GEMINI -> callGemini(androidContext, apiKey, tokenToUse, prompt)
                Provider.DEEPSEEK -> callDeepSeek(apiKey, prompt)
                Provider.CLAUDE -> callClaude(apiKey, prompt)
                else -> callOpenAI(apiKey, prompt)
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
        provider: Provider
    ): String = withContext(Dispatchers.IO) {
        val tokenToUse = if (useOAuth) oauthToken else null
        if (apiKey.isBlank() && tokenToUse == null) return@withContext "API Key missing."
        if (prompt.isBlank()) return@withContext "Empty prompt."
        
        try {
            return@withContext when (provider) {
                Provider.GEMINI -> callGemini(context, apiKey, tokenToUse, prompt)
                Provider.DEEPSEEK -> callDeepSeek(apiKey, prompt)
                Provider.CLAUDE -> callClaude(apiKey, prompt)
                else -> callOpenAI(apiKey, prompt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Error: ${e.localizedMessage}"
        }
    }

    // ... (keep private methods)


    private fun callOpenAI(apiKey: String, prompt: String): String {
        val jsonBody = buildOpenAiJson(prompt)
        val url = URL(OPENAI_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }

        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            return parseOpenAiResponse(response.toString())
        } else {
             // Try read error stream
            val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
            val err = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) err.append(line)
            return "Erreur OpenAI ($responseCode): $err"
        }
    }

    private fun callGemini(context: Context, apiKey: String?, oauthToken: String?, prompt: String): String {
        val resolver = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver(context)
        
        // Use manual dependency resolution with interfaces
        val aapsLogger = object : app.aaps.core.interfaces.logging.AAPSLogger {
            override fun debug(message: String) {}
            override fun debug(enable: Boolean, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, accessor: () -> String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun warn(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun warn(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun info(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun info(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, message: String, throwable: Throwable) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun error(message: String) {}
            override fun error(message: String, throwable: Throwable) {}
            override fun error(format: String, vararg arguments: Any?) {}
            override fun debug(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun info(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun warn(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun error(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
        }
        
        val sp = object : app.aaps.core.interfaces.sharedPreferences.SP {
            override fun getAll(): Map<String, *> = emptyMap<String, Any>()
            override fun clear() {}
            override fun contains(key: String): Boolean = false
            override fun contains(resourceId: Int): Boolean = false
            override fun remove(resourceID: Int) {}
            override fun remove(key: String) {}
            override fun getString(resourceID: Int, defaultValue: String): String = defaultValue
            override fun getString(key: String, defaultValue: String): String = context.getSharedPreferences("AAPS", Context.MODE_PRIVATE).getString(key, defaultValue) ?: defaultValue
            override fun getStringOrNull(resourceID: Int, defaultValue: String?): String? = defaultValue
            override fun getStringOrNull(key: String, defaultValue: String?): String? = context.getSharedPreferences("AAPS", Context.MODE_PRIVATE).getString(key, defaultValue)
            override fun getBoolean(resourceID: Int, defaultValue: Boolean): Boolean = defaultValue
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean = context.getSharedPreferences("AAPS", Context.MODE_PRIVATE).getBoolean(key, defaultValue)
            override fun getDouble(resourceID: Int, defaultValue: Double): Double = defaultValue
            override fun getDouble(key: String, defaultValue: Double): Double = defaultValue
            override fun getInt(resourceID: Int, defaultValue: Int): Int = defaultValue
            override fun getInt(key: String, defaultValue: Int): Int = defaultValue
            override fun getLong(resourceID: Int, defaultValue: Long): Long = defaultValue
            override fun getLong(key: String, defaultValue: Long): Long = context.getSharedPreferences("AAPS", Context.MODE_PRIVATE).getLong(key, defaultValue)
            override fun incLong(key: String) {}
            override fun putBoolean(key: String, value: Boolean) {}
            override fun putBoolean(resourceID: Int, value: Boolean) {}
            override fun putDouble(key: String, value: Double) {}
            override fun putDouble(resourceID: Int, value: Double) {}
            override fun putLong(key: String, value: Long) {}
            override fun putLong(resourceID: Int, value: Long) {}
            override fun putInt(key: String, value: Int) {}
            override fun putInt(resourceID: Int, value: Int) {}
            override fun incInt(key: String) {}
            override fun putString(resourceID: Int, value: String) {}
            override fun putString(key: String, value: String) {}
            override fun edit(commit: Boolean, block: app.aaps.core.interfaces.sharedPreferences.SP.Editor.() -> Unit) {}
        }

        val oauthManager = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager(aapsLogger, sp, context)
        
        val primaryModel = resolver.resolveGenerateContentModel(apiKey, oauthToken, "gemini-3.1-flash")
        
        try {
            return executeGeminiRequest(resolver, oauthManager, apiKey, oauthToken, prompt, primaryModel)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota")) {
                val fallbackModel = "gemini-3.1-flash"
                return executeGeminiRequest(resolver, oauthManager, apiKey, oauthToken, prompt, fallbackModel)
            }
            throw e
        }
    }

    private fun executeGeminiRequest(
        resolver: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver,
        oauthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager,
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
        if (oauthToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $oauthToken")
            oauthManager.getProjectId()?.let { 
                if (it.isNotEmpty()) connection.setRequestProperty("x-goog-user-project", it)
            }
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
            throw Exception("HTTP $responseCode: $errorResponse")
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
    
    private fun callDeepSeek(apiKey: String, prompt: String): String {
        val jsonBody = buildOpenAiJson(prompt) // DeepSeek uses OpenAI-compatible format
        jsonBody.put("model", DEEPSEEK_MODEL) // Override model
        
        val url = URL(DEEPSEEK_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }

        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()
        writer.close()

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            return parseOpenAiResponse(response.toString()) // Same format
        } else {
            val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
            val err = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) err.append(line)
            return "Erreur DeepSeek ($responseCode): $err"
        }
    }
    
    private fun callClaude(apiKey: String, prompt: String): String {
        val url = URL(CLAUDE_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        val jsonBody = JSONObject()
        jsonBody.put("model", CLAUDE_MODEL)
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
            return "Erreur Claude ($responseCode): $err"
        }
    }
    
    private fun parseClaudeResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val content = root.getJSONArray("content")
            content.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            "Erreur lecture Claude."
        }
    }
}
