package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FatSecretServiceTest {

    private val logger = mockk<AAPSLogger>(relaxed = true)
    private val preferences = mockk<Preferences>(relaxed = true)
    private val service = FatSecretService(preferences, logger)

    @Test
    fun `test uses first serving as default`() {
        val json = JSONObject("""
            {
                "food_name": "Energy Drink",
                "servings": {
                    "serving": [
                        {
                            "serving_description": "1 can (449 ml)",
                            "carbohydrate": "54.0",
                            "protein": "0.0",
                            "fat": "0.0",
                            "calories": "225"
                        },
                        {
                            "serving_description": "100 ml",
                            "carbohydrate": "12.0",
                            "protein": "0.0",
                            "fat": "0.0",
                            "calories": "50"
                        }
                    ]
                }
            }
        """.trimIndent())

        val info = service.parseFoodJson(json)
        
        // Should pick the first one (54.0) instead of the 100ml one
        assertEquals(54.0, info.carbs, 0.01)
        assertEquals("1 can (449 ml)", info.servingDescription)
    }

    @Test
    fun `test handles single serving object`() {
        val json = JSONObject("""
            {
                "food_name": "Chocolate Bar",
                "servings": {
                    "serving": {
                        "serving_description": "1 bar (50 g)",
                        "carbohydrate": "30.0",
                        "protein": "2.0",
                        "fat": "15.0",
                        "calories": "250"
                    }
                }
            }
        """.trimIndent())

        val info = service.parseFoodJson(json)
        
        assertEquals(30.0, info.carbs, 0.01)
        assertEquals("1 bar (50 g)", info.servingDescription)
    }
}
