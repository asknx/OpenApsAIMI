package app.aaps.plugins.aps.openAPSAIMI

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.util.LongSparseArray
import androidx.core.util.forEach
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.preference.ListPreference
import app.aaps.plugins.aps.openAPSAIMI.steps.UnifiedActivityProviderMTR
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import kotlinx.coroutines.Job
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.aps.openAPSAIMI.keys.AimiLongKey
import app.aaps.plugins.aps.openAPSAIMI.keys.AimiStringKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.data.model.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.aps.keys.ApsIntentKey
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.openAPS.TddStatus
import app.aaps.plugins.aps.openAPSAIMI.ISF.IsfAdjustmentEngine
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import io.reactivex.rxjava3.disposables.Disposable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import kotlin.math.floor
import app.aaps.plugins.aps.openAPSAIMI.ISF.IsfBlender
import app.aaps.plugins.aps.openAPSAIMI.pkpd.IsfFusion
import app.aaps.plugins.aps.openAPSAIMI.pkpd.IsfFusionBounds
import app.aaps.plugins.aps.openAPSAIMI.pkpd.PkPdIntegration
import androidx.core.util.isEmpty
import androidx.core.util.size
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.math.exp
import app.aaps.plugins.aps.openAPSAIMI.utils.AimiBackupManager

