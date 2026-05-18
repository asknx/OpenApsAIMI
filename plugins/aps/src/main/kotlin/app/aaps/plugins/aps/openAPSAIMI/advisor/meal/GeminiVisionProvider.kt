package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import android.graphics.Bitmap
import android.util.Base64
import app.aaps.core.keys.BooleanKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiVisionProvider(
    private val context: android.content.Context,
    private val preferences: app.aaps.core.keys.interfaces.Preferences,
    private val oauthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
) : AIVisionProvider {
    override val displayName = "Gemini 1.5 Flash (Latest)"
    override val providerId = "GEMINI"
    
    private val geminiResolver = app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiModelResolver(context)

    override suspend fun estimateFromImage(bitmap: Bitmap, userDescription: String, apiKey: String, glucoseContext: String?): EstimationResult = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val useOAuth = preferences.get(app.aaps.core.keys.BooleanKey.OApsAIMIGeminiUseOAuth) && oauthManager.isOAuthEnabled()
            val token = if (useOAuth) oauthManager.getValidAccessToken() else null
            
            val responseJson = callGeminiAPI(apiKey, token, base64Image, userDescription, glucoseContext)
            parseResponse(responseJson)
        } catch (e: Exception) {
            FoodAnalysisPrompt.emptyErrorResult("Gemini Error", e.message ?: "Unknown error")
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
    
    private fun callGeminiAPI(apiKey: String, oauthToken: String?, base64Image: String, userDescription: String, glucoseContext: String?): String {
        val preferredModel = preferences.get(app.aaps.core.keys.StringKey.AimiAdvisorProvider)
        val primaryModel = geminiResolver.resolveGenerateContentModel(
            if (oauthToken == null) apiKey else null, 
            oauthToken, 
            preferredModel
        )
        
        try {
            return executeRequest(apiKey, oauthToken, base64Image, primaryModel, userDescription, glucoseContext)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted")) {
                val fallbackModel = "gemini-3.1-flash"
                return executeRequest(apiKey, oauthToken, base64Image, fallbackModel, userDescription, glucoseContext)
            }
            throw e
        }
    }

    private fun executeRequest(apiKey: String, oauthToken: String?, base64Image: String, modelId: String, userDescription: String, glucoseContext: String?): String {
        val urlStr = if (oauthToken != null) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        } else {
            geminiResolver.getGenerateContentUrl(modelId, apiKey)
        }
        
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        if (oauthToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $oauthToken")
            oauthManager.getProjectId()?.let { 
                if (it.isNotEmpty()) connection.setRequestProperty("x-goog-user-project", it)
            }
        }
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        
        val userPrompt = StringBuilder().apply {
            if (userDescription.isNotBlank()) {
                append("IMPORTANT - User clarification: \"$userDescription\". Use this as the highest priority source for portion sizing and calculation adjustments. ")
            }
            if (glucoseContext != null) append("$glucoseContext. ")
            append("Analyze this meal image and return JSON only according to the required schema.")
        }.toString()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "${FoodAnalysisPrompt.SYSTEM_PROMPT}\n\n$userPrompt")
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 4096)
                put("temperature", 0.0)
                put("responseMimeType", "application/json")
            })
        }
        
        connection.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
        
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Empty error"
            // Log full error to logcat for developer analysis
            android.util.Log.e("GeminiVision", "403 Full Body: $err")
            throw Exception("HTTP $code: $err")
        }
    }
    
    private fun parseResponse(jsonStr: String): EstimationResult {
        val root = JSONObject(jsonStr)
        val content = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        
        val cleaned = FoodAnalysisPrompt.cleanJsonResponse(content)
        return FoodAnalysisPrompt.parseJsonToResult(cleaned)
    }
}
