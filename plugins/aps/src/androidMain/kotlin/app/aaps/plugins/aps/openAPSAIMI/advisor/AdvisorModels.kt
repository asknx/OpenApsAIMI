package app.aaps.plugins.aps.openAPSAIMI.advisor

import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority

/**
 * =============================================================================
 * METRICS & STATS MODELS
 * =============================================================================
 */

/**
 * Metrics collected for analysis.
 */
data class AgpHourStats(
    val hour: Int,
    val median: Double,
    val p10: Double,
    val p25: Double,
    val p75: Double,
    val p90: Double
)

data class DailyTirItem(
    val dateMillis: Long,
    val dateStr: String,
    val tirPct: Double,
    val meanBg: Double
)

data class AdvisorMetrics(
    val periodLabel: String,
    // 5-Zone TIR fractions (0.0 .. 1.0)
    val tirVeryLow: Double = 0.0,       // < 54 mg/dL (< 3.0 mmol/L)
    val tirLow: Double = 0.0,           // 54-69 mg/dL (3.0 - 3.8 mmol/L)
    val tirInRange: Double = 0.65,      // 70-180 mg/dL (3.9 - 10.0 mmol/L)
    val tirHigh: Double = 0.0,          // 181-250 mg/dL (10.1 - 13.9 mmol/L)
    val tirVeryHigh: Double = 0.0,      // > 250 mg/dL (> 13.9 mmol/L)
    // Legacy compatibility fields
    val tir70_180: Double = tirInRange,
    val tir70_140: Double = 0.40,
    val timeBelow70: Double = tirLow + tirVeryLow,
    val timeBelow54: Double = tirVeryLow,
    val timeAbove180: Double = tirHigh + tirVeryHigh,
    val timeAbove250: Double = tirVeryHigh,
    // Mean & GMI
    val meanBg: Double = 160.0,         // mg/dL
    val gmi: Double = 3.31 + (0.02392 * meanBg),
    val tdd: Double = 45.0,
    val basalPercent: Double = 0.50,
    // Statistical metrics
    val medianBg: Double = meanBg,
    val p10Bg: Double = 80.0,
    val p25Bg: Double = 105.0,
    val p75Bg: Double = 160.0,
    val p90Bg: Double = 195.0,
    val iqrBg: Double = 55.0,
    val sdBg: Double = 35.0,
    val cvPct: Double = 25.0,
    val gvi: Double = 1.2,
    val psg: Double = 1.9,
    val gri: Double = 25.0,
    val griZone: String = "Zone B",
    val griHypoComponent: Double = 5.0,
    val griHyperComponent: Double = 12.0,
    // AGP & Daily Distribution
    val hourlyAgp: List<AgpHourStats> = emptyList(),
    val dailyTirList: List<DailyTirItem> = emptyList(),
    // Episodes (>= 15 min deviations)
    val hypoEvents: Int = 0,
    val severeHypoEvents: Int = 0,
    val hyperEvents: Int = 0,
    val severeHyperEvents: Int = 0,
    val avgHypoDurationMin: Int = 0,
    val maxHypoDurationMin: Int = 0,
    val totalHypoMinutes: Int = 0,
    val avgHyperDurationMin: Int = 0,
    val maxHyperDurationMin: Int = 0,
    val totalHyperMinutes: Int = 0,
    val hypoPeakIntervalStr: String = "--",
    val hyperPeakIntervalStr: String = "--",
    // Units
    val unitsStr: String = "mg/dL",
    val isMmol: Boolean = false,
    val todayTir: Double? = null,
    val todayTdd: Double? = null,
    val therapyStats: TherapyStats? = null
)

data class TherapyStats(
    val meals: List<MealOutcome>,
    val totalCarbs: Double,
    val totalInsulin: Double,
    val totalSmb: Double,
    val totalBasal: Double,
    val avgPrebolusMin: Double = 0.0,
    val mealSuccessRate: Double = 0.0,
    val bFastAvgPeak: Double? = null,
    val lunchAvgPeak: Double? = null,
    val dinnerAvgPeak: Double? = null,
    val therapyEventsSummary: String? = null
)

