package app.aaps.plugins.aps.openAPSAIMI.advisor

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.hypot
import kotlin.math.abs
import java.util.Calendar
import app.aaps.plugins.aps.R
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.*
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.profile.Profile

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import app.aaps.plugins.aps.openAPSAIMI.plugins.impl.StableControlPlugin
import app.aaps.plugins.aps.openAPSAIMI.plugins.impl.SafetyAggressionPlugin
import app.aaps.plugins.aps.openAPSAIMI.plugins.impl.TherapyAdvisorPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * =============================================================================
 * AIMI ADVISOR SERVICE
 * =============================================================================
 * 
 * Analyzes metrics and provides scored recommendations.
 * Uses Resource IDs for localization support.
 * =============================================================================
 */
class AimiAdvisorService {
    data class BasalHourProposal(
        val hour: Int,
        val current: Double,
        val proposed: Double
    )

    data class BasalProfileProposal(
        val generatedAt: Long,
        val periodDays: Int,
        val strategy: String,
        val scalingFactor: Double,
        val rationale: String,
        val rows: List<BasalHourProposal>
    )

    private val profileFunction: app.aaps.core.interfaces.profile.ProfileFunction?
    private val persistenceLayer: app.aaps.core.interfaces.db.PersistenceLayer?
    private val preferences: app.aaps.core.keys.interfaces.Preferences?
    private val unifiedReactivityLearner: app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner?
    private val tddCalculator: app.aaps.core.interfaces.stats.TddCalculator?
    private val tirCalculator: app.aaps.core.interfaces.stats.TirCalculator?
    private val rh: app.aaps.core.interfaces.resources.ResourceHelper?
    private val aapsLogger: app.aaps.core.interfaces.logging.AAPSLogger?
    private val dateUtil: DateUtil?
    private val rxBus: RxBus?
    private val auditorAIService: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService?
    private val pluginManager: app.aaps.plugins.aps.openAPSAIMI.plugins.AimiPluginManager

