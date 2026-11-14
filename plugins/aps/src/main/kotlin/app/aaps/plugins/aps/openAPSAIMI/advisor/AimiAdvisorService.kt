package app.aaps.plugins.aps.openAPSAIMI.advisor

import kotlin.math.roundToInt
import app.aaps.plugins.aps.R
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
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
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
        val profile = profileFunction.getProfile()
        val profileSnapshot = if (profile != null) {
            val totalBasalCalc = (0 until 24).sumOf { h -> profile.getBasal((h * 3600).toLong()) }
            
            AimiProfileSnapshot(
                nightBasal = profile.getBasal(0L), // 00:00 basal
                icRatio = calculateWeightedAverage(profile.getIcsValues()),
                isf = calculateWeightedAverage(profile.getIsfsMgdlValues()),
                targetBg = calculateWeightedAverage(profile.getSingleTargetsMgdl()),
                dia = profile.dia,
                totalBasal = totalBasalCalc,
                originalName = profileFunction.getOriginalProfileName()
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

    private fun calculateMetrics(days: Int): AdvisorMetrics {
        // Fallback defaults
        var tir70_180 = 0.65
        var tir70_140 = 0.40
        var timeBelow70 = 0.05
        var timeBelow54 = 0.01
        var timeAbove180 = 0.30
        var timeAbove250 = 0.05
        var meanBg = 160.0
        var tdd = 45.0
        var basalPercent = 0.50
        var todayTir: Double? = null
        var todayTdd: Double? = null

        // 1. Calculate Real TIR (70-180)
        if (tirCalculator != null) {
            try {
                val tirs = tirCalculator.calculate(days.toLong(), 70.0, 180.0)
                val avgTir = tirCalculator.averageTIR(tirs)
                
                if (avgTir != null) {
                    tir70_180 = (avgTir.inRangePct() ?: 0.0) / 100.0
                    timeBelow70 = (avgTir.belowPct() ?: 0.0) / 100.0
                    timeAbove180 = (avgTir.abovePct() ?: 0.0) / 100.0
                }

                val tirs54 = tirCalculator.calculate(days.toLong(), 54.0, 180.0)
                val avg54 = tirCalculator.averageTIR(tirs54)
                if (avg54 != null) {
                    timeBelow54 = (avg54.belowPct() ?: 0.0) / 100.0
                }
                
                val tirs250 = tirCalculator.calculate(days.toLong(), 70.0, 250.0)
                val avg250 = tirCalculator.averageTIR(tirs250)
                if (avg250 != null) {
                    timeAbove250 = (avg250.abovePct() ?: 0.0) / 100.0
                }

                val tirs140 = tirCalculator.calculate(days.toLong(), 70.0, 140.0)
                val avg140 = tirCalculator.averageTIR(tirs140)
                if (avg140 != null) {
                    tir70_140 = (avg140.inRangePct() ?: 0.0) / 100.0
                }
                
                val dailyTirs = tirCalculator.calculateDaily(70.0, 180.0)
                if (dailyTirs != null && dailyTirs.size() > 0) {
                     var maxDate = 0L
                     var todayStat: app.aaps.core.interfaces.stats.TIR? = null
                     for(i in 0 until dailyTirs.size()) {
                         val key = dailyTirs.keyAt(i)
                         if (key > maxDate) {
                             maxDate = key
                             todayStat = dailyTirs.valueAt(i)
                         }
                     }
                     if (todayStat != null) {
                        todayTir = (todayStat.inRangePct() ?: 0.0) / 100.0
                     }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Calculate Real TDD
        if (tddCalculator != null) {
            try {
                val tdds = tddCalculator.calculate(days.toLong(), true)
                val avgTdd = tddCalculator.averageTDD(tdds)
                
                if (avgTdd != null) {
                    tdd = avgTdd.data.totalAmount
                    if (tdd > 0) {
                        basalPercent = avgTdd.data.basalAmount / tdd
                    }
                }
                
                val today = tddCalculator.calculateToday()
                if (today != null) {
                    todayTdd = today.totalAmount
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Calculate Mean BG & GMI
        if (persistenceLayer != null) {
            try {
                val now = System.currentTimeMillis()
                val fromTime = now - (days * 24 * 3600 * 1000L)
                val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, now, ascending = false)
                
                if (bgReadings.isEmpty()) {
                    android.util.Log.w("AIMI_ADVISOR", "⚠️ No BG readings found for last $days days.")
                } else {
                    val bgValues = bgReadings
                        .map { it.value }
                        .filter { it > 30.0 } 
                    
                    if (bgValues.isNotEmpty()) {
                        meanBg = bgValues.average()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Therapy Stats for Sub-module
        val therapyStats = collectTherapyStats(days)

        return AdvisorMetrics(
            periodLabel = "Last $days days",
            tir70_180 = tir70_180,
            tir70_140 = tir70_140,
            timeBelow70 = timeBelow70, 
            timeBelow54 = timeBelow54,
            timeAbove180 = timeAbove180,
            timeAbove250 = timeAbove250,
            meanBg = meanBg,
            gmi = 3.31 + (0.02392 * meanBg),
            tdd = tdd,
            basalPercent = basalPercent,
            hypoEvents = 0,
            severeHypoEvents = 0,
            hyperEvents = 0,
            todayTir = todayTir,
            todayTdd = todayTdd,
            therapyStats = therapyStats
        )
    }

    private fun collectTherapyStats(days: Int): TherapyStats? {
        if (persistenceLayer == null) return null
        val now = System.currentTimeMillis()
        val fromTime = now - (days * 24 * 3600 * 1000L)
        
        try {
            val boluses = persistenceLayer.getBolusesFromTimeToTime(fromTime, now, false)
            val carbs = persistenceLayer.getCarbsFromTimeToTimeExpanded(fromTime, now, false)
            val tbrs = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(fromTime, now, false)
            
            val meals = carbs.map { carb ->
                val startTime = carb.timestamp
                val endTime2h = startTime + (2 * 3600 * 1000L)
                val endTime4h = startTime + (4 * 3600 * 1000L)
                
                val relevantBoluses = boluses.filter { it.timestamp in startTime..endTime2h }
                val insulinIn2h = relevantBoluses.sumOf { it.amount }
                
                val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(startTime, endTime4h, true)
                val bgStart = bgReadings.firstOrNull()?.value ?: 0.0
                val bgMax2h = bgReadings.filter { it.timestamp <= endTime2h }.maxByOrNull { it.value }?.value ?: 0.0
                val bgEnd4h = bgReadings.lastOrNull()?.value ?: 0.0
                
                MealOutcome(
                    timestamp = startTime,
                    carbs = carb.amount,
                    insulin2h = insulinIn2h,
                    bgStart = bgStart,
                    bgMax2h = bgMax2h,
                    bgEnd4h = bgEnd4h,
                    success = bgEnd4h in 70.0..140.0 && bgMax2h < 180.0
                )
            }
            
            return TherapyStats(
                meals = meals,
                totalCarbs = carbs.sumOf { it.amount },
                totalInsulin = boluses.sumOf { it.amount },
                totalSmb = boluses.filter { it.type == app.aaps.core.data.model.BS.Type.SMB }.sumOf { it.amount },
                totalBasal = tbrs.sumOf { it.rate * (it.duration / 3600000.0) } 
            )
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
            metrics = AdvisorMetrics("N/A",0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0,0, null, null),
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
        val realProfile = profileFunction?.getProfile()
        val oapsProfile = if (realProfile != null) {
             app.aaps.core.interfaces.aps.OapsProfileAimi(
                dia = realProfile.dia,
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
        
        if (prefActions.isNotEmpty()) {
             prefActions.firstOrNull()?.let { rxBus?.send(app.aaps.core.interfaces.rx.events.EventPreferenceChange(it.key.key)) }
        }
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
        val currentProfile = profileFunction?.getProfile() ?: return
        val date = dateUtil?.now() ?: System.currentTimeMillis()
        
        val name = "Advisor " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(date))
        
        // Mock non-preference keys to avoid dependency cycle
        val amountKey = object : IntNonPreferenceKey {
            override val key: String = "LocalProfile_profiles"
            override val defaultValue: Int = 0
            override val exportable: Boolean = true
        }
        val amount = preferences?.get(amountKey) ?: 0
        val index = amount
        
        val profileJson = currentProfile.toPureNsJson(dateUtil ?: object : app.aaps.core.interfaces.utils.DateUtil {
            override fun fromISODateString(isoDateString: String): Long = 0
            override fun toISOString(date: Long): String = ""
            override fun toISOAsUTC(timestamp: Long): String = ""
            override fun toISONoZone(timestamp: Long): String = ""
            override fun secondsOfTheDayToMillisecondsOfHoursAndMinutes(seconds: Int): Long = 0
            override fun secondsOfTheDayToMilliseconds(seconds: Int): Long = 0
            override fun toSeconds(hhColonMm: String): Int = 0
            override fun dateString(mills: Long): String = ""
            override fun dateStringRelative(mills: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun dateStringShort(mills: Long): String = ""
            override fun timeString(): String = ""
            override fun timeString(mills: Long): String = ""
            override fun secondString(): String = ""
            override fun secondString(mills: Long): String = ""
            override fun minuteString(): String = ""
            override fun minuteString(mills: Long): String = ""
            override fun hourString(): String = ""
            override fun hourString(mills: Long): String = ""
            override fun amPm(): String = ""
            override fun amPm(mills: Long): String = ""
            override fun dayNameString(format: String): String = ""
            override fun dayNameString(mills: Long, format: String): String = ""
            override fun dayString(): String = ""
            override fun dayString(mills: Long): String = ""
            override fun monthString(format: String): String = ""
            override fun monthString(mills: Long, format: String): String = ""
            override fun weekString(): String = ""
            override fun weekString(mills: Long): String = ""
            override fun timeStringWithSeconds(mills: Long): String = ""
            override fun dateAndTimeRangeString(start: Long, end: Long): String = ""
            override fun timeRangeString(start: Long, end: Long): String = ""
            override fun dateAndTimeString(mills: Long): String = ""
            override fun dateAndTimeStringNullable(mills: Long?): String? = null
            override fun dateAndTimeAndSecondsString(mills: Long): String = ""
            override fun minAgo(rh: app.aaps.core.interfaces.resources.ResourceHelper, time: Long?): String = ""
            override fun minOrSecAgo(rh: app.aaps.core.interfaces.resources.ResourceHelper, time: Long?): String = ""
            override fun minAgoShort(time: Long?): String = ""
            override fun minAgoLong(rh: app.aaps.core.interfaces.resources.ResourceHelper, time: Long?): String = ""
            override fun hourAgo(time: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun dayAgo(time: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper, round: Boolean): String = ""
            override fun beginOfDay(mills: Long): Long = 0
            override fun timeStringFromSeconds(seconds: Int): String = ""
            override fun timeFrameString(timeInMillis: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun sinceString(timestamp: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun now(): Long = System.currentTimeMillis()
            override fun nowWithoutMilliseconds(): Long = 0
            override fun isOlderThan(date: Long, minutes: Long): Boolean = false
            override fun getTimeZoneOffsetMs(): Long = 0
            override fun getTimeZoneOffsetMsWithDST(): Long = 0
            override fun getTimeZoneOffsetMinutes(timestamp: Long): Int = 0
            override fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean = false
            override fun isAfterNoon(): Boolean = false
            override fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean = false
            override fun computeDiff(date1: Long, date2: Long): Map<TimeUnit, Long> = emptyMap()
            override fun age(milliseconds: Long, useShortText: Boolean, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun timeAgoFullString(milliseconds: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun niceTimeScalar(time: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun qs(x: Double, numDigits: Int): String = ""
            override fun formatHHMM(timeAsSeconds: Int): String = ""
            override fun untilString(timestamp: Long, rh: app.aaps.core.interfaces.resources.ResourceHelper): String = ""
            override fun timeZoneByOffset(offsetInMilliseconds: Long): String = "UTC"
            override fun timeStampToUtcDateMillis(timestamp: Long): Long = 0
            override fun getTimestampWithCurrentTimeOfDay(timestamp: Long): Long = 0
            override fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long = 0
            override fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean): Long = 0
        })

        // Use anonymous interface implementations to avoid dependency on :plugins:main
        preferences?.run {
            put(amountKey, amount + 1)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_name_"; override val format = "%d"; override val defaultValue = ""; override val exportable = true }, index, value = name)
            put(object : DoubleComposedNonPreferenceKey { override val key = "LocalProfile_dia_"; override val format = "%d"; override val defaultValue = 0.0; override val exportable = true }, index, value = currentProfile.dia)
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_basal_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = profileJson.getJSONArray("basal").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_isf_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = profileJson.getJSONArray("sens").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_ic_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = profileJson.getJSONArray("carbratio").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targetlow_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = profileJson.getJSONArray("target_low").toString())
            put(object : StringComposedNonPreferenceKey { override val key = "LocalProfile_targethigh_"; override val format = "%d"; override val defaultValue = "[]"; override val exportable = true }, index, value = profileJson.getJSONArray("target_high").toString())
            put(object : BooleanComposedNonPreferenceKey { override val key = "LocalProfile_mgdl_"; override val format = "%d"; override val defaultValue = true; override val exportable = true }, index, value = currentProfile.units == GlucoseUnit.MGDL)
        }
        
        profileFunction.createProfileSwitch(
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            action = Action.PROFILE_SWITCH,
            source = Sources.LocalProfile,
            note = "Applied by AIMI Advisor",
            listValues = emptyList()
        )
        
        rxBus?.send(EventProfileStoreChanged())
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
            val provider = AuditorAIService.Provider.GEMINI_38_FLASH // High-speed, efficient for coaching
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

    fun generateBasalProfileProposal(periodDays: Int = 7): BasalProfileProposal {
        val metrics = calculateMetrics(periodDays)
        val profile = profileFunction?.getProfile()
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
            val bgReadings = try { persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, now, false).map { it.value }.filter { it > 30.0 } } catch (e: Exception) { emptyList<Double>() }
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