data class MealOutcome(
    val timestamp: Long,
    val carbs: Double,
    val insulin2h: Double, // Bolus + SMB in 2h window
    val bgStart: Double,
    val bgMax2h: Double,
    val bgEnd4h: Double,
    val success: Boolean,
    val prebolusMinutes: Int = 0,
    val deltaPeak: Double = 0.0,
    val bgEnd3h: Double = 0.0,
    val note: String? = null
)

data class HourlyBasalProposal(
    val hour: Int,
    val currentRate: Double,
    val proposedRate: Double,
    val agpMedian: Double,
    val reason: String
)

data class AimiSettingProposal(
    val prefKey: String,
    val title: String,
    val currentValue: String,
    val proposedValue: String,
    val reason: String,
    val isEnabledByDefault: Boolean = true
)

data class FullAdvisorProposal(
    val generatedAt: Long,
    val periodDays: Int,
    val hourlyBasal: List<HourlyBasalProposal>,
    val currentIc: Double,
    val proposedIc: Double,
    val currentIsf: Double,
    val proposedIsf: Double,
    val aimiSettings: List<AimiSettingProposal>,
    val clinicalSummary: String
)

/**
 * Severity levels for assessments and flags.
 */
sealed class AdvisorSeverity {
    object Good : AdvisorSeverity()
    object Warning : AdvisorSeverity()
    object Critical : AdvisorSeverity()
}

/**
 * =============================================================================
 * UI & REPORTING MODELS
 * =============================================================================
 */

/**
 * A single recommendation from the advisor.
 * Uses Resource IDs for localization.
 */
data class AimiRecommendation(
    val titleResId: Int,
    val descriptionResId: Int,
    val priority: AimiPriority,
    val domain: AimiDomain,
    val action: AimiAction? = null,
    val descriptionArgs: List<String> = emptyList() // For dynamic description formatting
)

/**
 * Full advisor report.
 */
data class AdvisorReport(
    val generatedAt: Long,
    val metrics: AdvisorMetrics,
    val overallScore: Double,           // 0-10 score
    val overallSeverity: AdvisorSeverity,
    val overallAssessment: String,
    val recommendations: List<AimiRecommendation>,
    val summary: String
)

/**
 * =============================================================================
 * RULES ENGINE MODELS
 * =============================================================================
 */

data class AdvisorContext(
    val metrics: AdvisorMetrics,
    val profile: AimiProfileSnapshot,
    val prefs: AimiPrefsSnapshot,
    val pkpdPrefs: PkpdPrefsSnapshot
)

data class AimiProfileSnapshot(
    val nightBasal: Double,      // U/h (average or specific block)
    val icRatio: Double,         // g/U (weighted average)
    val isf: Double,             // mg/dL/U (weighted average)
    val targetBg: Double,        // mg/dL (weighted average)
    val dia: Double,             // hours
    val totalBasal: Double,      // U/day (sum of profile basal)
    val originalName: String
)

data class AimiPrefsSnapshot(
    val maxSmb: Double,                     // U
    val lunchFactor: Double,                // multiplier
    val unifiedReactivityFactor: Double,    // multiplier
    val autodriveMaxBasal: Double,          // U/h
    val allToggles: Map<String, Boolean>    // All AIMI related boolean settings
)

/**
 * =============================================================================
 * PKPD MODELS
 * =============================================================================
 */

data class PkpdPrefsSnapshot(
    val pkpdEnabled: Boolean,
    val initialDiaH: Double,
    val initialPeakMin: Double,
    val boundsDiaMinH: Double,
    val boundsDiaMaxH: Double,
    val boundsPeakMinMin: Double,
    val boundsPeakMinMax: Double,
    val maxDiaChangePerDayH: Double,
    val maxPeakChangePerDayMin: Double,
    val isfFusionMinFactor: Double,
    val isfFusionMaxFactor: Double,
    val isfFusionMaxChangePerTick: Double,
    val smbTailThreshold: Double,
    val smbTailDamping: Double,
    val smbExerciseDamping: Double,
    val smbLateFatDamping: Double
)
