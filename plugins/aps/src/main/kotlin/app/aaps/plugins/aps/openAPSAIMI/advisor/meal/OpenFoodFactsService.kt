package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsService @Inject constructor(
    private val aapsLogger: AAPSLogger
) {
    private companion object {
        // Используем префикс "world", чтобы искать по всем странам одновременно
        const val API_URL = "https://world.openfoodfacts.org/api/v2/product/"
        
        // ВАЖНО: OFF требует уникальный User-Agent. Без него могут заблокировать запросы.
        const val USER_AGENT = "AndroidAPS_AIMI_Fork - Android - Version 1.0 - https://github.com/nightscout/AndroidAPS"
    }

    suspend fun findProductByBarcode(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val cleanBarcode = barcode.trim()
            val urlStr = "$API_URL$cleanBarcode.json"

            aapsLogger.debug(LTag.APS, "OpenFoodFacts Request: $urlStr")
            
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // Обязательный заголовок для Open Food Facts
            connection.setRequestProperty("User-Agent", USER_AGENT) 
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                // OFF возвращает status = 1, если продукт найден, и 0, если нет
                if (json.optInt("status", 0) == 1 && json.has("product")) {
                    aapsLogger.info(LTag.APS, "OpenFoodFacts: Product found")
                    return@withContext parseProductJson(json.getJSONObject("product"))
                } else {
                    aapsLogger.debug(LTag.APS, "OpenFoodFacts: Product not found (status 0)")
                    return@withContext null
                }
            } else {
                aapsLogger.error(LTag.APS, "OpenFoodFacts API error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "OpenFoodFacts Barcode exception", e)
        }
        null
    }

    private fun parseProductJson(product: JSONObject): ProductInfo {
        // Пытаемся получить русское название, если нет - берем дефолтное
        val nameRu = product.optString("product_name_ru", "")
        val name = if (nameRu.isNotBlank()) nameRu else product.optString("product_name", "Unknown Product")

        // OFF отдает БЖУ в отдельном объекте "nutriments"
        val nutriments = product.optJSONObject("nutriments")

        // В отличие от FatSecret, OFF ВСЕГДА приводит данные к 100г/100мл.
        // Это идеально для AAPS, не нужно искать правильную порцию.
        val carbs = nutriments?.optDouble("carbohydrates_100g", 0.0) ?: 0.0
        val protein = nutriments?.optDouble("proteins_100g", 0.0) ?: 0.0
        val fat = nutriments?.optDouble("fat_100g", 0.0) ?: 0.0
        val calories = nutriments?.optDouble("energy-kcal_100g", 0.0) ?: 0.0

        // Достаем описание стандартной порции, если оно есть (например "500 ml")
        var servingDesc = product.optString("serving_size", "")
        if (servingDesc.isBlank()) {
            servingDesc = "100 g/ml"
        } else {
            servingDesc = "100 g/ml (Package size: $servingDesc)"
        }

        return ProductInfo(
            name = name,
            // optDouble может вернуть NaN, если в JSON была кривая строка. Проверяем для безопасности AAPS.
            carbs = if (carbs.isNaN()) 0.0 else carbs,
            protein = if (protein.isNaN()) 0.0 else protein,
            fat = if (fat.isNaN()) 0.0 else fat,
            calories = if (calories.isNaN()) 0.0 else calories,
            servingDescription = servingDesc
        )
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
