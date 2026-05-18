package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import android.content.Context
import android.graphics.Bitmap
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Food Recognition Service - Multi-Model Support
 * Supports OpenAI, Gemini, DeepSeek, and Claude vision APIs
 * Uses Factory pattern to select provider based on preferences
 */
class FoodRecognitionService(
    private val context: Context,
    private val preferences: Preferences,
    private val persistenceLayer: app.aaps.core.interfaces.db.PersistenceLayer,
    private val oauthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
) {
    
    private fun getProvider(): AIVisionProvider {
        val providerName = preferences.get(StringKey.AimiAdvisorProvider).uppercase(java.util.Locale.US)
        
        return when {
            providerName == "OPENAI" -> OpenAIVisionProvider()
            providerName.startsWith("GEMINI") -> GeminiVisionProvider(context, preferences, oauthManager)
            providerName == "DEEPSEEK" -> DeepSeekVisionProvider()
            providerName == "CLAUDE" -> ClaudeVisionProvider()
            else -> OpenAIVisionProvider()
        }
    }
    
    /**
     * Get API key for current provider
     */
    private fun getApiKey(providerId: String): String {
        val id = providerId.uppercase(java.util.Locale.US)
        return when {
            id == "OPENAI" -> preferences.get(StringKey.AimiAdvisorOpenAIKey)
            id.startsWith("GEMINI") -> preferences.get(StringKey.AimiAdvisorGeminiKey)
            id == "DEEPSEEK" -> preferences.get(StringKey.AimiAdvisorDeepSeekKey)
            id == "CLAUDE" -> preferences.get(StringKey.AimiAdvisorClaudeKey)
            else -> ""
        }
    }
    
    /**
     * Estimate carbs and macros from food image
     * Uses currently selected provider from preferences
     */
    suspend fun estimateCarbsFromImage(bitmap: Bitmap, userDescription: String = ""): EstimationResult = withContext(Dispatchers.IO) {
        val provider = getProvider()
        val apiKey = getApiKey(provider.providerId)
        
        if (apiKey.isBlank()) {
            return@withContext FoodAnalysisPrompt.emptyErrorResult(
                "API Key Missing",
                "Please configure ${provider.displayName} API key in AIMI Preferences → Meal Advisor."
            )
        }

        val glucoseContext = fetchGlucoseContext()
        
        try {
            return@withContext provider.estimateFromImage(bitmap, userDescription, apiKey, glucoseContext)
        } catch (e: Exception) {
            return@withContext FoodAnalysisPrompt.emptyErrorResult(
                "Error",
                "${provider.displayName} Error: ${e.message}"
            )
        }
    }

    private fun fetchGlucoseContext(): String? {
        return try {
            val now = System.currentTimeMillis()
            val fifteenMinsAgo = now - 15 * 60 * 1000L
            val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(fifteenMinsAgo, now, true)
            
            if (bgReadings.isEmpty()) return null

            val readingsStr = bgReadings.sortedBy { it.timestamp }.joinToString(", ") { 
                "${it.value} mg/dL (${(now - it.timestamp) / 60000}m ago)" 
            }
            
            val last = bgReadings.maxByOrNull { it.timestamp }
            val first = bgReadings.minByOrNull { it.timestamp }
            val trend = if (last != null && first != null && last != first) {
                val diff = last.value - first.value
                val mins = (last.timestamp - first.timestamp) / 60000.0
                if (mins > 0) "Trend: ${"%.1f".format(diff / mins)} mg/dL/min" else "Trend: Stable"
            } else "Trend: Stable"

            "User Glucose Context (last 15m): $readingsStr. $trend"
        } catch (e: Exception) {
            null
        }
    }
}
