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
data class AdvisorMetrics(
    val periodLabel: String,
    val tir70_180: Double,          // Fraction (0-1)
    val tir70_140: Double,
    val timeBelow70: Double,
    val timeBelow54: Double,
    val timeAbove180: Double,
    val timeAbove250: Double,
    val meanBg: Double,             // mg/dL
    val gmi: Double,                // % (derived)
    val tdd: Double,                // U/day
    val basalPercent: Double,       // Basal as fraction of TDD
    val hypoEvents: Int,
    val severeHypoEvents: Int,
    val hyperEvents: Int,
    val todayTir: Double?,    // (0-1) or null
    val todayTdd: Double?,     // U or null
    val therapyStats: TherapyStats? = null
)

data class TherapyStats(
    val meals: List<MealOutcome>,
    val totalCarbs: Double,
    val totalInsulin: Double,
    val totalSmb: Double,
    val totalBasal: Double
)

data class MealOutcome(
    val timestamp: Long,
    val carbs: Double,
    val insulin2h: Double, // Bolus + SMB in 2h window
    val bgStart: Double,
    val bgMax2h: Double,
    val bgEnd4h: Double,
    val success: Boolean
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
