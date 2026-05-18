package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAIMI.keys.AimiStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FatSecretService @Inject constructor(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    // Use Global US-based API endpoint as fallback for geographical IP restrictions
    private companion object {
        const val API_URL_PLATFORM = "https://platform.fatsecret.com/rest/food/barcode/find-by-id/v2"
    }

    private suspend fun getAccessToken(forceNew: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceNew && accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return@withContext accessToken
        }

        val clientId = preferences.get(AimiStringKey.FatSecretClientId)
        val clientSecret = preferences.get(AimiStringKey.FatSecretClientSecret)

        if (clientId.isBlank() || clientSecret.isBlank()) {
            aapsLogger.error(LTag.APS, "FatSecret credentials missing")
            return@withContext null
        }

        // Try multiple scope combinations from most powerful to most basic
        val scopes = listOf(
            "premier barcode localization",
            "premier barcode",
            "premier",
            "basic barcode localization",
            "barcode localization",
            "barcode",
            "basic",
            null
        )
        
        var token: String? = null
        for (scope in scopes) {
            aapsLogger.info(LTag.APS, "Attempting FatSecret OAuth with scope: '$scope'")
            token = tryFetchToken(clientId, clientSecret, scope)
            if (token != null) break
        }

        accessToken = token
        return@withContext token
    }

    private fun tryFetchToken(clientId: String, clientSecret: String, scope: String?): String? {
        try {
            val url = URL("https://oauth.fatsecret.com/connect/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            // Use Basic Auth
            val auth = "$clientId:$clientSecret"
            val encodedAuth = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $encodedAuth")
            
            connection.doOutput = true

            var body = "grant_type=client_credentials"
            if (scope != null) {
                body += "&scope=${URLEncoder.encode(scope, "UTF-8")}"
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            val code = connection.responseCode
            if (code == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val token = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600)
                tokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000
                aapsLogger.info(LTag.APS, "FatSecret Token Success (scope=$scope)")
                return token
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                aapsLogger.warn(LTag.APS, "FatSecret OAuth attempt failed ($code) for scope '$scope': $error")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "FatSecret OAuth exception for scope '$scope'", e)
        }
        return null
    }

    suspend fun findProductByBarcode(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        var token = getAccessToken() ?: return@withContext null
        val primaryRegion = preferences.get(AimiStringKey.FatSecretRegion).ifBlank { "RU" }
        
        // Regions to try in order
        val regions = linkedSetOf(primaryRegion, "RU", "US", "GB", "DE", "FR", "FI").toList()
        val paddedBarcode = barcode.trim().padStart(13, '0')

        for (region in regions) {
            val lang = if (region.uppercase() == "RU") "ru" else "en"
            try {
                aapsLogger.debug(LTag.APS, "Attempting barcode lookup in region: $region")
                val urlStr = "$API_URL_PLATFORM?barcode=$paddedBarcode&region=$region&language=$lang&format=json&flag_default_serving=true"
                var response = callApi(urlStr, token)

                if (response != null) {
                    if (response.contains("\"code\": 21,") || response.contains("\"code\": 21}")) {
                         aapsLogger.error(LTag.APS, "FatSecret REST API IP restriction detected (Code 21).")
                         return@withContext ProductInfo(name = "IP_BLOCKED", carbs = 0.0, protein = 0.0, fat = 0.0, calories = 0.0, servingDescription = "")
                    }
                    
                    if (response.contains("\"code\": 14,") || response.contains("\"code\": 14}")) {
                        aapsLogger.warn(LTag.APS, "FatSecret scope error detected (Code 14). Retrying with new token...")
                        token = getAccessToken(forceNew = true) ?: return@withContext null
                        response = callApi(urlStr, token)
                    }

                    if (response != null && !response.contains("\"code\": 211")) {
                        val json = JSONObject(response)
                        if (json.has("food")) {
                            aapsLogger.info(LTag.APS, "FatSecret: Product found in region $region via barcode.")
                            return@withContext parseFoodJson(json.getJSONObject("food"))
                        }
                    }
                }
            } catch (e: Exception) {
                aapsLogger.warn(LTag.APS, "FatSecret: Barcode lookup failed in region $region", e)
            }
        }

        // Final Fallback: Keyword Search by barcode number
        aapsLogger.info(LTag.APS, "FatSecret: Barcode not found in any region. Trying keyword search...")
        try {
            val searchUrl = "https://platform.fatsecret.com/rest/foods/search/v2?search_expression=$paddedBarcode&format=json&max_results=1"
            var searchResponse = callApi(searchUrl, token)
            
            if (searchResponse != null && searchResponse.contains("\"code\": 14")) {
                aapsLogger.warn(LTag.APS, "FatSecret keyword search failed due to scope. Retrying with premier token...")
                token = getAccessToken(forceNew = true) ?: return@withContext null
                searchResponse = callApi(searchUrl, token)
            }

            if (searchResponse != null) {
                val searchJson = JSONObject(searchResponse)
                val foodsObj = searchJson.optJSONObject("foods")
                val foodArray = foodsObj?.optJSONArray("food")
                if (foodArray != null && foodArray.length() > 0) {
                    val firstMatch = foodArray.getJSONObject(0)
                    aapsLogger.info(LTag.APS, "FatSecret: Product found via keyword search: ${firstMatch.optString("food_name")}")
                    return@withContext parseFoodJson(firstMatch)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "FatSecret: Keyword search fallback failed", e)
        }

        null
    }

    private fun parseFoodJson(food: JSONObject): ProductInfo {
        val name = food.getString("food_name")
        val servingsObj = food.optJSONObject("servings")
        
        val serving = when {
            servingsObj?.has("serving") == true -> {
                val servingItem = servingsObj.get("serving")
                if (servingItem is JSONArray) {
                    var bestMatch: JSONObject? = null
                    for (i in 0 until servingItem.length()) {
                        val s = servingItem.getJSONObject(i)
                        val desc = s.optString("serving_description", "").lowercase()
                        if (desc.contains("100") && (desc.contains("g") || desc.contains("г"))) {
                            bestMatch = s
                            break
                        }
                    }
                    bestMatch ?: servingItem.getJSONObject(0)
                } else {
                    servingItem as JSONObject
                }
            }
            else -> null
        }

        // If serving is null, check if macros are available at the top level (sometimes happens in search results)
        val carbs = serving?.optDouble("carbohydrate", 0.0) ?: food.optDouble("carbohydrate", 0.0)
        val protein = serving?.optDouble("protein", 0.0) ?: food.optDouble("protein", 0.0)
        val fat = serving?.optDouble("fat", 0.0) ?: food.optDouble("fat", 0.0)
        val calories = serving?.optDouble("calories", 0.0) ?: food.optDouble("calories", 0.0)
        val servingDesc = serving?.optString("serving_description", "100g") ?: "100g"

        return ProductInfo(
            name = name,
            carbs = carbs,
            protein = protein,
            fat = fat,
            calories = calories,
            servingDescription = servingDesc
        )
    }

    private fun callApi(urlStr: String, token: String): String? {
        try {
            aapsLogger.debug(LTag.APS, "FatSecret Request: $urlStr")
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                aapsLogger.debug(LTag.APS, "FatSecret Response: $response")
                return response
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                aapsLogger.error(LTag.APS, "FatSecret API error: ${connection.responseCode} $error")
                return error // Return error body to identify Code 21
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "FatSecret API exception", e)
        }
        return null
    }

    data class ProductInfo(
        val name: String,
        val carbs: Double,
        val protein: Double,
        val fat: Double,
        val calories: Double,
        val servingDescription: String
    )
}
