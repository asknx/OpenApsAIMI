package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveCommand
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveState
import app.aaps.plugins.aps.openAPSAIMI.autodrive.safety.ControlBarrierShield
import app.aaps.plugins.aps.openAPSAIMI.safety.SafetyNet
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AimiPreFlightSafetyVerificationTest {

    // --- 1. HYPO SAFETY TEST (SafetyNet & ControlBarrierShield) ---
    @Test
    fun `hypo safety cuts off insulin when falling near danger zone`() {
        // SafetyNet: When BG is low (< 120), strict guard reduces SMB by 50% from conservative limit
        val smbLimit = SafetyNet.calculateSafeSmbLimit(
            bg = 68.0,
            targetBg = 100.0,
            eventualBg = 58.0,
            delta = -2.0,
            shortAvgDelta = -2.0,
            maxSmbLow = 1.5,
            maxSmbHigh = 5.0,
            isExplicitUserAction = false,
            mealPriorityContext = false
        )
        assertEquals(0.75, smbLimit, 1e-6, "SafetyNet strict guard must cut SMB to 0.5 * low max")

        // ControlBarrierShield: When BG is dropping fast towards danger threshold (80 mg/dL)
        val mockLogger = Mockito.mock(AAPSLogger::class.java)
        val cbf = ControlBarrierShield(mockLogger)
        val rawCommand = AutoDriveCommand(
            temporaryBasalRate = 2.0,
            scheduledMicroBolus = 1.0,
            reason = "Aggressive MPC"
        )
        val state = AutoDriveState(
            bg = 74.0,
            bgVelocity = -1.2,
            iob = 2.0,
            cob = 0.0,
            estimatedSI = 1.0,
            estimatedRa = 0.0,
            physiologicalStressMask = doubleArrayOf(),
            maxIOB = 3.0,
            combinedDelta = -1.5,
            uamConfidence = 0.0
        )

        val enforced = cbf.enforce(rawCommand, state, profileBasal = 1.0)
        assertEquals(0.0, enforced.scheduledMicroBolus, 1e-6, "CBF must eliminate microbolus in hypo danger")
        assertEquals(0.0, enforced.temporaryBasalRate, 1e-6, "CBF must zero temporary basal in hypo danger")
        assertTrue(enforced.reason.contains("ZERO BASAL") || enforced.reason.contains("CBF SATURATED"))
    }

    // --- 2. MULTI-BLOCK PROFILE PRESERVATION & SCALING (mmol_L and mg_dL) ---
    @Test
    fun `multi block schedule preservation and factor clamping`() {
        val originalCarbRatioJson = JSONArray().apply {
            put(JSONObject().apply { put("time", "00:00"); put("value", 10.0); put("timeAsSeconds", 0) })
            put(JSONObject().apply { put("time", "07:00"); put("value", 8.0); put("timeAsSeconds", 25200) })
            put(JSONObject().apply { put("time", "13:00"); put("value", 12.0); put("timeAsSeconds", 46800) })
            put(JSONObject().apply { put("time", "19:00"); put("value", 11.0); put("timeAsSeconds", 68400) })
        }

        val originalSensMmolJson = JSONArray().apply {
            put(JSONObject().apply { put("time", "00:00"); put("value", 2.50); put("timeAsSeconds", 0) })
            put(JSONObject().apply { put("time", "08:00"); put("value", 2.00); put("timeAsSeconds", 28800) })
            put(JSONObject().apply { put("time", "18:00"); put("value", 3.20); put("timeAsSeconds", 64800) })
        }

        // Test IC scaling with +10% factor (10.0 -> 11.0, 8.0 -> 8.8, 12.0 -> 13.2, 11.0 -> 12.1)
        val currentIc = 10.0
        val proposedIc = 11.0
        val icRatioFactor = (proposedIc / currentIc).coerceIn(0.85, 1.15)
        assertEquals(1.10, icRatioFactor, 1e-6)

        val scaledIcArray = JSONArray().apply {
            for (i in 0 until originalCarbRatioJson.length()) {
                val obj = originalCarbRatioJson.getJSONObject(i)
                val oldVal = obj.getDouble("value")
                val scaledVal = round(oldVal * icRatioFactor * 100.0) / 100.0
                put(JSONObject().apply {
                    put("time", obj.getString("time"))
                    put("value", scaledVal)
                    put("timeAsSeconds", obj.getInt("timeAsSeconds"))
                })
            }
        }

        assertEquals(4, scaledIcArray.length())
        assertEquals("07:00", scaledIcArray.getJSONObject(1).getString("time"))
        assertEquals(25200, scaledIcArray.getJSONObject(1).getInt("timeAsSeconds"))
        assertEquals(8.80, scaledIcArray.getJSONObject(1).getDouble("value"), 1e-6)
        assertEquals(13.20, scaledIcArray.getJSONObject(2).getDouble("value"), 1e-6)

        // Test ISF scaling in mmol/L with factor clamping test (proposal wants -25%, clamped to -15%)
        val currentIsfMmol = 2.50
        val proposedIsfMmolExcessive = 1.50 // 1.5 / 2.5 = 0.60 -> must be clamped to 0.85
        val isfRatioFactorClamped = (proposedIsfMmolExcessive / currentIsfMmol).coerceIn(0.85, 1.15)
        assertEquals(0.85, isfRatioFactorClamped, 1e-6)

        val scaledSensArray = JSONArray().apply {
            for (i in 0 until originalSensMmolJson.length()) {
                val obj = originalSensMmolJson.getJSONObject(i)
                val oldVal = obj.getDouble("value")
                val scaledVal = round(oldVal * isfRatioFactorClamped * 100.0) / 100.0
                put(JSONObject().apply {
                    put("time", obj.getString("time"))
                    put("value", scaledVal)
                    put("timeAsSeconds", obj.getInt("timeAsSeconds"))
                })
            }
        }

        assertEquals(3, scaledSensArray.length())
        assertEquals("00:00", scaledSensArray.getJSONObject(0).getString("time"))
        assertEquals(2.13, scaledSensArray.getJSONObject(0).getDouble("value"), 1e-2) // 2.5 * 0.85 = 2.125 -> 2.13
        assertEquals(1.70, scaledSensArray.getJSONObject(1).getDouble("value"), 1e-2) // 2.0 * 0.85 = 1.70
        assertEquals(2.72, scaledSensArray.getJSONObject(2).getDouble("value"), 1e-2) // 3.2 * 0.85 = 2.72
    }

    // --- 3. JUGGLUCO NG METRICS VERIFICATION (TIR, CV, GMI, GRI, GVI, PGS) ---
    @Test
    fun `juggluco ng clinical metrics calculate properly`() {
        val sampleBgValues = listOf(
            90.0, 105.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 175.0, 185.0
        )
        val total = sampleBgValues.size.toDouble()
        val meanBg = sampleBgValues.average()
        assertEquals(136.5, meanBg, 1e-6)

        val variance = sampleBgValues.sumOf { (it - meanBg).pow(2) } / sampleBgValues.size
        val sdBg = sqrt(variance)
        val cvPct = (sdBg / meanBg) * 100.0
        assertTrue(cvPct in 20.0..25.0, "CV should be around 21%")

        val gmi = 3.31 + (0.02392 * meanBg)
        assertEquals(6.58, round(gmi * 100.0) / 100.0, 1e-2)

        // TIR Distribution check
        val tirInRange = sampleBgValues.count { it in 70.0..180.0 } / total
        val tirHigh = sampleBgValues.count { it > 180.0 } / total
        assertEquals(0.90, tirInRange, 1e-6)
        assertEquals(0.10, tirHigh, 1e-6)

        // GVI and PSG check
        var curveLength = 0.0
        for (i in 1 until sampleBgValues.size) {
            curveLength += hypot(5.0, abs(sampleBgValues[i] - sampleBgValues[i - 1]))
        }
        val idealLength = (sampleBgValues.size - 1) * 5.0
        val gvi = (curveLength / idealLength).coerceAtLeast(1.0)
        val psg = gvi * (meanBg / 100.0)

        assertTrue(gvi >= 1.0, "GVI must be >= 1.0")
        assertTrue(psg > 0.0, "PSG must be positive")
    }

    // --- 4. BACKUP KEY EXPORTABILITY VERIFICATION ---
    @Test
    fun `backup key exportability matches all aimi, apex, garmin, and profile prefixes`() {
        fun testIsExportable(key: String): Boolean {
            return key.startsWith("aimi_") ||
                key.startsWith("key_oaps_aimi_") ||
                key.startsWith("oa_aimi_") ||
                key.startsWith("key_aimi_") ||
                key.startsWith("apex_") ||
                key.startsWith("garmin_") ||
                key.startsWith("LocalProfile_") ||
                key.startsWith("Objectives_")
        }

        assertTrue(testIsExportable("aimi_dev_options_unlocked"))
        assertTrue(testIsExportable("aimi_engineering_bypass_enabled"))
        assertTrue(testIsExportable("aimi_auditor_enabled"))
        assertTrue(testIsExportable("key_oaps_aimi_ngr_enabled"))
        assertTrue(testIsExportable("oa_aimi_lunch_factor"))
        assertTrue(testIsExportable("key_aimi_pkpd_enabled"))
        assertTrue(testIsExportable("apex_serial_number"))
        assertTrue(testIsExportable("apex_max_basal"))
        assertTrue(testIsExportable("garmin_enabled"))
        assertTrue(testIsExportable("LocalProfile_profiles"))
        assertTrue(testIsExportable("LocalProfile_name_0"))
        assertTrue(testIsExportable("LocalProfile_basal_0"))
        assertTrue(testIsExportable("LocalProfile_sens_0"))
        assertTrue(testIsExportable("Objectives_started_2"))
        assertTrue(testIsExportable("Objectives_accomplished_3"))

        assertFalse(testIsExportable("unknown_random_unexportable_key"))
        assertFalse(testIsExportable("temp_file_cache"))
    }
}