@SingleIn(AppScope::class)
open class OpenAPSAIMIPlugin  @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    override val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    config: Config,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val glucoseStatusCalculatorAimi: GlucoseStatusCalculatorAimi,
    private val tddCalculator: TddCalculator,
    private val bgQualityCheck: BgQualityCheck,
    notificationManager: NotificationManager,
    private val ch: ConcentrationHelper,
    private val uiInteraction: UiInteraction,
    private val determineBasalaimiSMB2: DetermineBasalaimiSMB2,
    private val profiler: Profiler,
    private val context: Context,
    private val apsResultProvider: () -> APSResult,
    private val unifiedReactivityLearner: app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner, // 🧠 Brain Injection
    private val stepsManager: app.aaps.plugins.aps.openAPSAIMI.steps.AIMIStepsManagerMTR, // 🏃 Steps Manager MTR
    private val physioManager: app.aaps.plugins.aps.openAPSAIMI.physio.AIMIPhysioManagerMTR, // 🏥 Physiological Manager MTR
    // 🏥 Physiological Decision Adapter (The Safety Gate)
    private val physioAdapter: app.aaps.plugins.aps.openAPSAIMI.physio.AIMIInsulinDecisionAdapterMTR,
    private val auditorOrchestrator: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorOrchestrator, // 🧠 AI Auditor MTR
    private val auditorAIService: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService,
    private val contextManager: app.aaps.plugins.aps.openAPSAIMI.context.ContextManager, // 🎯 Context Manager
    private val aimiBackupManager: AimiBackupManager, // ☁️ Cloud Backup Manager (Force Init)
    private val geminiOAuthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager,
    private val logcatExporter: app.aaps.plugins.aps.openAPSAIMI.utils.LogcatExporter
) : app.aaps.core.interfaces.plugin.PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.APS)
        .composeContent { plugin ->
            app.aaps.plugins.aps.compose.OpenAPSComposeContent(
                apsPlugin = plugin as APS,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil
            )
        }
        .icon(IcPluginOpenAPS)
        .pluginName(TextRef.AndroidRes(R.string.openapsaimi))
        .preferencesVisibleInSimpleMode(false)
        .showInList { config.APS }
        .description(TextRef.AndroidRes(R.string.description_openapsaimi))
        .setDefault(),
    ownPreferences = app.aaps.plugins.aps.openAPSAIMI.keys.AimiLongKey.entries + app.aaps.plugins.aps.openAPSAIMI.keys.AimiStringKey.entries,
    aapsLogger, rh, preferences, notificationManager
), APS, PluginConstraints {

    override suspend fun onStart() {
        super.onStart()

        // 🛡️ Auto-migration of engineering mode for existing users
        if (preferences.getIfExists(BooleanKey.AimiDeveloperOptionsUnlocked) == null) {
            val externalDir = java.io.File(android.os.Environment.getExternalStorageDirectory().absolutePath + "/Documents/AAPS")
            val engFileExists = java.io.File(externalDir, "engineering_mode").exists() ||
                java.io.File(externalDir, "extra/engineering_mode").exists()
            val legacyObjKey1 = object : app.aaps.core.keys.interfaces.BooleanNonPreferenceKey { override val key = "Objectives_2_started"; override val defaultValue = false; override val exportable = true }
            val legacyObjKey2 = object : app.aaps.core.keys.interfaces.BooleanNonPreferenceKey { override val key = "Objectives_3_started"; override val defaultValue = false; override val exportable = true }
            val legacyObjKey3 = object : app.aaps.core.keys.interfaces.LongNonPreferenceKey { override val key = "Objectives_started_2"; override val defaultValue = 0L; override val exportable = true }
            val legacyObjKey4 = object : app.aaps.core.keys.interfaces.LongNonPreferenceKey { override val key = "Objectives_started_3"; override val defaultValue = 0L; override val exportable = true }
            val objStarted = (preferences.getIfExists(legacyObjKey1) == true) ||
                (preferences.getIfExists(legacyObjKey2) == true) ||
                ((preferences.getIfExists(legacyObjKey3) ?: 0L) > 0L) ||
                ((preferences.getIfExists(legacyObjKey4) ?: 0L) > 0L)
            val smbUsed = preferences.get(BooleanKey.ApsUseSmb)

            if (engFileExists || objStarted || smbUsed) {
                preferences.put(BooleanKey.AimiDeveloperOptionsUnlocked, true)
                preferences.put(BooleanKey.AimiEngineeringBypassEnabled, true)
                aapsLogger.info(LTag.APS, "🔓 AIMI Engineering Bypass & Developer Options auto-migrated to enabled")
            }
        }

        // 🏃 Start AIMI Steps Manager (Health Connect + Phone Sensor sync)
        try {
            stepsManager.start()
            aapsLogger.info(LTag.APS, "✅ AIMI Steps Manager started successfully")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "❌ Failed to start AIMI Steps Manager", e)
        }
        
        // 🏥 Start AIMI Physiological Manager
        try {
            physioManager.start()
            aapsLogger.info(LTag.APS, "✅ AIMI Physiological Manager started successfully")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "❌ Failed to start AIMI Physiological Manager", e)
        }

        physioJob?.cancel()
        physioJob = pluginScope.launch {
            preferences.observe(BooleanKey.AimiPhysioAssistantEnable).collect { enabled ->
                try {
                    if (enabled) {
                        physioManager.start()
                    } else {
                        physioManager.stop()
                    }
                } catch (e: Exception) {
                    aapsLogger.error(LTag.APS, "Physio preference toggle handling failed", e)
                }
            }
        }
        
        // 🧠 Start AIMI Neural Trainer
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()
                
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<app.aaps.plugins.aps.openAPSAIMI.autodrive.learning.AutodriveNeuralTrainerWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            ).setConstraints(constraints).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "AIMINeuralTrainer",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            aapsLogger.info(LTag.APS, "✅ AIMI Neural Trainer scheduled successfully")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "❌ Failed to schedule AIMI Neural Trainer", e)
        }
        
        AimiUamHandler.clearCache(context)
        AimiUamHandler.installConfidenceSupplier {
            // retourne null si tu veux "laisser la main" au runtime
            preferences.get(DoubleKey.AimiUamConfidence)
        }
        var count = 0
        val apsResults = persistenceLayer.getApsResults(dateUtil.now() - T.days(1).msecs(), dateUtil.now())
        apsResults.forEach {
            val glucose = it.glucoseStatus?.glucose ?: return@forEach
            val variableSens = it.variableSens ?: return@forEach
            val timestamp = it.date
            val key = timestamp - timestamp % T.mins(30).msecs() + glucose.toLong()
            if (variableSens > 0) dynIsfCache.put(key, variableSens)
            count++
        }
        aapsLogger.debug(LTag.APS, "Loaded $count variable sensitivity values from database")

        // 🧠 Pre-load ML model into memory for O(1) SMB inference on hot path
        try {
            val externalDir = java.io.File(android.os.Environment.getExternalStorageDirectory().absolutePath + "/Documents/AAPS")
            app.aaps.plugins.aps.openAPSAIMI.ml.AimiSmbTrainer.loadModel(externalDir)
            aapsLogger.info(LTag.APS, "✅ AimiSmbTrainer: model load requested (async)")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "❌ AimiSmbTrainer: failed to request model load", e)
        }
    }
    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? =
        glucoseStatusCalculatorAimi.getGlucoseStatusData(allowOldData)
    override suspend fun onStop() {
        super.onStop()

        physioJob?.cancel()
        physioJob = null
        
        // 🏃 Stop AIMI Steps Manager
        try {
            stepsManager.stop()
            aapsLogger.info(LTag.APS, "🛑 AIMI Steps Manager stopped")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Error stopping AIMI Steps Manager", e)
        }
        
        // 🏥 Stop AIMI Physiological Manager
        try {
            physioManager.stop()
            aapsLogger.info(LTag.APS, "🛑 AIMI Physiological Manager stopped")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Error stopping AIMI Physiological Manager", e)
        }

        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("AIMINeuralTrainer")
            aapsLogger.info(LTag.APS, "🛑 AIMI Neural Trainer stopped")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Error stopping AIMI Neural Trainer", e)
        }

        AimiUamHandler.close(context)
    }
    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AIMI
    override var lastAPSResult: APSResult? = null
    override fun usingDynamicIsf(): Boolean = preferences.get(BooleanKey.ApsUseDynamicSensitivity)
    override fun offersDynamicSensitivity(): Boolean = true
    private val pkpdIntegration = PkPdIntegration(preferences)
    private var lastPkpdScale: Double = 1.0
    // Dans votre classe principale (ou plugin), vous pouvez déclarer :
    private val kalmanISFCalculator = KalmanISFCalculator(tddCalculator, preferences, aapsLogger)
    // Fusion lente (TDD/profile) + rate-limit de blend
    private val isfBlender = IsfBlender()
    // top-level (à côté de isfBlender / pkpdIntegration)
    private val isfAdjEngine = IsfAdjustmentEngine()

    /** Réagit au switch Physio sans redémarrer l’app (planifie / annule WorkManager). */
    private var physioJob: Job? = null

    // état EMA persistant (clé Prefs à créer si tu veux le garder entre runs)
    private var tddEma: Double? = null
    private val TDD_EMA_ALPHA = 0.2 // ou pref


    // Recrée les bornes de la fusion ISF depuis les préférences (mêmes clés que PkPdIntegration)
    private fun isfFusion(): IsfFusion {
        val bounds = IsfFusionBounds(
            minFactor = preferences.get(DoubleKey.OApsAIMIIsfFusionMinFactor),
            maxFactor = preferences.get(DoubleKey.OApsAIMIIsfFusionMaxFactor),
            maxChangePer5Min = preferences.get(DoubleKey.OApsAIMIIsfFusionMaxChangePerTick)
        )
        return IsfFusion(bounds)
    }

    @SuppressLint("DefaultLocale")
    override fun getIsfMgdl(profile: Profile, caller: String): Double? {
        val start = dateUtil.now()
        val multiplier = (profile as? ProfileSealed.EPS)?.value?.originalPercentage?.div(100.0)
            ?: return null

        val sensitivity = calculateVariableIsf(start)

        profiler.log(
            LTag.APS,
            "getIsfMgdl() ${sensitivity.first} ${sensitivity.second} ${dateUtil.dateAndTimeAndSecondsString(start)} $caller",
            start
        )

        return sensitivity.second?.let { it * multiplier }
    }

    fun getAverageIsfMgdl(timestamp: Long, caller: String): Double? {
        if (dynIsfCache.isEmpty()) {
            aapsLogger.warn(LTag.APS, "dynIsfCache is empty. Unable to calculate average ISF.")
            return runBlocking { profileFunction.getProfile() }?.getProfileIsfMgdl() ?: 20.0
        }
        var count = 0
        var sum = 0.0
        val start = timestamp - T.hours(24).msecs()
        dynIsfCache.forEach { key, value ->
            if (key in start..timestamp) {
                count++
                sum += value
            }
        }
        val sensitivity = if (count == 0) runBlocking { profileFunction.getProfile() }?.getProfileIsfMgdl() else sum / count
        aapsLogger.debug(LTag.APS, "getAverageIsfMgdl() $sensitivity from $count values ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $caller")
        return sensitivity
    }

    private val dynIsfCache = LongSparseArray<Double>()

    // Exemple de fonction pour prédire le delta futur à partir d'un historique récent
    private fun predictedDelta(deltaHistory: List<Double>): Double {
        if (deltaHistory.isEmpty()) return 0.0
        // Par exemple, on peut utiliser une moyenne pondérée avec des poids croissants pour donner plus d'importance aux valeurs récentes
        val weights = (1..deltaHistory.size).map { it.toDouble() }
        val weightedSum = deltaHistory.zip(weights).sumOf { it.first * it.second }
        return weightedSum / weights.sum()
    }
    private fun estimateKalmanTrustFromDelta(delta: Double?): Double {
        val d = kotlin.math.abs(delta ?: 0.0)
        // 0..10 mg/dL/5min -> 0.1..0.9
        return (d / 10.0).coerceIn(0.1, 0.9)
    }

    // ISF basé TDD (ancre 1800/TDD 24h) avec garde-fous
    private fun tddIsf24hOr(profileIsf: Double): Double {
        val tdd24 = tddCalculator
            .averageTDD(runBlocking { tddCalculator.calculate(1, allowMissingDays = false) })
            ?.data?.totalAmount
            ?: preferences.get(DoubleKey.OApsAIMITDD7) // fallback 7j
        val anchored = if (tdd24 > 0.1) 1800.0 / tdd24 else profileIsf
        return anchored.coerceIn(5.0, 400.0)
    }
    private fun dynamicDeltaCorrectionFactor(delta: Double?, predicted: Double?, bg: Double?): Double {
        if (delta == null || predicted == null || bg == null) return 1.0
        val combinedDelta = (delta + predicted) / 2.0
        return when {
            // En cas d'hypoglycémie (delta négatif), on augmente progressivement l'ISF
            combinedDelta < 0 -> {
                val factor = exp(0.15 * abs(combinedDelta))
                factor.coerceAtMost(1.4)
            }
            // En hyperglycémie : si BG est > 130, on applique une réduction progressive
            bg > 110.0        -> {
                // On réduit d’un certain pourcentage (ici jusqu’à 30%) en fonction de BG
                val bgReduction = 1.0 - ((bg - 110.0) / (200.0 - 110.0)) * 0.5
                // On combine ce facteur avec la réponse exponentielle basée sur combinedDelta si nécessaire
                if (combinedDelta > 10) {
                    // Si le delta est important, on accentue la réduction avec une réponse exponentielle
                    val expFactor = exp(-0.3 * (combinedDelta - 10))
                    minOf(expFactor, bgReduction)
                } else {
                    bgReduction
                }
            }

            else              -> 1.0
        }
    }

    private fun getRecentDeltas(): List<Double> {
        val data = iobCobCalculator.ads.getBucketedDataTableCopy() ?: return emptyList()
        val smb = glucoseStatusCalculatorAimi.getGlucoseStatusData(true) ?: return emptyList()
        val bg = smb.glucose
        val deltaNow = smb.delta
        if (data.isEmpty()) return emptyList()

        val standardWindow = if (bg < 130) 30f else 15f
        val rapidRiseWindow = 10f
        val intervalMinutes = if (deltaNow > 15) rapidRiseWindow else standardWindow

        val nowTs = data.first().timestamp
        val recent = mutableListOf<Double>()
        for (i in 1 until data.size) {
            val r = data[i]
            if (r.value > 39 && !r.filledGap) {
                val minAgo = ((nowTs - r.timestamp) / 60000.0).toFloat()
                if (minAgo in 0.0f..intervalMinutes) {
                    val d = (data.first().recalculated - r.recalculated) / minAgo * 5f
                    recent.add(d)
                }
            }
        }
        return recent
    }
    @Synchronized
    @SuppressLint("DefaultLocale")
    private fun calculateVariableIsf(timestamp: Long): Pair<String, Double?> {
        if (!preferences.get(BooleanKey.ApsUseDynamicSensitivity)) return "OFF" to null

        // 0) cache DB existant
        val result = runBlocking { persistenceLayer.getApsResultCloseTo(timestamp) }
        if (result?.variableSens != null) return "DB" to result.variableSens

        // 1) BG & deltas actuels
        val glucose = glucoseStatusProvider.glucoseStatusData?.glucose ?: return "GLUC" to null
        val currentDelta = glucoseStatusProvider.glucoseStatusData?.delta
        val recentDeltas = getRecentDeltas()
        val predictedDelta = predictedDelta(recentDeltas)

        // 2) facteur historique (comme avant)
        val dynamicFactor = dynamicDeltaCorrectionFactor(currentDelta, predictedDelta, glucose)

        // 3) ISF rapide #1 : Kalman existant
        val kalmanFastIsf = kalmanISFCalculator.calculateISF(glucose, currentDelta, predictedDelta)
        aapsLogger.debug(LTag.APS, "Adaptive ISF via Kalman: $kalmanFastIsf for BG: $glucose")

        // 4) ISF lent (socle) : profil/TDD fusionné + pkpdScale (inchangé)
        val profileIsf = runBlocking { profileFunction.getProfile() }?.getProfileIsfMgdl() ?: 20.0
        val tddIsf = tddIsf24hOr(profileIsf)
        val fusedSlowIsf = isfFusion().fused(profileIsf, tddIsf, lastPkpdScale)
        aapsLogger.debug(LTag.APS, "Fused slow ISF: $fusedSlowIsf (profile=$profileIsf, tddIsf=$tddIsf, pkpdScale=$lastPkpdScale)")

        // 5) EMA TDD (stabilise l’ajustement AF)
        val tdd24 = runBlocking { tddCalculator.calculateDaily(-24, 0)?.totalAmount } ?: tddIsf /* fallback */
        tddEma = when (val prev = tddEma) {
            null -> tdd24
            else -> prev + TDD_EMA_ALPHA * (tdd24 - prev)
        }

        // 6) proxys de confiance (si variance non exposée ici)
        val kalmanTrustProxy = estimateKalmanTrustFromDelta(currentDelta)             // 0..1
        val kalmanVarProxy = (1.0 - kalmanTrustProxy).coerceIn(0.0, 1.0)             // 1-trust
        val sippConfidence = AimiUamHandler.confidenceOrZero().coerceIn(0.0, 1.0)

        // 7) ISF rapide #2 : IsfAdjustmentEngine (AF ln(BG/55) + TDD-EMA + rate-limit)
        val isfAdj = isfAdjEngine.compute(
            bgKalman = glucose,
            tddEma   = (tddEma ?: tdd24),
            profileIsf = profileIsf,
            sippConfidence = sippConfidence,
            kalmanVar = kalmanVarProxy,
            nowMs = System.currentTimeMillis()
        )
        aapsLogger.debug(LTag.APS, "Adaptive ISF via IsfAdjustmentEngine: $isfAdj (tddEma=$tddEma, sipp=$sippConfidence, var=$kalmanVarProxy)")

        // 8) Combine les deux rapides par médiane robuste (résistant aux outliers)
        val fastMedian = listOf(kalmanFastIsf, isfAdj).sorted()[1]

        // 9) Blend final (socle lent vs rapide), avec rate-limit temporel de IsfBlender
        var blended = isfBlender.blend(
            fusedIsf = fusedSlowIsf,
            kalmanIsf = fastMedian,
            trustFast = kalmanTrustProxy,
            nowMs = System.currentTimeMillis()
        )

        // 10) facteur dynamique + bornes globales
        blended *= dynamicFactor
        
        // 🏥 PHYSIO MODULATION (ISF)
        // We fetching multipliers for the specific timestamp is tricky, so we use current context
        // This affects the "Displayed ISF" in AAPS.
        
        // Calculate IOB/COB for Cosine Gate
        val profile = runBlocking { profileFunction.getProfile() }
        if (profile != null) {
            val iobCalc = runBlocking { iobCobCalculator.calculateFromTreatmentsAndTemps(timestamp, profile) }
            val mealData = runBlocking { iobCobCalculator.getMealDataWithWaitingForCalculationFinish() }
            
            val physioMults = physioAdapter.getMultipliers(
                currentBG = glucose, 
                currentDelta = currentDelta ?: 0.0,
                iob = iobCalc.iob,
                cob = mealData.mealCOB
            )
            if (physioMults.isfFactor != 1.0) {
                blended *= physioMults.isfFactor
                aapsLogger.debug(LTag.APS, "🏥 DynISF modulated by Physio: x${physioMults.isfFactor} -> $blended")
            }
        }

        blended = blended.coerceIn(5.0, 300.0)

        aapsLogger.debug(LTag.APS, "Final DynISF: $blended")
        aapsLogger.debug(
            LTag.APS,
            "DynISF inputs: fusedSlowIsf=$fusedSlowIsf, kalmanFastIsf=$kalmanFastIsf, isfAdj=$isfAdj, trustFast=$kalmanTrustProxy, pkpdScale=$lastPkpdScale"
        )

        // 11) cache
        val key = timestamp - timestamp % T.mins(30).msecs() + glucose.toLong()
        if (dynIsfCache.size > 1000) dynIsfCache.clear()
        dynIsfCache.put(key, blended)

        return "CALC" to blended
    }


    override suspend fun invoke(initiator: String, tempBasalFallback: Boolean) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = getGlucoseStatusData(false)
        if (glucoseStatus == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
            return
        }
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump

        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(app.aaps.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(app.aaps.core.ui.R.string.no_profile_set))
            return
        }
        if (!isEnabled()) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return
        }

        val inputConstraints = ConstraintObject(0.0, aapsLogger) // fake. only for collecting all results

        if (!hardLimits.checkHardLimits(profile.iCfg.dia, InterfacesStrings.profile_dia, hardLimits.diaRange())) return
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                InterfacesStrings.profile_carbs_ratio_value,
                hardLimits.icRange()
            )
        ) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSAIMIPlugin"), InterfacesStrings.profile_sensitivity_value, HardLimits.LIMIT_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), CoreUiStrings.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(ch.fromPump(pump.baseBasalRate), CoreUiStrings.current_basal_value, 0.01, hardLimits.maxBasal())) return

        // End of check, start gathering data
        
        // 🏥 PHYSIO INTEGRATION: Retrieve Context & Multipliers
        val glucoseForPhysio = glucoseStatusProvider.glucoseStatusData?.glucose ?: 100.0
        val deltaForPhysio = glucoseStatusProvider.glucoseStatusData?.delta ?: 0.0
        
        // Calculate IOB/COB early for Physio Adapter
        val nowMs = dateUtil.now()
        val iobCalc = iobCobCalculator.calculateFromTreatmentsAndTemps(nowMs, profile) // Uses 'profile' from enabled check above
        val mealDataForPhysio = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        
        val physioMults = physioAdapter.getMultipliers(
            currentBG = glucoseForPhysio, 
            currentDelta = deltaForPhysio,
            iob = iobCalc.iob,
            cob = mealDataForPhysio.mealCOB
        )
        
        if (!physioMults.isNeutral()) {
            aapsLogger.info(LTag.APS, "🏥 LOOP: Applying Physio Factors: ISF x${physioMults.isfFactor}, Basal x${physioMults.basalFactor}, SMB x${physioMults.smbFactor}")
        }

        val dynIsfMode = preferences.get(BooleanKey.ApsUseDynamicSensitivity)
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        val advancedFiltering = constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

        val now = dateUtil.now()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val currentTemp = CurrentTemp(
            duration = tb?.plannedRemainingMinutes ?: 0,
            rate = tb?.convertedToAbsolute(now, profile) ?: 0.0,
            minutesrunning = tb?.getPassedDurationToTimeInMinutes(now)
        )
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), InterfacesStrings.profile_low_target, HardLimits.LIMIT_MIN_BG)
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), InterfacesStrings.profile_high_target, HardLimits.LIMIT_MAX_BG)
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TARGET_BG)
        var isTempTarget = false
        runBlocking { persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) }?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.lowTarget, CoreUiStrings.temp_target_low_target, HardLimits.LIMIT_TEMP_MIN_BG)
            maxBg = hardLimits.verifyHardLimits(tempTarget.highTarget, CoreUiStrings.temp_target_high_target, HardLimits.LIMIT_TEMP_MAX_BG)
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG)
        }
        val peak = profile.iCfg.peak
        val insulinDivisor = when {
            peak > 65 -> 55 // rapid peak: 75
            peak > 50 -> 65 // ultra rapid peak: 55
            else      -> 45 // lyumjev peak: 45
        }

        var autosensResult = AutosensResult()
        val tddStatus: TddStatus?
        val variableSensitivity = 0.0
        val tdd = 0.0
        if (true) { // FIX: Always run, DetermineBasalAIMI2 handles dynIsfMode internally
            val tdd7P: Double = preferences.get(DoubleKey.OApsAIMITDD7)
//
// // Plancher pour éviter des TDD trop faibles au démarrage
            val minTDD = 10.0
//
// Récupération et ajustement du TDD sur 7 jours
            val tdd7D = tddCalculator.averageTDD(runBlocking { tddCalculator.calculate(7, allowMissingDays = false) })
            if (tdd7D != null && tdd7D.data.totalAmount > tdd7P && tdd7D.data.totalAmount > 1.3 * tdd7P) {
                tdd7D.data.totalAmount = 1.2 * tdd7P
                aapsLogger.info(LTag.APS, "TDD for 7 days limited to 10% increase. New TDD7D: ${tdd7D.data.totalAmount}")
            }
            if (tdd7D != null && tdd7D.data.totalAmount < tdd7P * 0.9) {
                tdd7D.data.totalAmount = tdd7P * 0.9
                aapsLogger.info(LTag.APS, "TDD for 7 days was too low. Adjusted to 90% of TDD7P: ${tdd7D.data.totalAmount}")
            }

            // Calcul du TDD sur 2 jours
            var tdd2Days = tddCalculator.averageTDD(runBlocking { tddCalculator.calculate(2, allowMissingDays = false) })?.data?.totalAmount ?: 0.0
            if (tdd2Days == 0.0 || tdd2Days < tdd7P) tdd2Days = tdd7P
//
            val tdd2DaysPerHour = tdd2Days / 24
            val tddLast4H = tdd2DaysPerHour * 4
//
// Calcul du TDD sur 1 jour avec une limite minimale pour éviter des instabilités
            var tddDaily = tddCalculator.averageTDD(runBlocking { tddCalculator.calculate(1, allowMissingDays = false) })?.data?.totalAmount ?: 0.0
            if (tddDaily == 0.0 || tddDaily < tdd7P / 2) tddDaily = maxOf(tdd7P, minTDD)

            if (tddDaily > tdd7P && tddDaily > 1.1 * tdd7P) {
                tddDaily = 1.1 * tdd7P
                aapsLogger.info(LTag.APS, "TDD for 1 day limited to 10% increase. New TDDDaily: $tddDaily")
            }
// // Calcul du TDD sur 24 heures
            var tdd24Hrs = runBlocking { tddCalculator.calculateDaily(-24, 0)?.totalAmount } ?: 0.0
            if (tdd24Hrs == 0.0) tdd24Hrs = tdd7P
            val tdd24HrsPerHour = tdd24Hrs / 24
            val tddLast8to4H = tdd24HrsPerHour * 4
// // Calcul pondéré du TDD récent pour éviter les fluctuations extrêmes
            val tddWeightedFromLast8H = ((1.2 * tdd2DaysPerHour) + (0.3 * tddLast4H) + (0.5 * tddLast8to4H)) * 3
            val tdd = (tddWeightedFromLast8H * 0.20) + (tdd2Days * 0.50) + (tddDaily * 0.30)

            // On récupère la glycémie et le delta actuel
            val gsData = glucoseStatusProvider.glucoseStatusData
            val currentBG = gsData?.glucose
            if (currentBG == null) {
                aapsLogger.error(LTag.APS, "Données de glycémie indisponibles, impossibilité de calculer l'ISF adaptatif.")
                return
            }
            val currentDelta = gsData?.delta
            val recentDeltas = getRecentDeltas()
            val predictedDelta = predictedDelta(recentDeltas)

            // Calcul adaptatif de l'ISF via la fonction centralisée encapsulant le tout (incluant l'alimentation du cache)
            val (source, calcSensitivity) = calculateVariableIsf(now)
            var variableSensitivity = calcSensitivity ?: profile.getProfileIsfMgdl()
            
            aapsLogger.debug(LTag.APS, "Adaptive ISF computed (source: $source): $variableSensitivity for BG: $currentBG, currentDelta: $currentDelta, predictedDelta: $predictedDelta")

            // 🏥 Apply Physio ISF Modulation to Dynamic ISF (it might already be in calculateVariableIsf, but applying it if not fully wrapped)
            // (calculateVariableIsf does apply physioMults internally before returning blended, 
            // but if we fell back to profile ISF, we apply it here for safety)
            if (source == "OFF" || calcSensitivity == null) {
                if (physioMults.isfFactor != 1.0) {
                    variableSensitivity *= physioMults.isfFactor
                    aapsLogger.debug(LTag.APS, "🏥 LOOP: DynISF modulated: $variableSensitivity (x${physioMults.isfFactor})")
                }
                // Imposition des bornes
                variableSensitivity = variableSensitivity.coerceIn(5.0, 300.0)
            }
            
            aapsLogger.debug(LTag.APS, "Final adaptive ISF after clamping: $variableSensitivity")

// 🔹 Création du résultat final (Convention: Ratio < 1 = Résistant)
            autosensResult = AutosensResult(
                ratio = tdd2Days / tdd24Hrs,
                ratioFromTdd = tdd2Days / tdd24Hrs,
                ratioFromCarbs = 1.0 
            )

            // 🧠 AIMI BRAIN INTEGRATION (UnifiedReactivityLearner)
            // "The Cognitive Bridge": Adjusts BOTH Sensitivity (ISF) and Resistance (Autosens Ratio)
            try {
                // 🚀 TRIPLE-SIGNAL CONFIRMATION for Confirmed Rise
                val gsAimi = gsData as? GlucoseStatusAIMI
                val accel = gsAimi?.bgAcceleration ?: 0.0
                val combDelta = ((gsData?.delta ?: 0.0) + predictedDelta) / 2.0
                val isConfirmedHighRise = (gsData?.glucose ?: 0.0) > 150.0 && combDelta > 1.5 && accel > 0.4

                unifiedReactivityLearner.processIfNeeded(isConfirmedHighRise)
                var brainFactor = unifiedReactivityLearner.getCombinedFactor()
                
                // 🚨 SAFETY OVERRIDE (FCL 10.3) - Refined for "Blind Spot" Removal:
                // If we are in Hyper (>150) AND Rising/Stable, we MUST NOT be protective (<1.0).
                // ANTI-LAG: Lower threshold to 110 if rising fast (delta > 3.0)
                val isFastRise = (gsData?.delta ?: 0.0) > 3.0
                val overrideThreshold = if (isFastRise) 110.0 else 150.0
                val isHyper = (gsData?.glucose ?: 0.0) > overrideThreshold
                val isRising = (gsData?.delta ?: 0.0) > -0.5
                
                if (isHyper && isRising && brainFactor < 1.0) {
                    aapsLogger.debug(LTag.APS, "🧠 Brain Override: IGNORING protective factor ${"%.2f".format(brainFactor)} because BG ${gsData?.glucose ?: 0.0} is > $overrideThreshold & stable/rising.")
                    brainFactor = 1.0
                }

                // 🚀 EXPLOSIVE RISE BOOST: If delta is very high, force a slight aggressive factor
                if (glucoseStatus.delta > 6.0 && brainFactor < 1.1) {
                    aapsLogger.debug(LTag.APS, "🚀 Brain Boost: Forcing factor 1.1 due to explosive rise (delta=${glucoseStatus.delta})")
                    brainFactor = 1.1
                }

                if (brainFactor != 1.0) {
                    val originalRatio = autosensResult.ratio
                    val originalISF = variableSensitivity
                    
                    // 1. Modulate Autosens Ratio (Basal/Targets/ISF)
                    // High Brain Factor (Aggressive) -> Ratio decreases (e.g. 0.8 / 1.5 = 0.53) -> More Resistant
                    autosensResult.ratio = originalRatio / brainFactor
                    
                    // 🚨 IMPORTANT: variableSensitivity (Dynamic ISF) is NO LONGER modulated here.
                    // It will be modulated by autosensResult.ratio in DetermineBasalAIMI2.kt 
                    // to ensure a single, consistent point of application for the "Resistance" multiplier.
                    
                    aapsLogger.debug(LTag.APS, "🧠 AIMI Brain Override: " +
                        "Autosens ${"%.2f".format(originalRatio)}->${"%.2f".format(autosensResult.ratio)} " +
                        "(Factor ${"%.2f".format(brainFactor)})")
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Failed to apply AIMI Brain factor", e)
            }

            val iobArray = iobCobCalculator.calculateIobArrayForSMB(autosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
            var currentActivity = 0.0
            for (i in -4..0) { //MP: -4 to 0 calculates all the insulin active during the last 5 minutes
                val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(i.toLong()), profile)
                currentActivity += iob.activity
            }
            var futureActivity = 0.0
            
            // 🌀 Cosine Gate: Apply Peak Shift
            val activityPredTimePK = peak + physioMults.peakShiftMinutes
            // Ensure peak time remains reasonable (min 35m)
            val safepk = activityPredTimePK.coerceAtLeast(35)
            
            for (i in -4..0) { //MP: calculate 5-minute-insulin activity centering around peakTime
                val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(safepk.toLong() - i), profile)
                futureActivity += iob.activity
            }
            val sensorLag = -10L //MP Assume that the glucose value measurement reflect the BG value from 'sensorlag' minutes ago & calculate the insulin activity then
            var sensorLagActivity = 0.0
            for (i in -4..0) {
                val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(sensorLag - i), profile)
                sensorLagActivity += iob.activity
            }

            val activityHistoric = -20L //MP Activity at the time in minutes from now. Used to calculate activity in the past to use as target activity.
            var historicActivity = 0.0
            for (i in -2..2) {
                val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(activityHistoric - i), profile)
                historicActivity += iob.activity
            }
