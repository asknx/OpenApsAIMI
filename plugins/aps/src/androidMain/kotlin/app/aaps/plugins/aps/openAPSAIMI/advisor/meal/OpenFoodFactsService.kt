package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope

@SingleIn(AppScope::class)
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

    internal fun parseProductJson(product: JSONObject): ProductInfo {
        // Пытаемся получить русское название, если нет - берем дефолтное
        val nameRu = product.optString("product_name_ru", "")
        val name = if (nameRu.isNotBlank()) nameRu else product.optString("product_name", "Unknown Product")

        // OFF отдает БЖУ в отдельном объекте "nutriments"
        val nutriments = product.optJSONObject("nutriments")

        // Пытаемся найти размер порции/упаковки для пересчета
        var servingSizeStr = product.optString("serving_size", "")
        if (servingSizeStr.isBlank()) {
            servingSizeStr = product.optString("product_quantity", "")
        }
        if (servingSizeStr.isBlank()) {
            servingSizeStr = product.optString("quantity", "")
        }

        val packageSize = extractNumericValue(servingSizeStr)
        val ratio = if (packageSize > 0) packageSize / 100.0 else 1.0

        // Приоритет: 1. Значение на порцию (если есть), 2. Пересчитанное из 100г, 3. Просто 100г
        val carbs = nutriments?.optDouble("carbohydrates_serving")?.takeIf { !it.isNaN() }
            ?: (nutriments?.optDouble("carbohydrates_100g", 0.0)?.times(ratio) ?: 0.0)
            
        val protein = nutriments?.optDouble("proteins_serving")?.takeIf { !it.isNaN() }
            ?: (nutriments?.optDouble("proteins_100g", 0.0)?.times(ratio) ?: 0.0)
            
        val fat = nutriments?.optDouble("fat_serving")?.takeIf { !it.isNaN() }
            ?: (nutriments?.optDouble("fat_100g", 0.0)?.times(ratio) ?: 0.0)
            
        val calories = nutriments?.optDouble("energy-kcal_serving")?.takeIf { !it.isNaN() }
            ?: (nutriments?.optDouble("energy-kcal_100g", 0.0)?.times(ratio) ?: 0.0)

        // Формируем описание порции
        val servingDesc = if (packageSize > 0) servingSizeStr else "100 g/ml"

        return ProductInfo(
            name = name,
            carbs = if (carbs.isNaN()) 0.0 else carbs,
            protein = if (protein.isNaN()) 0.0 else protein,
            fat = if (fat.isNaN()) 0.0 else fat,
            calories = if (calories.isNaN()) 0.0 else calories,
            servingDescription = servingDesc
        )
    }

    private fun extractNumericValue(input: String): Double {
        if (input.isBlank()) return 0.0
        return try {
            // Ищем первое число в строке (может быть с точкой)
            val regex = Regex("(\\d+[.,]?\\d*)")
            val match = regex.find(input)
            match?.value?.replace(',', '.')?.toDouble() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
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
