package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import app.aaps.core.interfaces.logging.AAPSLogger
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenFoodFactsServiceTest {

    private val logger = mockk<AAPSLogger>(relaxed = true)
    private val service = OpenFoodFactsService(logger)

    @Test
    fun `test auto-scaling with serving_size`() {
        val json = JSONObject("""
            {
                "product_name": "Adrenaline Rush",
                "serving_size": "449 ml",
                "nutriments": {
                    "carbohydrates_100g": 12.0,
                    "proteins_100g": 0.0,
                    "fat_100g": 0.0,
                    "energy-kcal_100g": 50.0
                }
            }
        """.trimIndent())

        val info = service.parseProductJson(json)
        
        // 12.0 * (449 / 100) = 53.88
        assertEquals(53.88, info.carbs, 0.01)
        assertEquals("449 ml", info.servingDescription)
    }

    @Test
    fun `test preference for serving values`() {
        val json = JSONObject("""
            {
                "product_name": "Snack Bar",
                "serving_size": "50g",
                "nutriments": {
                    "carbohydrates_100g": 60.0,
                    "carbohydrates_serving": 25.0
                }
            }
        """.trimIndent())

        val info = service.parseProductJson(json)
        
        // Should use 25.0 instead of 60.0 * 0.5 = 30.0
        assertEquals(25.0, info.carbs, 0.01)
    }

    @Test
    fun `test fallback to 100g when no quantity found`() {
        val json = JSONObject("""
            {
                "product_name": "Generic Item",
                "nutriments": {
                    "carbohydrates_100g": 10.0
                }
            }
        """.trimIndent())

        val info = service.parseProductJson(json)
        
        assertEquals(10.0, info.carbs, 0.01)
        assertEquals("100 g/ml", info.servingDescription)
    }

    @Test
    fun `test extraction of numeric value from complex string`() {
        val json = JSONObject("""
            {
                "product_name": "Liquid",
                "quantity": "Net weight: 500ml e",
                "nutriments": {
                    "carbohydrates_100g": 5.0
                }
            }
        """.trimIndent())

        val info = service.parseProductJson(json)
        
        // 5.0 * 5 = 25.0
        assertEquals(25.0, info.carbs, 0.01)
        assertEquals("Net weight: 500ml e", info.servingDescription)
    }
}