// Récupère GS standard + features AIMI
            val pack = glucoseStatusCalculatorAimi.compute(allowOldData = true)
            val gs = pack.gs ?: run {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
                aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
                return
            }
            val f = pack.features

// Construit l’objet attendu par determine_basal
            val glucoseStatusAimi = GlucoseStatusAIMI(
                glucose         = gs.glucose,
                noise           = gs.noise,
                delta           = gs.delta,
                shortAvgDelta   = gs.shortAvgDelta,
                longAvgDelta    = gs.longAvgDelta,
                date            = gs.date,

                // Champs AIMI disponibles
                duraISFminutes  = f?.stable5pctMinutes ?: 0.0,
                deltaPl         = f?.delta5Prev ?: 0.0,
                deltaPn         = f?.delta5Next ?: 0.0,
                bgAcceleration  = f?.accel ?: 0.0,
                corrSqu         = f?.corrR2 ?: 0.0,

                // Champs non exposés par AimiBgFeatures => valeurs neutres
                duraISFaverage  = 0.0,
                parabolaMinutes = 0.0,
                a0              = 0.0,
                a1              = 0.0,
                a2              = 0.0
            )
            futureActivity = Round.roundTo(futureActivity, 0.0001)
            sensorLagActivity = Round.roundTo(sensorLagActivity, 0.0001)
            historicActivity = Round.roundTo(historicActivity, 0.0001)
            currentActivity = Round.roundTo(currentActivity, 0.0001)
            // === PK/PD: calcule un pkpdScale cohérent et le mémorise ===
            // === PK/PD: calcule un pkpdScale cohérent et le mémorise (SANS dyn ISF) ===
            val nowMs = dateUtil.now()