    // Constructor injection for dependencies
    constructor(
        profileFunction: app.aaps.core.interfaces.profile.ProfileFunction? = null,
        persistenceLayer: app.aaps.core.interfaces.db.PersistenceLayer? = null,
        preferences: app.aaps.core.keys.interfaces.Preferences? = null,
        rh: app.aaps.core.interfaces.resources.ResourceHelper? = null,
        unifiedReactivityLearner: app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner? = null,
        tddCalculator: app.aaps.core.interfaces.stats.TddCalculator? = null,
        tirCalculator: app.aaps.core.interfaces.stats.TirCalculator? = null,
        pluginManager: app.aaps.plugins.aps.openAPSAIMI.plugins.AimiPluginManager? = null,
        auditorAIService: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService? = null,
        aapsLogger: app.aaps.core.interfaces.logging.AAPSLogger? = null,
        dateUtil: DateUtil? = null,
        rxBus: RxBus? = null
    ) {
        this.profileFunction = profileFunction
        this.persistenceLayer = persistenceLayer
        this.preferences = preferences
        this.rh = rh
        this.unifiedReactivityLearner = unifiedReactivityLearner
        this.tddCalculator = tddCalculator
        this.tirCalculator = tirCalculator
        this.aapsLogger = aapsLogger
        this.dateUtil = dateUtil
        this.rxBus = rxBus
        this.auditorAIService = auditorAIService
        this.pluginManager = pluginManager ?: app.aaps.plugins.aps.openAPSAIMI.plugins.AimiPluginManager(aapsLogger ?: object : app.aaps.core.interfaces.logging.AAPSLogger {
            override fun debug(message: String) {}
            override fun debug(enable: Boolean, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, accessor: () -> String) {}
            override fun debug(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun warn(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun warn(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun info(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun info(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, message: String, throwable: Throwable) {}
            override fun error(tag: app.aaps.core.interfaces.logging.LTag, format: String, vararg arguments: Any?) {}
            override fun error(message: String) {}
            override fun error(message: String, throwable: Throwable) {}
            override fun error(format: String, vararg arguments: Any?) {}
            override fun debug(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun info(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun warn(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
            override fun error(className: String, methodName: String, lineNumber: Int, tag: app.aaps.core.interfaces.logging.LTag, message: String) {}
        })
        
        // Register core and sub-modules
        this.pluginManager.register(StableControlPlugin())
        this.pluginManager.register(SafetyAggressionPlugin())
        this.pluginManager.register(TherapyAdvisorPlugin(persistenceLayer))
    }

    /**
     * Generate a full advisor report for the specified period.
     */
    fun generateReport(periodDays: Int = 7, history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog> = emptyList()): AdvisorReport {
        val context = collectContext(periodDays)
        val score = computeGlobalScore(context.metrics)
        val severity = classifySeverity(score)
        val recommendations = generateRecommendations(context, history).toMutableList()
        
        // PKPD Analysis - now returns AimiRecommendation
        val pkpdSuggestions = PkpdAdvisor().analysePkpd(context.metrics, context.pkpdPrefs, rh!!)
        
        // Filter PKPD suggestions based on history (48h cooldown)
        pkpdSuggestions.forEach { rec ->
            if (shouldShowRecommendation(rec, history)) {
                recommendations.add(rec)
            }
        }
        
        return AdvisorReport(
            generatedAt = System.currentTimeMillis(),
            metrics = context.metrics,
            overallScore = score,
            overallSeverity = severity,
            overallAssessment = getAssessmentLabel(score),
            recommendations = recommendations,
            summary = formatSummary(context.metrics)
        )
    }

    /**
     * Gather all necessary data for analysis.
     */
    fun collectContext(periodDays: Int): AdvisorContext {
        if (persistenceLayer == null || profileFunction == null || preferences == null) {
            return getEmptyContext()
        }

        // 1. Calculate Metrics (from Persistence Layer)
        val metrics = calculateMetrics(periodDays)

        // 2. Snapshot Profile
        val profile = runBlocking { profileFunction.getProfile() }
        val profileSnapshot = if (profile != null) {
            val totalBasalCalc = (0 until 24).sumOf { h -> profile.getBasal((h * 3600).toLong()) }
            
            AimiProfileSnapshot(
                nightBasal = profile.getBasal(0L), // 00:00 basal
                icRatio = calculateWeightedAverage(profile.getIcsValues()),
                isf = calculateWeightedAverage(profile.getIsfsMgdlValues()),
                targetBg = calculateWeightedAverage(profile.getSingleTargetsMgdl()),
                dia = profile.iCfg.dia,
                totalBasal = totalBasalCalc,
                originalName = runBlocking { profileFunction.getOriginalProfileName() }
            )
        } else {
            AimiProfileSnapshot(0.5, 10.0, 40.0, 100.0, 5.0, 12.0, "Unknown") // Fallback
        }

        // 3. Snapshot Preferences (Captured all AIMI and key AAPS settings)
        val aimiToggles = mutableMapOf<String, Boolean>()
        // Core toggles
        aimiToggles[BooleanKey.ApsUseDynamicSensitivity.key] = preferences.get(BooleanKey.ApsUseDynamicSensitivity)
        aimiToggles[BooleanKey.ApsUseAutosens.key] = preferences.get(BooleanKey.ApsUseAutosens)
        aimiToggles[BooleanKey.ApsUseSmb.key] = preferences.get(BooleanKey.ApsUseSmb)
        aimiToggles[BooleanKey.ApsUseUam.key] = preferences.get(BooleanKey.ApsUseUam)
        aimiToggles[BooleanKey.ApsSensitivityRaisesTarget.key] = preferences.get(BooleanKey.ApsSensitivityRaisesTarget)
        aimiToggles[BooleanKey.ApsResistanceLowersTarget.key] = preferences.get(BooleanKey.ApsResistanceLowersTarget)
        aimiToggles[BooleanKey.AimiPhysioAssistantEnable.key] = preferences.get(BooleanKey.AimiPhysioAssistantEnable)
        aimiToggles[BooleanKey.OApsAIMIPkpdEnabled.key] = preferences.get(BooleanKey.OApsAIMIPkpdEnabled)

        val prefsSnapshot = AimiPrefsSnapshot(
            maxSmb = preferences.get(DoubleKey.OApsAIMIMaxSMB),
            lunchFactor = preferences.get(DoubleKey.OApsAIMILunchFactor),
            unifiedReactivityFactor = 1.0, // Default for now
            autodriveMaxBasal = preferences.get(DoubleKey.autodriveMaxBasal),
            allToggles = aimiToggles
        )
        
        // 4. Snapshot PKPD Preferences
        val pkpdSnapshot = PkpdPrefsSnapshot(
            pkpdEnabled = preferences.get(BooleanKey.OApsAIMIPkpdEnabled),
            initialDiaH = preferences.get(DoubleKey.OApsAIMIPkpdInitialDiaH),
            initialPeakMin = preferences.get(DoubleKey.OApsAIMIPkpdInitialPeakMin),
            boundsDiaMinH = preferences.get(DoubleKey.OApsAIMIPkpdBoundsDiaMinH),
            boundsDiaMaxH = preferences.get(DoubleKey.OApsAIMIPkpdBoundsDiaMaxH),
            boundsPeakMinMin = preferences.get(DoubleKey.OApsAIMIPkpdBoundsPeakMinMin),
            boundsPeakMinMax = preferences.get(DoubleKey.OApsAIMIPkpdBoundsPeakMinMax),
            maxDiaChangePerDayH = preferences.get(DoubleKey.OApsAIMIPkpdMaxDiaChangePerDayH),
            maxPeakChangePerDayMin = preferences.get(DoubleKey.OApsAIMIPkpdMaxPeakChangePerDayMin),
            isfFusionMinFactor = preferences.get(DoubleKey.OApsAIMIIsfFusionMinFactor),
            isfFusionMaxFactor = preferences.get(DoubleKey.OApsAIMIIsfFusionMaxFactor),
            isfFusionMaxChangePerTick = preferences.get(DoubleKey.OApsAIMIIsfFusionMaxChangePerTick),
            smbTailThreshold = preferences.get(DoubleKey.OApsAIMISmbTailThreshold),
            smbTailDamping = preferences.get(DoubleKey.OApsAIMISmbTailDamping),
            smbExerciseDamping = preferences.get(DoubleKey.OApsAIMISmbExerciseDamping),
            smbLateFatDamping = preferences.get(DoubleKey.OApsAIMISmbLateFatDamping)
        )

        return AdvisorContext(metrics, profileSnapshot, prefsSnapshot, pkpdSnapshot)
    }

    private fun calculateWeightedAverage(values: Array<app.aaps.core.interfaces.profile.Profile.ProfileValue>): Double {
        if (values.isEmpty()) return 0.0
        
        var totalWeightedValue = 0.0
        var totalDuration = 0
        
        // Sort by time just in case
        val sorted = values.sortedBy { it.timeAsSeconds }
        
        for (i in sorted.indices) {
            val start = sorted[i].timeAsSeconds
            val end = if (i < sorted.size - 1) sorted[i + 1].timeAsSeconds else 24 * 3600
            val duration = end - start
            
            totalWeightedValue += sorted[i].value * duration
            totalDuration += duration
        }
        
        return if (totalDuration > 0) totalWeightedValue / totalDuration else 0.0
    }

    data class JugglucoMetrics(
        val totalCount: Double,
        val meanBg: Double,
        val sdBg: Double,
        val cvPct: Double,
        val gmi: Double,
        val tirVeryLow: Double,
        val tirLow: Double,
        val tirInRange: Double,
        val tirHigh: Double,
        val tirVeryHigh: Double,
        val tir70_140: Double,
        val cHypo: Double,
        val cHyper: Double,
        val gri: Double,
        val griZone: String,
        val gvi: Double,
        val psg: Double,
        val iqrBg: Double,
        val medianBg: Double,
        val p10Bg: Double,
        val p25Bg: Double,
        val p75Bg: Double,
        val p90Bg: Double
    )

    companion object {
        internal fun scaleProfileScheduleArray(
            existing: JSONArray?,
            currentVal: Double,
            proposedVal: Double
        ): JSONArray {
            val ratioFactor = if (currentVal > 0.0) {
                (proposedVal / currentVal).coerceIn(0.85, 1.15)
            } else 1.0

            return if (existing != null && existing.length() > 0) {
                JSONArray().apply {
                    for (i in 0 until existing.length()) {
                        val obj = existing.getJSONObject(i)
                        val oldVal = obj.optDouble("value", currentVal)
                        val scaledVal = kotlin.math.round(oldVal * ratioFactor * 100.0) / 100.0
                        put(JSONObject().apply {
                            put("time", obj.optString("time", "00:00"))
                            put("value", scaledVal)
                            put("timeAsSeconds", obj.optInt("timeAsSeconds", 0))
                        })
                    }
                }
            } else {
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("time", "00:00")
                        put("value", kotlin.math.round(proposedVal * 100.0) / 100.0)
                        put("timeAsSeconds", 0)
                    })
                }
            }
        }

        internal fun calculateJugglucoMetrics(values: List<Double>): JugglucoMetrics {
            if (values.isEmpty()) {
                val defaultMean = 160.0
                return JugglucoMetrics(
                    totalCount = 0.0,
                    meanBg = defaultMean,
                    sdBg = 0.0,
                    cvPct = 0.0,
                    gmi = 3.31 + (0.02392 * defaultMean),
                    tirVeryLow = 0.01,
                    tirLow = 0.04,
                    tirInRange = 0.65,
                    tirHigh = 0.25,
                    tirVeryHigh = 0.05,
                    tir70_140 = 0.40,
                    cHypo = 0.0,
                    cHyper = 0.0,
                    gri = 0.0,
                    griZone = "Zone A",
                    gvi = 1.0,
                    psg = defaultMean / 100.0,
                    iqrBg = 0.0,
                    medianBg = defaultMean,
                    p10Bg = defaultMean,
                    p25Bg = defaultMean,
                    p75Bg = defaultMean,
                    p90Bg = defaultMean
                )
            }

            val totalCount = values.size.toDouble()
            val tirVeryLow = values.count { it < 54.0 } / totalCount
            val tirLow = values.count { it in 54.0..69.99 } / totalCount
            val tirInRange = values.count { it in 70.0..180.0 } / totalCount
            val tirHigh = values.count { it in 180.01..250.0 } / totalCount
            val tirVeryHigh = values.count { it > 250.0 } / totalCount
            val tir70_140 = values.count { it in 70.0..140.0 } / totalCount

            val sorted = values.sorted()
            val meanBg = values.average()
            val medianBg = sorted[sorted.size / 2]
            val p10Bg = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)]
            val p25Bg = sorted[(sorted.size * 0.25).toInt().coerceIn(0, sorted.size - 1)]
            val p75Bg = sorted[(sorted.size * 0.75).toInt().coerceIn(0, sorted.size - 1)]
            val p90Bg = sorted[(sorted.size * 0.90).toInt().coerceIn(0, sorted.size - 1)]
            val iqrBg = p75Bg - p25Bg

            val variance = values.sumOf { (it - meanBg).pow(2) } / values.size
            val sdBg = sqrt(variance)
            val cvPct = if (meanBg > 0.0) (sdBg / meanBg) * 100.0 else 0.0
            val gmi = 3.31 + (0.02392 * meanBg)

            // GVI (Glycemic Variability Index)
            var actualCurveLength = 0.0
            for (i in 1 until values.size) {
                val deltaVal = abs(values[i] - values[i - 1])
                actualCurveLength += hypot(5.0, deltaVal)
            }
            val idealLength = (values.size - 1).coerceAtLeast(1) * 5.0
            val gvi = (actualCurveLength / idealLength).coerceAtLeast(1.0)
            val psg = gvi * (meanBg / 100.0)

            // GRI (Glycemia Risk Index) 0-100
            val cHypo = (tirVeryLow * 100.0) + (0.8 * tirLow * 100.0)
            val cHyper = (tirVeryHigh * 100.0) + (0.5 * tirHigh * 100.0)
            val gri = ((3.0 * cHypo) + (1.6 * cHyper)).coerceIn(0.0, 100.0)
            val griZone = when {
                gri < 20.0 -> "Zone A"
                gri < 40.0 -> "Zone B"
                gri < 60.0 -> "Zone C"
                gri < 80.0 -> "Zone D"
                else -> "Zone E"
            }

            return JugglucoMetrics(
                totalCount = totalCount,
                meanBg = meanBg,
                sdBg = sdBg,
                cvPct = cvPct,
                gmi = gmi,
                tirVeryLow = tirVeryLow,
                tirLow = tirLow,
                tirInRange = tirInRange,
                tirHigh = tirHigh,
                tirVeryHigh = tirVeryHigh,
                tir70_140 = tir70_140,
                cHypo = cHypo,
                cHyper = cHyper,
                gri = gri,
                griZone = griZone,
                gvi = gvi,
                psg = psg,
                iqrBg = iqrBg,
                medianBg = medianBg,
                p10Bg = p10Bg,
                p25Bg = p25Bg,
                p75Bg = p75Bg,
                p90Bg = p90Bg
            )
        }
    }

    fun calculateMetrics(days: Int): AdvisorMetrics {
        val now = System.currentTimeMillis()
        val fromTime = now - (days * 24 * 3600 * 1000L)
        val isMmol = profileFunction?.getUnits() == GlucoseUnit.MMOL
        val unitsStr = if (isMmol) "mmol/L" else "mg/dL"

        val rawReadings = persistenceLayer?.let {
            runBlocking { it.getBgReadingsDataFromTimeToTime(fromTime, now, ascending = true) }
        } ?: emptyList()

        val validReadings = rawReadings.filter { it.value > 20.0 }
        val values = validReadings.map { it.value }

        // Real TDD
        var tdd = 45.0
        var basalPercent = 0.50
        var todayTdd: Double? = null
        if (tddCalculator != null) {
            try {
                runBlocking {
                    val tdds = tddCalculator.calculate(days.toLong(), true)
                    val avgTdd = tddCalculator.averageTDD(tdds)
                    if (avgTdd != null) {
                        tdd = avgTdd.data.totalAmount
                        if (tdd > 0) basalPercent = avgTdd.data.basalAmount / tdd
                    }
                    val today = tddCalculator.calculateToday()
                    if (today != null) todayTdd = today.totalAmount
                }
            } catch (_: Exception) {}
        }

        val therapyStats = collectTherapyStats(days)

        if (values.isEmpty()) {
            return AdvisorMetrics(
                periodLabel = "Last $days days",
                tirVeryLow = 0.01,
                tirLow = 0.04,
                tirInRange = 0.65,
                tirHigh = 0.25,
                tirVeryHigh = 0.05,
                meanBg = 160.0,
                gmi = 3.31 + (0.02392 * 160.0),
                tdd = tdd,
                basalPercent = basalPercent,
                unitsStr = unitsStr,
                isMmol = isMmol,
                todayTdd = todayTdd,
                therapyStats = therapyStats
            )
        }

        val jm = calculateJugglucoMetrics(values)
        val totalCount = jm.totalCount
        val tirVeryLow = jm.tirVeryLow
        val tirLow = jm.tirLow
        val tirInRange = jm.tirInRange
        val tirHigh = jm.tirHigh
        val tirVeryHigh = jm.tirVeryHigh

        val sorted = values.sorted()
        val meanBg = jm.meanBg
        val medianBg = jm.medianBg
        val p10Bg = jm.p10Bg
        val p25Bg = jm.p25Bg
        val p75Bg = jm.p75Bg
        val p90Bg = jm.p90Bg
        val iqrBg = jm.iqrBg

        val sdBg = jm.sdBg
        val cvPct = jm.cvPct

        val gvi = jm.gvi
        val psg = jm.psg
        val cHypo = jm.cHypo
        val cHyper = jm.cHyper
        val gri = jm.gri
        val griZone = jm.griZone

        // Hourly AGP (24 bins)
        val cal = Calendar.getInstance()
        val hourlyBins = (0..23).map { mutableListOf<Double>() }
        validReadings.forEach { r ->
            cal.timeInMillis = r.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourlyBins[hour].add(r.value)
        }
        val hourlyAgp = (0..23).map { h ->
            val bValues = hourlyBins[h].sorted()
            if (bValues.isNotEmpty()) {
                AgpHourStats(
                    hour = h,
                    median = bValues[bValues.size / 2],
                    p10 = bValues[(bValues.size * 0.10).toInt().coerceIn(0, bValues.size - 1)],
                    p25 = bValues[(bValues.size * 0.25).toInt().coerceIn(0, bValues.size - 1)],
                    p75 = bValues[(bValues.size * 0.75).toInt().coerceIn(0, bValues.size - 1)],
                    p90 = bValues[(bValues.size * 0.90).toInt().coerceIn(0, bValues.size - 1)]
                )
            } else {
                AgpHourStats(h, meanBg, meanBg * 0.7, meanBg * 0.85, meanBg * 1.15, meanBg * 1.3)
            }
        }

        // Day-by-Day TIR Grid
        val sdfDay = SimpleDateFormat("dd.MM", Locale.getDefault())
        val dayGroups = validReadings.groupBy {
            cal.timeInMillis = it.timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        val dailyTirList = dayGroups.map { (dayMillis, readingsInDay) ->
            val inRangeCount = readingsInDay.count { it.value in 70.0..180.0 }
            val tirPct = (inRangeCount.toDouble() / readingsInDay.size.coerceAtLeast(1)) * 100.0
            DailyTirItem(
                dateMillis = dayMillis,
                dateStr = sdfDay.format(Date(dayMillis)),
                tirPct = tirPct,
                meanBg = readingsInDay.map { it.value }.average()
            )
        }.sortedBy { it.dateMillis }

        val todayTir = if (dailyTirList.isNotEmpty()) dailyTirList.last().tirPct / 100.0 else null

        // Episode Analysis (>= 15 min deviations: 3 consecutive readings spaced <= 10 min)
        var hypoEventsCount = 0
        var severeHypoEventsCount = 0
        var hyperEventsCount = 0
        var severeHyperEventsCount = 0
        var totalHypoMinutes = 0
        var totalHyperMinutes = 0
        var maxHypoDurationMin = 0
        var maxHyperDurationMin = 0
        val hypoOnsetHours = mutableListOf<Int>()
        val hyperOnsetHours = mutableListOf<Int>()

        var currentEpisodeType = 0
        var episodeStart = 0L
        var episodeSevere = false
        var episodePoints = 0

        for (i in validReadings.indices) {
            val r = validReadings[i]
            val prev = if (i > 0) validReadings[i - 1] else null
            val timeDiffMin = if (prev != null) (r.timestamp - prev.timestamp) / 60000 else 5

            if (r.value < 70.0) {
                if (currentEpisodeType != -1 || timeDiffMin > 15) {
                    if (currentEpisodeType == 1 && episodePoints >= 3) {
                        hyperEventsCount++
                        val dur = ((prev?.timestamp ?: r.timestamp) - episodeStart) / 60000
                        val durMin = dur.toInt().coerceAtLeast(15)
                        totalHyperMinutes += durMin
                        maxHyperDurationMin = max(maxHyperDurationMin, durMin)
                        if (episodeSevere) severeHyperEventsCount++
                    }
                    currentEpisodeType = -1
                    episodeStart = r.timestamp
                    episodeSevere = r.value < 54.0
                    episodePoints = 1
                    cal.timeInMillis = r.timestamp
                    hypoOnsetHours.add(cal.get(Calendar.HOUR_OF_DAY))
                } else {
                    episodePoints++
                    if (r.value < 54.0) episodeSevere = true
                }
            } else if (r.value > 180.0) {
                if (currentEpisodeType != 1 || timeDiffMin > 15) {
                    if (currentEpisodeType == -1 && episodePoints >= 3) {
                        hypoEventsCount++
                        val dur = ((prev?.timestamp ?: r.timestamp) - episodeStart) / 60000
                        val durMin = dur.toInt().coerceAtLeast(15)
                        totalHypoMinutes += durMin
                        maxHypoDurationMin = max(maxHypoDurationMin, durMin)
                        if (episodeSevere) severeHypoEventsCount++
                    }
                    currentEpisodeType = 1
                    episodeStart = r.timestamp
                    episodeSevere = r.value > 250.0
                    episodePoints = 1
                    cal.timeInMillis = r.timestamp
                    hyperOnsetHours.add(cal.get(Calendar.HOUR_OF_DAY))
                } else {
                    episodePoints++
                    if (r.value > 250.0) episodeSevere = true
                }
            } else {
                if (currentEpisodeType == -1 && episodePoints >= 3) {
                    hypoEventsCount++
                    val dur = (r.timestamp - episodeStart) / 60000
                    val durMin = dur.toInt().coerceAtLeast(15)
                    totalHypoMinutes += durMin
                    maxHypoDurationMin = max(maxHypoDurationMin, durMin)
                    if (episodeSevere) severeHypoEventsCount++
                } else if (currentEpisodeType == 1 && episodePoints >= 3) {
                    hyperEventsCount++
                    val dur = (r.timestamp - episodeStart) / 60000
                    val durMin = dur.toInt().coerceAtLeast(15)
                    totalHyperMinutes += durMin
                    maxHyperDurationMin = max(maxHyperDurationMin, durMin)
                    if (episodeSevere) severeHyperEventsCount++
                }
                currentEpisodeType = 0
                episodePoints = 0
            }
        }

        fun findPeakInterval(hours: List<Int>): String {
            if (hours.isEmpty()) return "--"
            val hourCounts = (0..23).map { h -> hours.count { it in h..(h + 2) % 24 } }
            val maxH = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0
            return String.format(Locale.US, "%02d:00 - %02d:00", maxH, (maxH + 3) % 24)
        }

        val hypoPeakIntervalStr = findPeakInterval(hypoOnsetHours)
        val hyperPeakIntervalStr = findPeakInterval(hyperOnsetHours)
        val avgHypoDur = if (hypoEventsCount > 0) totalHypoMinutes / hypoEventsCount else 0
        val avgHyperDur = if (hyperEventsCount > 0) totalHyperMinutes / hyperEventsCount else 0

        return AdvisorMetrics(
            periodLabel = "Last $days days",
            tirVeryLow = tirVeryLow,
            tirLow = tirLow,
            tirInRange = tirInRange,
            tirHigh = tirHigh,
            tirVeryHigh = tirVeryHigh,
            tir70_180 = tirInRange,
            tir70_140 = values.count { it in 70.0..140.0 } / totalCount,
            timeBelow70 = tirLow + tirVeryLow,
            timeBelow54 = tirVeryLow,
            timeAbove180 = tirHigh + tirVeryHigh,
            timeAbove250 = tirVeryHigh,
            meanBg = meanBg,
            gmi = 3.31 + (0.02392 * meanBg),
            tdd = tdd,
            basalPercent = basalPercent,
            medianBg = medianBg,
            p10Bg = p10Bg,
            p25Bg = p25Bg,
            p75Bg = p75Bg,
            p90Bg = p90Bg,
            iqrBg = iqrBg,
            sdBg = sdBg,
            cvPct = cvPct,
            gvi = gvi,
            psg = psg,
            gri = gri,
            griZone = griZone,
            griHypoComponent = cHypo,
            griHyperComponent = cHyper,
            hourlyAgp = hourlyAgp,
            dailyTirList = dailyTirList,
            hypoEvents = hypoEventsCount,
            severeHypoEvents = severeHypoEventsCount,
            hyperEvents = hyperEventsCount,
            severeHyperEvents = severeHyperEventsCount,
            avgHypoDurationMin = avgHypoDur,
            maxHypoDurationMin = maxHypoDurationMin,
            totalHypoMinutes = totalHypoMinutes,
            avgHyperDurationMin = avgHyperDur,
            maxHyperDurationMin = maxHyperDurationMin,
            totalHyperMinutes = totalHyperMinutes,
            hypoPeakIntervalStr = hypoPeakIntervalStr,
            hyperPeakIntervalStr = hyperPeakIntervalStr,
            unitsStr = unitsStr,
            isMmol = isMmol,
            todayTir = todayTir,
            todayTdd = todayTdd,
            therapyStats = therapyStats
        )
    }

    private fun collectTherapyStats(days: Int): TherapyStats? {
        if (persistenceLayer == null) return null
        val now = System.currentTimeMillis()
        val fromTime = now - (days * 24 * 3600 * 1000L)
        val cal = Calendar.getInstance()

        try {
            return runBlocking {
                val boluses = persistenceLayer.getBolusesFromTimeToTime(fromTime, now, false)
                val carbs = persistenceLayer.getCarbsFromTimeToTimeExpanded(fromTime, now, false)
                val tbrs = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(fromTime, now, false)
                val therapyEvents = persistenceLayer.getTherapyEventDataFromToTime(fromTime, now)

                val bFastPeaks = mutableListOf<Double>()
                val lunchPeaks = mutableListOf<Double>()
                val dinnerPeaks = mutableListOf<Double>()
                val prebolusList = mutableListOf<Int>()

                val meals = carbs.map { carb ->
                    val startTime = carb.timestamp
                    val endTime2h = startTime + (2 * 3600 * 1000L)
                    val endTime3h = startTime + (3 * 3600 * 1000L)
                    val endTime4h = startTime + (4 * 3600 * 1000L)

                    val relevantBoluses = boluses.filter { it.timestamp in (startTime - 45 * 60000L)..endTime2h }
                    val insulinIn2h = relevantBoluses.sumOf { it.amount }

                    val preBolus = boluses
                        .filter { it.timestamp in (startTime - 45 * 60000L)..(startTime + 5 * 60000L) }
                        .minByOrNull { abs(startTime - it.timestamp) }

                    val prebolusMin = if (preBolus != null && preBolus.timestamp <= startTime) {
                        ((startTime - preBolus.timestamp) / 60000).toInt()
                    } else if (preBolus != null) {
                        -((preBolus.timestamp - startTime) / 60000).toInt()
                    } else 0
                    prebolusList.add(prebolusMin)

                    val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(startTime - 15 * 60000L, endTime4h, true)
                    val bgStart = bgReadings.firstOrNull()?.value ?: 100.0
                    val bgMax2h = bgReadings.filter { it.timestamp in startTime..endTime2h }.maxByOrNull { it.value }?.value ?: bgStart
                    val bgEnd3h = bgReadings.filter { it.timestamp in (startTime + 2 * 3600000L)..endTime3h }.lastOrNull()?.value ?: bgMax2h
                    val bgEnd4h = bgReadings.lastOrNull()?.value ?: bgEnd3h
                    val deltaPeak = (bgMax2h - bgStart).coerceAtLeast(0.0)

                    cal.timeInMillis = startTime
                    when (cal.get(Calendar.HOUR_OF_DAY)) {
                        in 5..10 -> bFastPeaks.add(bgMax2h)
                        in 11..15 -> lunchPeaks.add(bgMax2h)
                        in 16..22 -> dinnerPeaks.add(bgMax2h)
                    }

                    val success = deltaPeak <= 90.0 && bgMax2h <= 200.0 && bgEnd4h in 70.0..160.0 && bgReadings.none { it.value < 70.0 }

                    MealOutcome(
                        timestamp = startTime,
                        carbs = carb.amount,
                        insulin2h = insulinIn2h,
                        bgStart = bgStart,
                        bgMax2h = bgMax2h,
                        bgEnd4h = bgEnd4h,
                        success = success,
                        prebolusMinutes = prebolusMin,
                        deltaPeak = deltaPeak,
                        bgEnd3h = bgEnd3h,
                        note = carb.notes
                    )
                }

                val successfulMeals = meals.count { it.success }
                val successRate = (successfulMeals.toDouble() / meals.size.coerceAtLeast(1)) * 100.0
                val avgPrebolus = if (prebolusList.isNotEmpty()) prebolusList.average() else 0.0

                val teSummary = if (therapyEvents.isNotEmpty()) {
                    val grouped = therapyEvents.groupBy { it.type.name }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")
                    "Events (${therapyEvents.size}): $grouped"
                } else null

                TherapyStats(
                    meals = meals,
                    totalCarbs = carbs.sumOf { it.amount },
                    totalInsulin = boluses.sumOf { it.amount },
                    totalSmb = boluses.filter { it.type == app.aaps.core.data.model.BS.Type.SMB }.sumOf { it.amount },
                    totalBasal = tbrs.sumOf { it.rate * (it.duration / 3600000.0) },
                    avgPrebolusMin = avgPrebolus,
                    mealSuccessRate = successRate,
                    bFastAvgPeak = if (bFastPeaks.isNotEmpty()) bFastPeaks.average() else null,
                    lunchAvgPeak = if (lunchPeaks.isNotEmpty()) lunchPeaks.average() else null,
                    dinnerAvgPeak = if (dinnerPeaks.isNotEmpty()) dinnerPeaks.average() else null,
                    therapyEventsSummary = teSummary
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun computeGlobalScore(m: AdvisorMetrics): Double {
        val tirScore = m.tir70_180 * 10.0
        val hypoScore = (1.0 - (m.timeBelow70 * 5).coerceAtMost(1.0)) * 10.0
        val hyperScore = (1.0 - m.timeAbove180) * 10.0
        
        return (tirScore * 0.5) + (hypoScore * 0.3) + (hyperScore * 0.2)
    }

    private fun classifySeverity(score: Double): AdvisorSeverity {
        return when {
            score >= 7.0 -> AdvisorSeverity.Good
            score >= 4.0 -> AdvisorSeverity.Warning
            else -> AdvisorSeverity.Critical
        }
    }

    private fun getAssessmentLabel(score: Double): String {
        return when {
            score >= 8.5 -> rh?.gs(R.string.aimi_advisor_score_label_excellent) ?: "Excellent"
            score >= 7.0 -> rh?.gs(R.string.aimi_advisor_score_label_good) ?: "Good"
            score >= 4.0 -> rh?.gs(R.string.aimi_advisor_score_label_warning) ?: "Warning"
            score >= 2.0 -> rh?.gs(R.string.aimi_advisor_score_label_attention) ?: "Attention"
            else -> rh?.gs(R.string.aimi_advisor_score_label_critical) ?: "Critical"
        }
    }

    private fun getEmptyContext(): AdvisorContext {
        return AdvisorContext(
            metrics = AdvisorMetrics("N/A"),
            profile = AimiProfileSnapshot(0.0,0.0,0.0,0.0, 5.0, 12.0, "Unknown"),
            prefs = AimiPrefsSnapshot(0.0,0.0,0.0,0.0, emptyMap()),
            pkpdPrefs = PkpdPrefsSnapshot(false,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
        )
    }

    private fun formatSummary(m: AdvisorMetrics): String {
        return "TIR: ${(m.tir70_180*100).toInt()}% | Hypos: ${(m.timeBelow70*100).toInt()}% | Mean: ${m.meanBg.toInt()}"
    }

    /**
     * Generate recommendations based on Context using the Plugin System.
     */
    fun generateRecommendations(ctx: AdvisorContext, history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog>): List<AimiRecommendation> {
        if (preferences == null) return emptyList()

        // 🛡️ REFINEMENT: Use Real Profile for recommendation logic
        val realProfile = profileFunction?.let { runBlocking { it.getProfile() } }
        val oapsProfile = if (realProfile != null) {
             app.aaps.core.interfaces.aps.OapsProfileAimi(
                dia = realProfile.iCfg.dia,
                min_5m_carbimpact = 8.0, 
                max_iob = preferences.get(DoubleKey.ApsSmbMaxIob),
                max_daily_basal = realProfile.getMaxDailyBasal(),
                max_basal = preferences.get(DoubleKey.ApsMaxBasal),
                min_bg = realProfile.getTargetLowMgdl(),
                max_bg = realProfile.getTargetHighMgdl(),
                target_bg = realProfile.getTargetMgdl(),
                carb_ratio = realProfile.getIc(),
                sens = realProfile.getIsfMgdl("Advisor"),
                autosens_adjust_targets = true,
                max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
                current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
                lgsThreshold = preferences.get(UnitDoubleKey.ApsLgsThreshold).toInt(),
                high_temptarget_raises_sensitivity = true,
                low_temptarget_lowers_sensitivity = true,
                sensitivity_raises_target = true,
                resistance_lowers_target = true,
                adv_target_adjustments = true,
                exercise_mode = false,
                half_basal_exercise_target = 160,
                maxCOB = 120,
                skip_neutral_temps = true,
                remainingCarbsCap = 90,
                enableUAM = true,
                A52_risk_enable = true,
                SMBInterval = 3,
                enableSMB_with_COB = true,
                enableSMB_with_temptarget = true,
                allowSMB_with_high_temptarget = true,
                enableSMB_always = true,
                enableSMB_after_carbs = true,
                maxSMBBasalMinutes = 120,
                maxUAMSMBBasalMinutes = 120,
                bolus_increment = 0.05,
                carbsReqThreshold = 1,
                current_basal = realProfile.getBasal(),
                temptargetSet = false,
                autosens_max = 1.2,
                out_units = "mg/dL",
                variable_sens = 1.0,
                insulinDivisor = 30,
                TDD = ctx.metrics.tdd,
                peakTime = 75.0,
                futureActivity = 0.0,
                sensorLagActivity = 0.0,
                historicActivity = 0.0,
                currentActivity = 0.0
            )
        } else createDummyProfile()

        val loopCtx = app.aaps.plugins.aps.openAPSAIMI.model.AimiPluginContext(
            glucose = app.aaps.core.interfaces.aps.GlucoseStatusAIMI(glucose = ctx.metrics.meanBg),
            profile = oapsProfile,
            iob = emptyList(),
            cob = 0.0,
            preferences = preferences
        )

        // Collect actions from all plugins
        val actions = pluginManager.collectActions(loopCtx)

        // Map actions to recommendations for the UI
        val recs = actions.map { action ->
            AimiRecommendation(
                titleResId = app.aaps.plugins.aps.R.string.aimi_advisor_recommendations_title,
                descriptionResId = 0, 
                priority = action.priority,
                domain = action.domain,
                action = action
            )
        }.toMutableList()

        if (recs.isEmpty()) {
            recs.add(AimiRecommendation(
                titleResId = app.aaps.plugins.aps.R.string.aimi_adv_rec_profile_ok_title,
                descriptionResId = app.aaps.plugins.aps.R.string.aimi_adv_rec_profile_ok_desc,
                priority = app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority.Low,
                domain = app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain.Profile,
                action = null
            ))
        }

        return recs
    }

    /**
     * Apply only Profile-related recommendations (Basal, ISF, IC, Target).
     * Creates a new profile with the current date/time.
     */
    fun applyProfileRecommendations(recs: List<AimiRecommendation>) {
        if (persistenceLayer == null || profileFunction == null || preferences == null) return
        
        val actions = recs.mapNotNull { it.action }
        val profileActions = actions.filter { it.domain in listOf(AimiDomain.Profile, AimiDomain.Basal, AimiDomain.Isf, AimiDomain.Target) }
        
        if (profileActions.isNotEmpty()) {
            createNewAdvisorProfile(profileActions)
        }
    }

    /**
     * Apply only Settings-related recommendations (Preference Updates).
     */
    fun applySettingRecommendations(recs: List<AimiRecommendation>) {
        if (preferences == null) return
        
        val actions = recs.mapNotNull { it.action }
        val prefActions = actions.filterIsInstance<AimiAction.PreferenceUpdate>()
        
        prefActions.forEach { update ->
            try {
                val key = update.key
                val value = update.newValue
                
                when (key) {
                    is BooleanKey -> preferences.put(key, value as Boolean)
                    is DoubleKey -> preferences.put(key, value as Double)
                    is IntKey -> preferences.put(key, value as Int)
                    is StringKey -> preferences.put(key, value as String)
                    is UnitDoubleKey -> preferences.put(key, value as Double)
                }
                
                aapsLogger?.info(app.aaps.core.interfaces.logging.LTag.APS, "Applied Preference Update: ${update.key.key} -> ${update.newValue}")
            } catch (e: Exception) {
                aapsLogger?.error(app.aaps.core.interfaces.logging.LTag.APS, "Failed update: ${update.key.key}", e)
            }
        }
        
        // Preferences are updated directly; PreferencesImpl emits changes via StateFlows
    }

    /**
     * unified application of ALL recommendations in a report.
     * @deprecated Use split apply methods for better UX
     */
    fun applyRecommendations(recs: List<AimiRecommendation>) {
        applyProfileRecommendations(recs)
        applySettingRecommendations(recs)
    }

    private fun createNewAdvisorProfile(actions: List<AimiAction>) {
        val currentProfile = profileFunction?.let { runBlocking { it.getProfile() } } ?: return
        val effectiveDateUtil = dateUtil ?: return
        val date = effectiveDateUtil.now()
        
        val name = "Advisor " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(date))
        
        // Mock non-preference keys to avoid dependency cycle
        val amountKey = object : IntNonPreferenceKey {
            override val key: String = "LocalProfile_profiles"
            override val defaultValue: Int = 0
            override val exportable: Boolean = true
        }
        val amount = preferences?.get(amountKey) ?: 0
        val index = amount
        
        val profileJson = currentProfile.toPureNsJson(effectiveDateUtil)

        // Use anonymous interface implementations to avoid dependency on :plugins:main
        preferences?.run {
            put(amountKey, amount + 1)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_name_"; override val format = "%d"; override val defaultValue = ""; override val exportable = true }, index, value = name)
            put(object : DoubleComposedNonPreferenceKey { override val key = "LocalProfile_dia_"; override val format = "%d"; override val defaultValue = 0.0; override val exportable = true }, index, value = currentProfile.iCfg.dia)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_basal_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (profileJson["basal"] ?: "[]").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_isf_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (profileJson["sens"] ?: "[]").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_ic_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (profileJson["carbratio"] ?: "[]").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targetlow_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (profileJson["target_low"] ?: "[]").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targethigh_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (profileJson["target_high"] ?: "[]").toString())
            put(object : BooleanComposedNonPreferenceKey { override val key = "LocalProfile_mgdl_"; override val format = "%d"; override val defaultValue = true; override val exportable = true }, index, value = currentProfile.units == GlucoseUnit.MGDL)
        }
        
        runBlocking {
            profileFunction.createProfileSwitch(
                durationInMinutes = 0,
                percentage = 100,
                timeShiftInHours = 0,
                action = Action.PROFILE_SWITCH,
                source = Sources.LocalProfile,
                note = "Applied by AIMI Advisor",
                listValues = emptyList()
            )
        }
        
        rxBus?.send(EventProfileChangeRequested())
    }

    private fun createDummyProfile(): app.aaps.core.interfaces.aps.OapsProfileAimi {
        return app.aaps.core.interfaces.aps.OapsProfileAimi(
            dia = 5.0, min_5m_carbimpact = 8.0, max_iob = 0.0, max_daily_basal = 0.0, max_basal = 0.0,
            min_bg = 70.0, max_bg = 180.0, target_bg = 100.0, carb_ratio = 10.0, sens = 50.0,
            autosens_adjust_targets = true, max_daily_safety_multiplier = 3.0, current_basal_safety_multiplier = 4.0,
            high_temptarget_raises_sensitivity = true, low_temptarget_lowers_sensitivity = true,
            sensitivity_raises_target = true, resistance_lowers_target = true, adv_target_adjustments = true,
            exercise_mode = false, half_basal_exercise_target = 160, maxCOB = 120, skip_neutral_temps = true,
            remainingCarbsCap = 90, enableUAM = true, A52_risk_enable = true, SMBInterval = 3,
            enableSMB_with_COB = true, enableSMB_with_temptarget = true, allowSMB_with_high_temptarget = true,
            enableSMB_always = true, enableSMB_after_carbs = true, maxSMBBasalMinutes = 120,
            maxUAMSMBBasalMinutes = 120, bolus_increment = 0.05, carbsReqThreshold = 1,
            current_basal = 1.0, temptargetSet = false, autosens_max = 1.2, out_units = "mg/dL",
            lgsThreshold = 70, variable_sens = 1.0, insulinDivisor = 30, TDD = 40.0, peakTime = 75.0,
            futureActivity = 0.0, sensorLagActivity = 0.0, historicActivity = 0.0, currentActivity = 0.0
        )
    }

    suspend fun generatePlainTextAnalysis(context: AdvisorContext, report: AdvisorReport): String {
        val sb = StringBuilder()
        
        // 1. Try AI Analysis (Strategic Coaching)
        val aiAnalysis = auditorAIService?.let { service ->
            val prompt = app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.TherapyPromptBuilder.buildAnalysisPrompt(context, report)
            val provider = service.getProvider()
            service.getChatCompletion(prompt, provider, useHighPerf = false)
        }
        
        if (!aiAnalysis.isNullOrBlank()) {
            sb.append(aiAnalysis).append("\n\n---\n")
        }

        // 2. Local Fallback/Append (Tactical Observatons)
        if (rh != null) {
            if (report.overallScore >= 8.5) {
                sb.append(rh.gs(R.string.aimi_adv_analysis_all_good) + "\n\n")
            } else if (report.overallScore >= 5.5) {
                sb.append("Stable result, but minor adjustments possible.\n\n")
            } else {
                sb.append("Critical attention required to improve control.\n\n")
            }
            if (report.recommendations.isNotEmpty()) {
                report.recommendations.take(3).forEach { rec ->
                    val title = try { rh.gs(rec.titleResId) } catch (e: Exception) { "-" }
                    sb.append("- $title\n")
                }
            }
            sb.append("\n" + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(report.generatedAt)))
        } else {
             sb.append("Analysis available in app.")
        }
        return sb.toString()
    }

    fun generateFullProposal(periodDays: Int = 7): FullAdvisorProposal {
        val metrics = calculateMetrics(periodDays)
        val profile = profileFunction?.let { runBlocking { it.getProfile() } }
        val isMmol = metrics.isMmol
        val currentIc = profile?.getIc() ?: 10.0
        val isfMgdl = profile?.getProfileIsfMgdl() ?: 40.0
        val currentIsf = if (isMmol) isfMgdl / 18.01559 else isfMgdl

        val hourlyProposals = (0..23).map { hour ->
            val curRate = profile?.getBasal((hour * 3600).toLong()) ?: 1.0
            val agpStat = metrics.hourlyAgp.getOrNull(hour)
            val median = agpStat?.median ?: metrics.meanBg

            val factor = when {
                agpStat != null && agpStat.p10 < 70.0 -> 0.90
                median < 85.0 -> 0.92
                median > 180.0 -> 1.08
                median > 150.0 -> 1.04
                else -> 1.00
            }
            val proposed = (curRate * factor).coerceIn(curRate * 0.85, curRate * 1.15)
            val reason = when {
                factor < 1.0 -> "Hypo risk in hour $hour (P10 < 70)"
                factor > 1.0 -> "Elevated median (${if (isMmol) "%.1f".format(median / 18.01559) else "%.0f".format(median)} ${metrics.unitsStr})"
                else -> "Median in target range"
            }
            HourlyBasalProposal(hour, curRate, proposed, median, reason)
        }

        val mealSuccess = metrics.therapyStats?.mealSuccessRate ?: 70.0
        val avgPeak = metrics.therapyStats?.meals?.map { it.bgMax2h }?.average() ?: metrics.meanBg
        val proposedIc = if (mealSuccess < 50.0 && avgPeak > 180.0) {
            (currentIc * 0.92).coerceAtLeast(currentIc * 0.85)
        } else if (metrics.timeBelow70 > 0.05) {
            (currentIc * 1.08).coerceAtMost(currentIc * 1.15)
        } else {
            currentIc
        }

        val proposedIsf = if (metrics.meanBg > 165.0 && metrics.cvPct < 32.0 && metrics.timeBelow70 < 0.02) {
            (currentIsf * 0.92).coerceAtLeast(currentIsf * 0.85)
        } else if (metrics.timeBelow70 > 0.04) {
            (currentIsf * 1.08).coerceAtMost(currentIsf * 1.15)
        } else {
            currentIsf
        }

        val aimiSettings = mutableListOf<AimiSettingProposal>()
        if (preferences != null) {
            val curLunch = preferences.get(DoubleKey.OApsAIMILunchFactor)
            val lunchPeak = metrics.therapyStats?.lunchAvgPeak ?: 160.0
            if (lunchPeak > 180.0) {
                aimiSettings.add(
                    AimiSettingProposal(
                        prefKey = DoubleKey.OApsAIMILunchFactor.key,
                        title = "Lunch Factor",
                        currentValue = "%.2f".format(curLunch),
                        proposedValue = "%.2f".format((curLunch * 1.1).coerceAtMost(1.8)),
                        reason = "Lunch postprandial peak is elevated (${if (isMmol) "%.1f".format(lunchPeak / 18.01559) else "%.0f".format(lunchPeak)} ${metrics.unitsStr})"
                    )
                )
            }

            val curMaxSmb = preferences.get(DoubleKey.OApsAIMIMaxSMB)
            if (metrics.timeBelow54 > 0.01) {
                aimiSettings.add(
                    AimiSettingProposal(
                        prefKey = DoubleKey.OApsAIMIMaxSMB.key,
                        title = "Max SMB",
                        currentValue = "%.2f U".format(curMaxSmb),
                        proposedValue = "%.2f U".format((curMaxSmb * 0.85).coerceAtLeast(0.5)),
                        reason = "Severe hypo pressure detected (TIR <54 = %.1f%%)".format(metrics.timeBelow54 * 100)
                    )
                )
            }

            val pkpdEnabled = preferences.get(BooleanKey.OApsAIMIPkpdEnabled)
            if (!pkpdEnabled && metrics.cvPct > 36.0) {
                aimiSettings.add(
                    AimiSettingProposal(
                        prefKey = BooleanKey.OApsAIMIPkpdEnabled.key,
                        title = "PK/PD Dynamic Model",
                        currentValue = "Disabled",
                        proposedValue = "Enabled",
                        reason = "High glycemic variability (CV = %.1f%% > 36%%)".format(metrics.cvPct)
                    )
                )
            }
        }

        val clinicalSummary = "AGP Median: ${if (isMmol) "%.1f".format(metrics.medianBg / 18.01559) else "%.0f".format(metrics.medianBg)} ${metrics.unitsStr}, " +
            "CV: %.1f%%, GRI: %.0f (%s), Success Rate: %.0f%%".format(metrics.cvPct, metrics.gri, metrics.griZone, mealSuccess)

        return FullAdvisorProposal(
            generatedAt = System.currentTimeMillis(),
            periodDays = periodDays,
            hourlyBasal = hourlyProposals,
            currentIc = currentIc,
            proposedIc = proposedIc,
            currentIsf = currentIsf,
            proposedIsf = proposedIsf,
            aimiSettings = aimiSettings,
            clinicalSummary = clinicalSummary,
            isMmol = isMmol
        )
    }

    fun createAndActivateAimiProfile(
        proposal: FullAdvisorProposal,
        applyBasal: Boolean,
        applyIc: Boolean,
        applyIsf: Boolean,
        checkedSettings: Map<String, Boolean>
    ): String {
        val currentProfile = profileFunction?.let { runBlocking { it.getProfile() } }
            ?: throw IllegalStateException("Profile is not available")
        val effectiveDateUtil = dateUtil ?: throw IllegalStateException("DateUtil is not available")
        val date = effectiveDateUtil.now()
        val name = "AIMI " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(date))

        val baseJson = currentProfile.toPureNsJson(effectiveDateUtil)

        val basalArray = if (applyBasal) {
            JSONArray().apply {
                proposal.hourlyBasal.forEach { row ->
                    put(JSONObject().apply {
                        put("time", String.format(Locale.US, "%02d:00", row.hour))
                        put("value", kotlin.math.round(row.proposedRate * 100.0) / 100.0)
                        put("timeAsSeconds", row.hour * 3600)
                    })
                }
            }
        } else {
            baseJson["basal"] as? JSONArray ?: JSONArray()
        }

        val icArray = if (applyIc && proposal.proposedIc > 0.0) {
            scaleProfileScheduleArray(baseJson["carbratio"] as? JSONArray, proposal.currentIc, proposal.proposedIc)
        } else {
            baseJson["carbratio"] as? JSONArray ?: JSONArray()
        }

        val isfArray = if (applyIsf && proposal.proposedIsf > 0.0) {
            scaleProfileScheduleArray(baseJson["sens"] as? JSONArray, proposal.currentIsf, proposal.proposedIsf)
        } else {
            baseJson["sens"] as? JSONArray ?: JSONArray()
        }

        val amountKey = object : IntNonPreferenceKey {
            override val key: String = "LocalProfile_profiles"
            override val defaultValue: Int = 0
            override val exportable: Boolean = true
        }
        val amount = preferences?.get(amountKey) ?: 0
        val index = amount

        preferences?.run {
            put(amountKey, amount + 1)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_name_"; override val format = "%d"; override val defaultValue = ""; override val exportable = true }, index, value = name)
            put(object : DoubleComposedNonPreferenceKey { override val key = "LocalProfile_dia_"; override val format = "%d"; override val defaultValue = 0.0; override val exportable = true }, index, value = currentProfile.iCfg.dia)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_basal_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = basalArray.toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_isf_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = isfArray.toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_ic_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = icArray.toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targetlow_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (baseJson["target_low"] ?: "[]").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targethigh_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = (baseJson["target_high"] ?: "[]").toString())
            put(object : BooleanComposedNonPreferenceKey { override val key = "LocalProfile_mgdl_"; override val format = "%d"; override val defaultValue = true; override val exportable = true }, index, value = currentProfile.units == GlucoseUnit.MGDL)
        }

        proposal.aimiSettings.forEach { setting ->
            if (checkedSettings[setting.prefKey] == true && preferences != null) {
                when (setting.prefKey) {
                    DoubleKey.OApsAIMILunchFactor.key -> preferences.put(DoubleKey.OApsAIMILunchFactor, setting.proposedValue.toDoubleOrNull() ?: 1.0)
                    DoubleKey.OApsAIMIMaxSMB.key -> {
                        val num = setting.proposedValue.replace("U", "").trim().toDoubleOrNull()
                        if (num != null) preferences.put(DoubleKey.OApsAIMIMaxSMB, num)
                    }
                    BooleanKey.OApsAIMIPkpdEnabled.key -> preferences.put(BooleanKey.OApsAIMIPkpdEnabled, setting.proposedValue.equals("Enabled", ignoreCase = true))
                }
            }
        }

        runBlocking {
            profileFunction.createProfileSwitch(
                durationInMinutes = 0,
                percentage = 100,
                timeShiftInHours = 0,
                action = Action.PROFILE_SWITCH,
                source = Sources.LocalProfile,
                note = "AIMI Clinical Advisor Profile: $name",
                listValues = emptyList()
            )
        }
        rxBus?.send(EventProfileChangeRequested())

        return name
    }

    fun generateBasalProfileProposal(periodDays: Int = 7): BasalProfileProposal {
        val metrics = calculateMetrics(periodDays)
        val profile = profileFunction?.let { runBlocking { it.getProfile() } }
        if (profile == null) {
            return BasalProfileProposal(System.currentTimeMillis(), periodDays, "NO_PROFILE", 1.0, "Profile unavailable", emptyList())
        }
        val (factor, strategy, rationale) = computeBasalProposalFactor(metrics)
        val rows = (0 until 24).map { hour ->
            val current = profile.getBasal((hour * 3600).toLong())
            val proposed = (current * factor).coerceIn(current * 0.85, current * 1.15)
            BasalHourProposal(hour, current, proposed)
        }
        return BasalProfileProposal(System.currentTimeMillis(), periodDays, strategy, factor, rationale, rows)
    }

    fun exportBasalProfileProposalText(proposal: BasalProfileProposal): String {
        val header = buildString {
            appendLine("AIMI BASAL PROPOSAL (NOT APPLIED)")
            appendLine("generatedAt=${proposal.generatedAt}")
            appendLine("periodDays=${proposal.periodDays}")
            appendLine("strategy=${proposal.strategy}")
            appendLine("scalingFactor=${"%.3f".format(Locale.US, proposal.scalingFactor)}")
            appendLine("rationale=${proposal.rationale}")
            appendLine("hour,current,proposed,deltaPct")
        }
        val lines = proposal.rows.joinToString(separator = "\n") { row ->
            val deltaPct = if (row.current > 0.0) ((row.proposed / row.current) - 1.0) * 100.0 else 0.0
            String.format(Locale.US, "%02d,%.3f,%.3f,%+.1f", row.hour, row.current, row.proposed, deltaPct)
        }
        return header + lines + "\n"
    }

    private fun computeBasalProposalFactor(metrics: AdvisorMetrics): Triple<Double, String, String> {
        return when {
            metrics.timeBelow54 >= 0.01 || metrics.timeBelow70 >= 0.06 -> Triple(0.95, "SAFETY_REDUCTION", "Hypo pressure detected")
            metrics.timeAbove180 >= 0.35 && metrics.tir70_180 < 0.60 && metrics.timeBelow70 <= 0.03 -> Triple(1.06, "GENTLE_INCREASE", "Persistent hyperglycemia")
            else -> Triple(1.00, "HOLD_BASELINE", "No robust pattern")
        }
    }
    
    private fun shouldShowRecommendation(rec: AimiRecommendation, history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog>): Boolean {
        if (rec.action == null) return true 
        if (rec.domain is app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain.Pkpd) {
            val brittleKey = object : BooleanNonPreferenceKey {
                override val key: String = "oa_aimi_t3c_brittle_mode"
                override val defaultValue: Boolean = false
                override val exportable: Boolean = true
            }
            val isT3cActive = preferences?.get(brittleKey) ?: false
            if (isT3cActive) return false
        }
        if (rec.action is app.aaps.plugins.aps.openAPSAIMI.model.AimiAction.PreferenceUpdate) {
            val update = rec.action as app.aaps.plugins.aps.openAPSAIMI.model.AimiAction.PreferenceUpdate
            return !wasRecentlyChanged(history, update.key.key)
        }
        return true
    }

    private fun wasRecentlyChanged(history: List<app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.AdvisorActionLog>, keyString: String): Boolean {
        val threshold = System.currentTimeMillis() - (48 * 3600 * 1000L)
        return history.any { it.timestamp > threshold && it.key == keyString }
    }

    fun generateClinicalExport(periodDays: Int = 14): String {
        try {
            if (persistenceLayer == null || tddCalculator == null || tirCalculator == null) return "{ \"error\": \"Dependencies missing\" }"
            val now = System.currentTimeMillis()
            val fromTime = now - (periodDays * 24 * 3600 * 1000L)
            val bgReadings = try { runBlocking { persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, now, false) }.map { it.value }.filter { it > 30.0 } } catch (e: Exception) { emptyList<Double>() }
            val metrics = calculateMetrics(periodDays)
            val stats = JSONObject()
            val mean = if(bgReadings.isNotEmpty()) bgReadings.average() else 0.0
            val stdDev = if(bgReadings.isNotEmpty()) kotlin.math.sqrt(bgReadings.map { (it-mean)*(it-mean) }.sum() / bgReadings.size) else 0.0
            val cv = if(mean > 0) stdDev/mean * 100 else 0.0
            var lbgiSum = 0.0
            bgReadings.forEach { bg -> if(bg > 10) { val f = 1.509 * (java.lang.Math.pow(java.lang.Math.log(bg), 1.084) - 5.381); if(f < 0) lbgiSum += 10 * f * f } }
            val lbgi = if(bgReadings.isNotEmpty()) lbgiSum/bgReadings.size else 0.0
            stats.put("meta", JSONObject().put("generated", now))
            stats.put("metabolic", JSONObject().apply { put("gmi", 3.31 + 0.02392 * mean); put("cv", cv); put("lbgi", lbgi); put("tir", metrics.tir70_180) })
            stats.put("advisor_metrics", JSONObject().apply { put("hypos", metrics.timeBelow70); put("hypers", metrics.timeAbove180); put("basalRatio", metrics.basalPercent) })
            return stats.toString(2)
        } catch (e: Exception) { return "{ \"error\": \"${e.message}\" }" }
    }
}
