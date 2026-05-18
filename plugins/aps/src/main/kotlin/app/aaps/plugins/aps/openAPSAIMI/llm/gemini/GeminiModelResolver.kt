package app.aaps.plugins.aps.openAPSAIMI.llm.gemini

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * reliable resolver for Gemini Model IDs.
 * Handles dynamic listing, fallback logic, and caching.
 */
@Singleton
class GeminiModelResolver @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "AIMI_GEMINI"
        // Switched back to v1beta for AI Studio per-user quota support
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        
        // PREFS
        private const val PREFS_NAME = "aimi_gemini_cache"
        private const val KEY_CACHE_TIMESTAMP = "cache_ts"
        private const val KEY_AVAILABLE_MODELS = "available_models_json"
        
        // TTL: 24h
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)

        // Fallback fallback priority list (Realistic Model IDs as of April 2026)
        private val FALLBACK_PRIORITY = listOf(
            "gemini-3.1-pro",
            "gemini-3.1-flash",
            "gemini-3.1-flash-lite",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro-latest",
            "gemini-2.0-flash-exp"
        )
    }

    private val memoryCache = ConcurrentHashMap<String, Boolean>() // ModelID -> True if exists
    private var lastCacheUpdate: Long = 0

    /**
     * Resolves a valid model ID for generateContent calls.
     * 
     * @param apiKey The API Key (optional if using OAuth)
     * @param oauthToken The OAuth Access Token (optional if using API Key)
     * @param preferredModel The user's preferred model
     * @return A valid model ID ready for use in URL
     */
    fun resolveGenerateContentModel(apiKey: String?, oauthToken: String?, preferredModel: String?): String {
        val availableModels = getOrFetchModels(apiKey, oauthToken)
        
        // 1. Explicit Mapping for the new UI internal IDs
        if (!preferredModel.isNullOrBlank()) {
            val mappedModel = when (preferredModel.uppercase(Locale.US)) {
                "GEMINI-3.1-PRO" -> "gemini-3.1-pro"
                "GEMINI-3.1-FLASH" -> "gemini-3.1-flash"
                "GEMINI-3.1-FLASH-LITE" -> "gemini-3.1-flash-lite"
                "GEMINI-1.5-PRO" -> "gemini-1.5-pro-latest"
                "GEMINI-1.5-FLASH" -> "gemini-1.5-flash-latest"
                "GEMINI-2.0-FLASH" -> "gemini-2.0-flash-exp"
                "GEMINI" -> "gemini-3.1-flash"
                else -> preferredModel.trim().removePrefix("models/")
            }

            if (availableModels.contains(mappedModel)) {
                Log.d(TAG, "Using mapped/preferred model: $mappedModel")
                return mappedModel
            }
        }

        // 2. Iterate Priority List
        for (candidate in FALLBACK_PRIORITY) {
            if (availableModels.contains(candidate)) {
                return candidate
            }
        }

        // 3. Last resort
        return availableModels.firstOrNull { it.contains("gemini") } ?: "gemini-2.5-flash"
    }
    
    /**
     * Helper to construct the full URL for a resolved model
     */
    fun getGenerateContentUrl(modelId: String, apiKey: String?): String {
        return if (apiKey != null) {
            "$BASE_URL/$modelId:generateContent?key=$apiKey"
        } else {
            "$BASE_URL/$modelId:generateContent"
        }
    }

    private fun getOrFetchModels(apiKey: String?, oauthToken: String?): Set<String> {
        // 1. Check Memory Cache Validity
        if (memoryCache.isNotEmpty() && !isCacheExpired()) {
            return memoryCache.keys
        }

        // 2. Check Disk Cache Validity
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diskTs = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
        
        if (System.currentTimeMillis() - diskTs < CACHE_TTL_MS) {
            val jsonStr = prefs.getString(KEY_AVAILABLE_MODELS, null)
            if (jsonStr != null) {
                val set = parseModelsSet(jsonStr)
                if (set.isNotEmpty()) {
                    updateMemoryCache(set)
                    return set
                }
            }
        }

        // 3. Fetch Network
        return try {
            val freshModels = fetchModelsFromApi(apiKey, oauthToken)
            if (freshModels.isEmpty()) throw Exception("Empty model list returned")
            
            // Save to Disk
            prefs.edit()
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_AVAILABLE_MODELS, freshModels.joinToString(","))
                .apply()
            
            updateMemoryCache(freshModels)
            freshModels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models: ${e.message}")
            if (memoryCache.isNotEmpty()) return memoryCache.keys
            FALLBACK_PRIORITY.toSet()
        }
    }

    private fun updateMemoryCache(models: Set<String>) {
        memoryCache.clear()
        models.forEach { memoryCache[it] = true }
        lastCacheUpdate = System.currentTimeMillis()
    }

    private fun isCacheExpired(): Boolean {
        return (System.currentTimeMillis() - lastCacheUpdate) > CACHE_TTL_MS
    }

    private fun parseModelsSet(csv: String): Set<String> {
        return csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun fetchModelsFromApi(apiKey: String?, oauthToken: String?): Set<String> {
        var connection: HttpURLConnection? = null
        try {
            val urlStr = if (apiKey != null) "$BASE_URL?key=$apiKey" else BASE_URL
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            if (oauthToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $oauthToken")
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val status = connection.responseCode
            if (status != 200) return emptySet()

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (!json.has("models")) return emptySet()
            
            val modelsArray = json.getJSONArray("models")
            val resultSet = mutableSetOf<String>()
            
            for (i in 0 until modelsArray.length()) {
                val m = modelsArray.getJSONObject(i)
                val name = m.getString("name")
                val supportedMethods = m.optJSONArray("supportedGenerationMethods")
                
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        if (supportedMethods.getString(j) == "generateContent") {
                            resultSet.add(name.removePrefix("models/"))
                            break
                        }
                    }
                }
            }
            return resultSet
        } catch (e: Exception) {
            return emptySet()
        } finally {
            connection?.disconnect()
        }
    }
}