// valeurs BG/delta déjà dispo dans invoke (glucoseStatus)
            val bgNow = glucoseStatus.glucose
            val deltaNow = glucoseStatus.delta

// IOB instantané
            val iobNow = iobCobCalculator.calculateFromTreatmentsAndTemps(nowMs, profile).iob

// Utilise le TDD 24h que tu as déjà calculé/chargé dans invoke (évite les IO coûteuses)
            val tdd24ForPk = tdd24Hrs  // garde ta variable existante ici (Double)

// IMPORTANT : passer un ISF "profil brut" pour éviter toute ré-entrée dans dynISF
            val profileIsfRaw = profile.getProfileIsfMgdl()   // mg/dL/U du profil, SANS dynamique

            val pkpdRuntimeNow = pkpdIntegration.computeRuntime(
                epochMillis = nowMs,
                bg = bgNow,
                deltaMgDlPer5 = deltaNow,
                iobU = iobNow,
                carbsActiveG = 0.0,          // branche tes carbs actifs réels si tu les as ici
                windowMin = 240,             // fenêtre standard (4h) – ajuste si besoin
                exerciseFlag = false,        // remplace par ton flag 'sportTime' si dispo ICI
                profileIsf = profileIsfRaw,  // ← **PROFIL BRUT, PAS getIsfMgdl()**
                tdd24h = tdd24ForPk,
                combinedDelta = deltaNow,    // Fallback simple car combinedDelta non dispo ici
                uamConfidence = AimiUamHandler.confidenceOrZero()
            )

            lastPkpdScale = pkpdRuntimeNow?.pkpdScale ?: 1.0
            aapsLogger.debug(LTag.APS, "PK/PD: pkpdScale=$lastPkpdScale (bg=$bgNow, delta=$deltaNow, iob=$iobNow, tdd24=$tdd24ForPk, isfRaw=$profileIsfRaw)")
            val tdd4D = tddCalculator.averageTDD(runBlocking { tddCalculator.calculate(4, allowMissingDays = false) })
            val oapsProfile = OapsProfileAimi(
                dia = profile.iCfg.dia,
                min_5m_carbimpact = 0.0, // not used
                max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
                max_daily_basal = profile.getMaxDailyBasal(),
                max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
                min_bg = minBg,
                max_bg = maxBg,
                target_bg = targetBg,
                carb_ratio = profile.getIc(),
                sens = profile.getIsfMgdl("OpenAPSAIMIPlugin") * physioMults.isfFactor, // 🏥 ISF Modulation
                autosens_adjust_targets = false, // not used
                max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier) * physioMults.smbFactor, // 🏥 SMB Cap modulation
                current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier) * physioMults.basalFactor, // 🏥 Basal Cap modulation
                lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
                high_temptarget_raises_sensitivity = false,
                low_temptarget_lowers_sensitivity = false,
                sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
                resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
                adv_target_adjustments = SMBDefaults.adv_target_adjustments,
                exercise_mode = SMBDefaults.exercise_mode,
                half_basal_exercise_target = SMBDefaults.half_basal_exercise_target,
                maxCOB = SMBDefaults.maxCOB,
                skip_neutral_temps = pump.setNeutralTempAtFullHour(),
                remainingCarbsCap = SMBDefaults.remainingCarbsCap,
                enableUAM = constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
                A52_risk_enable = SMBDefaults.A52_risk_enable,
                SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
                enableSMB_with_COB = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob),
                enableSMB_with_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt),
                allowSMB_with_high_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt),
                enableSMB_always = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering,
                enableSMB_after_carbs = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAfterCarbs) && advancedFiltering,
                maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
                maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
                bolus_increment = pump.pumpDescription.bolusStep,
                carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
                current_basal = ch.fromPump(activePlugin.activePump.baseBasalRate) * physioMults.basalFactor, // 🏥 Basal Rate Modulation
                temptargetSet = isTempTarget,
                autosens_max = preferences.get(DoubleKey.AutosensMax),
                out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
                variable_sens = variableSensitivity,
                insulinDivisor = insulinDivisor,
                TDD = if (tdd4D == null) preferences.get(DoubleKey.OApsAIMITDD7) else tdd,
                peakTime = activityPredTimePK.toDouble(),
                futureActivity = futureActivity,
                sensorLagActivity = sensorLagActivity,
                historicActivity = historicActivity,
                currentActivity = currentActivity
            )

            val microBolusAllowed = constraintsChecker.isSMBModeEnabled(ConstraintObject(tempBasalFallback.not(), aapsLogger)).also { inputConstraints.copyReasons(it) }.value()
            val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT

            aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal AIMI <<<")
            aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
            aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
            aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.joinToString()}")
            aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
            aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
            aapsLogger.debug(LTag.APS, "Meal data:          $mealData")
            aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
            aapsLogger.debug(LTag.APS, "flatBGsDetected:    $flatBGsDetected")
            aapsLogger.debug(LTag.APS, "DynIsfMode:         $dynIsfMode")

            determineBasalaimiSMB2.determine_basal(
                glucose_status = glucoseStatusAimi,
                currenttemp = currentTemp,
                iob_data_array = iobArray,
                profile = oapsProfile,
                autosens_data = autosensResult,
                mealData = mealData,
                microBolusAllowed = microBolusAllowed,
                currentTime = now,
                flatBGsDetected = flatBGsDetected,
                dynIsfMode = dynIsfMode,
                uiInteraction = uiInteraction,
                extraDebug = physioMults.detailedReason
            ).also {
                val determineBasalResult = apsResultProvider().with(it)
                
                // 🔮 FCL 11.0: Force Copy Predictions via JSON (Manual Construction)
                // 🔮 FCL 11.0: Force Copy Predictions via JSON (Manual Construction)
                if (it.predBGs != null) {
                    // 🔮 FCL 11.1: Force Populate predictionsAsGv for UI (OverviewViewModel)
                    // If 'with(RT)' failed to populate the list, we do it manually here.
                    if (determineBasalResult.predictionsAsGv.isEmpty()) {
                        it.predBGs?.IOB?.forEachIndexed { index, value ->
                             val time = now + index * 300000L // 5 mins
                             val gv = GV(
                                 timestamp = time,
                                 value = value.toDouble(),
                                 raw = value.toDouble(),
                                 trendArrow = TrendArrow.NONE,
                                 noise = 0.0,
                                 sourceSensor = SourceSensor.IOB_PREDICTION
                             )
                             determineBasalResult.predictionsAsGv.add(gv)
                         }
                    }
                }

                // Preserve input data
                determineBasalResult.inputConstraints = inputConstraints
                determineBasalResult.autosensResult = autosensResult
                determineBasalResult.iobData = iobArray
                determineBasalResult.glucoseStatus = glucoseStatus
                determineBasalResult.currentTemp = currentTemp
                determineBasalResult.oapsProfileAimi = oapsProfile
                determineBasalResult.mealData = mealData
                lastAPSResult = determineBasalResult
                lastAPSRun = now
                aapsLogger.debug(LTag.APS, "Result: $it")
                rxBus.send(EventAPSCalculationFinished())
            }

            rxBus.send(EventOpenAPSUpdateGui())
        }
    }

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false)
        return value
    }

    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref = preferences.get(DoubleKey.ApsSmbMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobSMB(), rh.gs(R.string.limiting_iob, hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        }
        return maxIob
    }
    fun detectMealOnset(delta: Float, predictedDelta: Float, acceleration: Float): Boolean {
        val combinedDelta = (delta + predictedDelta) / 2.0f
        return combinedDelta > 3.0f && acceleration > 1.2f
    }

    override fun applyBasalConstraints(
        absoluteRate: Constraint<Double>,
        profile: Profile
    ): Constraint<Double> {
        // ────────────────────────────────────────────────────
        // 1️⃣ On détecte si l’on est en mode “meal” ou “early autodrive”
        val therapy = Therapy(persistenceLayer).also { it.updateStatesBasedOnTherapyEvents() }
        
        // 🎯 Context Integration (Remote/AI)
        val contextSnapshot = contextManager.getSnapshot(dateUtil.now())
        
        val isMealMode = therapy.snackTime
            || therapy.highCarbTime
            || therapy.mealTime
            || therapy.lunchTime
            || therapy.dinnerTime
            || therapy.bfastTime
            || contextSnapshot.hasMealRisk // 🍕 Remote "Lunch/Meal" triggers this

        val isSportMode = therapy.sportTime || contextSnapshot.hasActivity // 🏃 Remote "Sport" triggers this

        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        val night = hour <= 7
        val isAutodriveEnabled = preferences.get(BooleanKey.OApsAIMIautoDrive) || preferences.get(BooleanKey.OApsAIMIautoDriveActive)
        val smb = glucoseStatusCalculatorAimi.getGlucoseStatusData(false) ?: return absoluteRate

        val isEarlyAutodrive = !night && !isMealMode && !isSportMode && isAutodriveEnabled &&
            determineBasalaimiSMB2.isAutodriveEngaged()

        val isSpecialMode = isMealMode || isEarlyAutodrive || preferences.get(BooleanKey.OApsAIMIT3cBrittleMode)

        // ────────────────────────────────────────────────────
        // 2️⃣ On choisit la bonne pref en fonction du mode
        var maxBasal = when {
            isMealMode       -> preferences.get(DoubleKey.meal_modes_MaxBasal)
            isEarlyAutodrive -> preferences.get(DoubleKey.autodriveMaxBasal)
            else             -> preferences.get(DoubleKey.ApsMaxBasal)
        }

        if (isEnabled()) {
            // 3️⃣ On remonte au maxDailyBasal si besoin
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(
                    rh.gs(R.string.increasing_max_basal),
                    this
                )
            }

            // 4️⃣ On bride toujours sur maxBasal
            absoluteRate.setIfSmaller(
                maxBasal,
                rh.gs(
                    app.aaps.core.ui.R.string.limitingbasalratio,
                    maxBasal,
                    rh.gs(R.string.maxvalueinpreferences)
                ),
                this
            )

            // ───> **Si on est dans un mode spécial, on s’arrête là :**
            if (isSpecialMode) {
                return absoluteRate
            }

            // ────────────────────────────────────────────────────
            // 5️⃣ Sinon, on applique en plus le multiplicateur “current basal”
            val maxBasalMultiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(
                    app.aaps.core.ui.R.string.limitingbasalratio,
                    maxFromBasalMultiplier,
                    rh.gs(R.string.max_basal_multiplier)
                ),
                this
            )

            // 6️⃣ Et le multiplicateur “daily basal”
            val maxDailyMultiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxDailyMultiplier * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromDaily,
                rh.gs(
                    app.aaps.core.ui.R.string.limitingbasalratio,
                    maxFromDaily,
                    rh.gs(R.string.max_daily_basal_multiplier)
                ),
                this
            )
        }

        return absoluteRate
    }

    override suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseSmb)
        if (!enabled) value.set(false, rh.gs(R.string.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseUam)
        if (!enabled) value.set(false, rh.gs(R.string.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (preferences.get(BooleanKey.ApsUseDynamicSensitivity)) {
            // DynISF mode
            if (!preferences.get(BooleanKey.ApsDynIsfAdjustSensitivity))
                value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        } else {
            // SMB mode
            val enabled = preferences.get(BooleanKey.ApsUseAutosens)
            if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        }
        return value
    }



    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "openapsaimi_settings",
        title = TextRef.Literal("OpenAPS AIMI"),
        items = listOf(
            PreferenceSubScreenDef(
                key = "Global User Preferences",
                title = TextRef.Literal("Global User Preferences"),
                items = listOf(
                    PreferenceSubScreenDef(
                        key = "AIMI_AI",
                        title = TextRef.Literal("AIMI AI / LLM Configuration"),
                        items = listOf(
                            StringKey.AimiAdvisorProvider,
                            StringKey.AimiAdvisorOpenAIKey,
                            StringKey.AimiAdvisorGeminiKey,
                            StringKey.AimiAdvisorDeepSeekKey,
                            StringKey.AimiAdvisorClaudeKey,
                            StringKey.AimiAdvisorGlmKey,
                            StringKey.AimiAdvisorGrokKey,
                            StringKey.AimiAdvisorOpenRouterKey,
                            StringKey.AimiAdvisorGroqKey,
                            StringKey.AimiAdvisorCustomUrl,
                            StringKey.AimiAdvisorCustomKey,
                            StringKey.AimiAdvisorModel,
                            BooleanKey.OApsAIMIGeminiUseOAuth,
                            StringKey.AimiGeminiOAuthClientId,
                            StringKey.AimiGeminiOAuthClientSecret,
                            StringKey.AimiGeminiOAuthProjectId,
                            BooleanKey.OApsAIMIUseOpenFoodFacts,
                            AimiStringKey.FatSecretClientId,
                            AimiStringKey.FatSecretClientSecret,
                            AimiStringKey.FatSecretRegion,
                            BooleanKey.OApsAIMIMealAdvisorTrigger,
                            BooleanKey.AimiMealAdvisorEnable,
                            BooleanKey.OApsAIMIContextEnabled,
                            BooleanKey.OApsAIMIContextLLMEnabled,
                            StringKey.ContextMode,
                            StringKey.AimiGeminiOAuthLoginButton,
                            StringKey.AimiGeminiOAuthLogoutButton,
                            IntentKey.OApsAIMIProfileAdvisor,
                            IntentKey.OApsAIMIMealAdvisor,
                            IntentKey.OApsAIMIContext
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_EMERGENCY_SOS",
                        title = TextRef.Literal("Emergency SOS (Hypo)"),
                        items = listOf(
                            BooleanKey.AimiEmergencySosEnable,
                            StringKey.AimiEmergencySosPhone,
                            IntKey.AimiEmergencySosThreshold
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_PHYSIO",
                        title = TextRef.Literal("AIMI Physiological Assistant"),
                        items = listOf(
                            BooleanKey.AimiPhysioAssistantEnable,
                            BooleanKey.AimiPhysioSleepDataEnable,
                            BooleanKey.AimiPhysioHRVDataEnable,
                            BooleanKey.AimiPhysioLLMAnalysisEnable,
                            BooleanKey.AimiPhysioDebugLogs
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_PKPD",
                        title = TextRef.Literal("AIMI PKPD Adaptive Models"),
                        items = listOf(
                            BooleanKey.OApsAIMIPkpdEnabled,
                            DoubleKey.OApsAIMIPkpdInitialDiaH,
                            DoubleKey.OApsAIMIPkpdInitialPeakMin,
                            DoubleKey.OApsAIMIPkpdBoundsDiaMinH,
                            DoubleKey.OApsAIMIPkpdBoundsDiaMaxH,
                            DoubleKey.OApsAIMIPkpdBoundsPeakMinMin,
                            DoubleKey.OApsAIMIPkpdBoundsPeakMinMax,
                            DoubleKey.OApsAIMIPkpdMaxDiaChangePerDayH,
                            DoubleKey.OApsAIMIPkpdMaxPeakChangePerDayMin,
                            DoubleKey.OApsAIMIIsfFusionMinFactor,
                            DoubleKey.OApsAIMIIsfFusionMaxFactor,
                            DoubleKey.OApsAIMIIsfFusionMaxChangePerTick,
                            DoubleKey.OApsAIMISmbTailThreshold,
                            DoubleKey.OApsAIMISmbTailDamping,
                            BooleanKey.OApsAIMIPkpdPragmaticReliefEnabled,
                            DoubleKey.OApsAIMIPkpdPragmaticReliefMinFactor,
                            DoubleKey.OApsAIMIRedCarpetRestoreThreshold,
                            DoubleKey.OApsAIMIPriorityMaxIobFactor,
                            DoubleKey.OApsAIMIPriorityMaxIobExtraU,
                            DoubleKey.OApsAIMISmbExerciseDamping,
                            DoubleKey.OApsAIMISmbLateFatDamping,
                            DoubleKey.OApsAIMIPkpdStateDiaH,
                            DoubleKey.OApsAIMIPkpdStatePeakMin,
                            DoubleKey.OApsAIMIPlateauBandAbs,
                            DoubleKey.OApsAIMIR2Confident,
                            DoubleKey.OApsAIMIMaxMultiplier,
                            DoubleKey.OApsAIMIKickerStep,
                            DoubleKey.OApsAIMIKickerMinUph,
                            IntKey.OApsAIMIKickerStartMin,
                            IntKey.OApsAIMIKickerMaxMin,
                            DoubleKey.OApsAIMIZeroResumeFrac,
                            IntKey.OApsAIMIZeroResumeMin,
                            IntKey.OApsAIMIZeroResumeMax,
                            DoubleKey.OApsAIMIAntiStallBias,
                            DoubleKey.OApsAIMIDeltaPosRelease
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_T3C",
                        title = TextRef.Literal("AIMI T3c Adaptive Basal"),
                        items = listOf(
                            BooleanKey.OApsAIMIT3cAdaptiveBasalEnabled,
                            DoubleKey.OApsAIMIAdaptiveBasalMaxScaling,
                            BooleanKey.OApsAIMIT3cBrittleMode,
                            DoubleKey.OApsAIMIT3cActivationThreshold,
                            DoubleKey.OApsAIMIT3cAggressiveness
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_Trajectory",
                        title = TextRef.Literal("Trajectory Guard"),
                        items = listOf(
                            BooleanKey.OApsAIMITrajectoryGuardEnabled
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "Women_Cycle",
                        title = TextRef.Literal("Women Cycle & Hormonal Modulation"),
                        items = listOf(
                            BooleanKey.OApsAIMIwcycle,
                            StringKey.OApsAIMIWCycleTrackingMode,
                            StringKey.OApsAIMIWCycleContraceptive,
                            DoubleKey.OApsAIMIwcycledateday,
                            IntKey.OApsAIMIWCycleAvgLength,
                            BooleanKey.OApsAIMIWCycleShadow,
                            BooleanKey.OApsAIMIWCycleRequireConfirm,
                            DoubleKey.OApsAIMIWCycleClampMin,
                            DoubleKey.OApsAIMIWCycleClampMax,
                            StringKey.OApsAIMIWCycleThyroid,
                            StringKey.OApsAIMIWCycleVerneuil
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "Thyroid_Module",
                        title = TextRef.Literal("Thyroid / Basedow Module"),
                        items = listOf(
                            BooleanKey.OApsAIMIThyroidEnabled,
                            StringKey.OApsAIMIThyroidMode,
                            StringKey.OApsAIMIThyroidManualStatus,
                            StringKey.OApsAIMIThyroidTreatmentPhase,
                            StringKey.OApsAIMIThyroidGuardLevel,
                            BooleanKey.OApsAIMIThyroidLogVerbosity
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_ENDO",
                        title = TextRef.Literal("Endometriosis Management"),
                        items = listOf(
                            BooleanKey.AimiEndometriosisEnable,
                            BooleanKey.AimiEndometriosisHormonalSuppression,
                            BooleanKey.AimiEndometriosisPainFlare,
                            IntKey.AimiEndometriosisFlareDuration,
                            DoubleKey.AimiEndometriosisBasalMult,
                            DoubleKey.AimiEndometriosisSmbDampen
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_AI_Auditor",
                        title = TextRef.Literal("AI Decision Auditor"),
                        items = listOf(
                            BooleanKey.AimiAuditorEnabled,
                            StringKey.AimiAuditorMode,
                            IntKey.AimiAuditorMaxPerHour,
                            IntKey.AimiAuditorTimeoutSeconds,
                            IntKey.AimiAuditorMinConfidence
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "Night_Growth",
                        title = TextRef.Literal("Night Growth Resistance"),
                        items = listOf(
                            BooleanKey.OApsAIMINightGrowthEnabled,
                            IntKey.OApsAIMINightGrowthAgeYears,
                            StringKey.OApsAIMINightGrowthStart,
                            StringKey.OApsAIMINightGrowthEnd,
                            DoubleKey.OApsAIMINightGrowthMaxIobExtra,
                            DoubleKey.OApsAIMINightGrowthMinRiseSlope,
                            DoubleKey.OApsAIMINightGrowthSmbMultiplier,
                            DoubleKey.OApsAIMINightGrowthBasalMultiplier,
                            DoubleKey.OApsAIMINightGrowthMaxSmbClamp,
                            IntKey.OApsAIMINightGrowthMinDurationMin,
                            IntKey.OApsAIMINightGrowthMinEventualOverTarget,
                            IntKey.OApsAIMINightGrowthDecayMinutes
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "AIMI_Pregnancy",
                        title = TextRef.Literal("Gestational Autopilot"),
                        items = listOf(
                            BooleanKey.OApsAIMIpregnancy,
                            AimiLongKey.PregnancyDueDate,
                            AimiStringKey.PregnancyDueDateString
                        )
                    ),
                    BooleanKey.OApsAIMIhoneymoon,
                    BooleanKey.OApsAIMInight,
                    BooleanKey.OApsAIMIUnifiedReactivityEnabled,
                    BooleanKey.OApsxdriponeminute,
                    BooleanKey.OApsAIMIEnableBasal,
                    BooleanKey.OApsAIMIEnableStepsFromWatch,
                    BooleanKey.OApsAIMIforcelimits,
                    BooleanKey.AimiEngineeringBypassEnabled,
                    IntKey.OApsAIMIlogsize,
                    DoubleKey.OApsAIMIFCLFactor,
                    DoubleKey.OApsAIMIHighBg,
                    IntKey.OApsAIMIHighBGinterval,
                    AimiStringKey.RemoteControlPin
                )
            ),
            BooleanKey.OApsAIMIMLtraining,
            DoubleKey.OApsAIMIMaxSMB,
            DoubleKey.OApsAIMIHighBGMaxSMB,
            DoubleKey.OApsAIMIweight,
            DoubleKey.OApsAIMICHO,
            DoubleKey.OApsAIMITDD7,
            PreferenceSubScreenDef(
                key = "Training_ML_Modes",
                title = TextRef.Literal("Training ML Modes"),
                items = listOf(
                    DoubleKey.meal_modes_MaxBasal,
                    PreferenceSubScreenDef(
                        key = "mode_Breakfast",
                        title = TextRef.Literal("Breakfast Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMIBFPrebolus,
                            DoubleKey.OApsAIMIBFPrebolus2,
                            DoubleKey.OApsAIMIBFFactor,
                            IntKey.OApsAIMIBFinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_Lunch",
                        title = TextRef.Literal("Lunch Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMILunchPrebolus,
                            DoubleKey.OApsAIMILunchPrebolus2,
                            DoubleKey.OApsAIMILunchFactor,
                            IntKey.OApsAIMILunchinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_dinner",
                        title = TextRef.Literal("Dinner Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMIDinnerPrebolus,
                            DoubleKey.OApsAIMIDinnerPrebolus2,
                            DoubleKey.OApsAIMIDinnerFactor,
                            IntKey.OApsAIMIDinnerinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_highcarb",
                        title = TextRef.Literal("High Carb Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMIHighCarbPrebolus,
                            DoubleKey.OApsAIMIHighCarbPrebolus2,
                            DoubleKey.OApsAIMIHCFactor,
                            IntKey.OApsAIMIHCinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_snack",
                        title = TextRef.Literal("Snack Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMISnackPrebolus,
                            DoubleKey.OApsAIMISnackFactor,
                            IntKey.OApsAIMISnackinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_meal",
                        title = TextRef.Literal("Generic Meal Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMIMealPrebolus,
                            DoubleKey.OApsAIMIMealFactor,
                            IntKey.OApsAIMImealinterval
                        )
                    ),
                    PreferenceSubScreenDef(
                        key = "mode_sleep",
                        title = TextRef.Literal("Sleep Mode"),
                        items = listOf(
                            DoubleKey.OApsAIMIsleepFactor,
                            IntKey.OApsAIMISleepinterval
                        )
                    )
                )
            ),
            PreferenceSubScreenDef(
                key = "Autodrive",
                title = TextRef.Literal("Autodrive"),
                items = listOf(
                    BooleanKey.OApsAIMIautoDrive,
                    BooleanKey.OApsAIMIautoDriveActive,
                    DoubleKey.autodriveMaxBasal,
                    DoubleKey.OApsAIMIMpcInsulinUPerKgPerStep,
                    DoubleKey.OApsAIMIautodrivesmallPrebolus,
                    DoubleKey.OApsAIMIautodrivePrebolus,
                    BooleanKey.OApsAIMIAutodriveV3EnhancedGater,
                    DoubleKey.OApsAIMIAutodriveAcceleration,
                    IntKey.OApsAIMIAutodriveTarget,
                    BooleanKey.AimiCosineGateEnabled,
                    DoubleKey.AimiCosineGateAlpha,
                    DoubleKey.AimiCosineGateMinDataQuality,
                    DoubleKey.AimiCosineGateMinSensitivity,
                    DoubleKey.AimiCosineGateMaxSensitivity,
                    IntKey.AimiCosineGateMaxPeakShift,
                    PreferenceSubScreenDef(
                        key = "Autodrive prebolus variables",
                        title = TextRef.Literal("Autodrive Prebolus Variables"),
                        items = listOf(
                            IntKey.OApsAIMIAutodriveBG,
                            DoubleKey.OApsAIMIcombinedDelta,
                            DoubleKey.OApsAIMIAutodriveDeviation
                        )
                    )
                )
            ),
            PreferenceSubScreenDef(
                key = "OAPS_SMB_Settings",
                title = TextRef.Literal("OAPS SMB Settings"),
                items = listOf(
                    DoubleKey.ApsMaxBasal,
                    DoubleKey.ApsSmbMaxIob,
                    BooleanKey.ApsUseDynamicSensitivity,
                    BooleanKey.ApsUseAutosens,
                    IntKey.ApsDynIsfAdjustmentFactor,
                    UnitDoubleKey.ApsLgsThreshold,
                    BooleanKey.ApsDynIsfAdjustSensitivity,
                    BooleanKey.ApsSensitivityRaisesTarget,
                    BooleanKey.ApsResistanceLowersTarget,
                    BooleanKey.ApsUseSmb,
                    BooleanKey.ApsUseSmbWithHighTt,
                    BooleanKey.ApsUseSmbAlways,
                    BooleanKey.ApsUseSmbWithCob,
                    BooleanKey.ApsUseSmbWithLowTt,
                    BooleanKey.ApsUseSmbAfterCarbs,
                    IntKey.ApsMaxSmbFrequency,
                    IntKey.ApsMaxMinutesOfBasalToLimitSmb,
                    IntKey.ApsUamMaxMinutesOfBasalToLimitSmb,
                    BooleanKey.ApsUseUam,
                    IntKey.ApsCarbsRequestThreshold,
                    PreferenceSubScreenDef(
                        key = "absorption_smb_advanced",
                        title = CoreUiStrings.advanced_settings_title,
                        items = listOf(
                            ApsIntentKey.LinkToDocs,
                            BooleanKey.ApsAlwaysUseShortDeltas,
                            DoubleKey.ApsMaxDailyMultiplier,
                            DoubleKey.ApsMaxCurrentBasalMultiplier
                        )
                    )
                )
            )
        ),
        icon = pluginDescription.icon
    )

}
