package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.ExportableKeys
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiAdvisorService
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveCommand
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveState
import app.aaps.plugins.aps.openAPSAIMI.autodrive.safety.ControlBarrierShield
import app.aaps.plugins.aps.openAPSAIMI.safety.SafetyNet
import kotlin.math.round
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        val scaledIcArray = AimiAdvisorService.scaleProfileScheduleArray(originalCarbRatioJson, currentIc, proposedIc)

        assertEquals(4, scaledIcArray.length())
        assertEquals("07:00", scaledIcArray.getJSONObject(1).getString("time"))
        assertEquals(25200, scaledIcArray.getJSONObject(1).getInt("timeAsSeconds"))
        assertEquals(8.80, scaledIcArray.getJSONObject(1).getDouble("value"), 1e-6)
        assertEquals(13.20, scaledIcArray.getJSONObject(2).getDouble("value"), 1e-6)

        // Test ISF scaling in mmol/L with factor clamping test (proposal wants -40%, clamped to -15% / 0.85)
        val currentIsfMmol = 2.50
        val proposedIsfMmolExcessive = 1.50
        val scaledSensArray = AimiAdvisorService.scaleProfileScheduleArray(originalSensMmolJson, currentIsfMmol, proposedIsfMmolExcessive)

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
        val metrics = AimiAdvisorService.calculateJugglucoMetrics(sampleBgValues)

        assertEquals(136.5, metrics.meanBg, 1e-6)
        assertTrue(metrics.cvPct in 20.0..25.0, "CV should be around 21%")
        assertEquals(6.58, round(metrics.gmi * 100.0) / 100.0, 1e-2)

        // TIR Distribution check
        assertEquals(0.90, metrics.tirInRange, 1e-6)
        assertEquals(0.10, metrics.tirHigh, 1e-6)
        assertEquals(0.0, metrics.tirVeryLow, 1e-6)
        assertEquals(0.0, metrics.tirLow, 1e-6)
        assertEquals(0.0, metrics.tirVeryHigh, 1e-6)

        // GVI and PSG check
        assertTrue(metrics.gvi >= 1.0, "GVI must be >= 1.0")
        assertTrue(metrics.psg > 0.0, "PSG must be positive")

        // GRI check
        assertTrue(metrics.gri in 0.0..100.0, "GRI must be in [0, 100]")
        assertEquals("Zone A", metrics.griZone)
    }

    // --- 4. BACKUP KEY EXPORTABILITY VERIFICATION ---
    @Test
    fun `backup key exportability matches all aimi, apex, garmin, and profile prefixes`() {
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("aimi_dev_options_unlocked"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("aimi_engineering_bypass_enabled"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("aimi_auditor_enabled"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("key_oaps_aimi_ngr_enabled"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("oa_aimi_lunch_factor"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("key_aimi_pkpd_enabled"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("apex_serial_number"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("apex_max_basal"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("garmin_enabled"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("LocalProfile_profiles"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("LocalProfile_name_0"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("LocalProfile_basal_0"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("LocalProfile_sens_0"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("Objectives_started_2"))
        assertTrue(ExportableKeys.isCustomExportableKeyPrefix("Objectives_accomplished_3"))

        assertFalse(ExportableKeys.isCustomExportableKeyPrefix("unknown_random_unexportable_key"))
        assertFalse(ExportableKeys.isCustomExportableKeyPrefix("temp_file_cache"))
    }

    // --- 5. CUSTOM ENDPOINT SECURITY AND CONFIGURATION ERROR VERIFICATION ---
    @Test
    fun `normalizeEndpointUrl strictly rejects empty or invalid custom url and normalizes valid http paths`() {
        // Blank or whitespace URL must throw IllegalArgumentException without falling back to OpenAI
        assertThrows<IllegalArgumentException> {
            AuditorAIService.normalizeEndpointUrl("")
        }
        assertThrows<IllegalArgumentException> {
            AuditorAIService.normalizeEndpointUrl("   ")
        }
        // Non-http(s) schemes must throw IllegalArgumentException
        assertThrows<IllegalArgumentException> {
            AuditorAIService.normalizeEndpointUrl("ftp://localhost:8080/v1")
        }
        assertThrows<IllegalArgumentException> {
            AuditorAIService.normalizeEndpointUrl("file:///etc/hosts")
        }

        // Valid http/https endpoints must normalize properly
        assertEquals(
            "http://localhost:8080/v1/chat/completions",
            AuditorAIService.normalizeEndpointUrl("http://localhost:8080")
        )
        assertEquals(
            "http://192.168.1.50:11434/v1/chat/completions",
            AuditorAIService.normalizeEndpointUrl("http://192.168.1.50:11434/v1")
        )
        assertEquals(
            "https://api.mycustomllm.org/chat/completions",
            AuditorAIService.normalizeEndpointUrl("https://api.mycustomllm.org/chat/completions")
        )
    }
}
