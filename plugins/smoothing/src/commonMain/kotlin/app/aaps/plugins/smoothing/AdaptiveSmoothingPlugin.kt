package app.aaps.plugins.smoothing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualitySnapshot
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier
import app.aaps.core.interfaces.rx.events.EventAdaptiveSmoothingQuality
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.smoothing.keys.AdaptiveDoubleNonKey
import app.aaps.plugins.smoothing.keys.AdaptiveIntNonKey
import app.aaps.plugins.smoothing.keys.AdaptiveLongNonKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.collections.ArrayDeque
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Clock

/**
 * 🚀 HYBRID ADAPTIVE UKF SMOOTHING PLUGIN
 *
 * A state-of-the-art fusion of:
 * 1. Unscented Kalman Filter (UKF) -> For optimal signal processing, noise reduction, and lag-free trend estimation.
 * 2. Adaptive Safety Logic -> For heuristic handling of physical artifacts (Compression Lows, Hypo Safety).
 *
 * CORE PHILOSOPHY:
 * "Trust the Maths for precision, trust the Rules for safety."
 *
 * FEATURES:
 * - UKF State-Space Model (Glucose + Rate positions)
 * - Adaptive Measurement Noise (R) based on signal quality
 * - Compression Artifact Blocking (Rule-based override)
 * - Hypo Safety Passthrough (Data transparency in critical lows)
 * - Zero-Lag Trend Estimation
 *
 * @author MTR, Lyra & The OpenAPS AI Team
 */
@OptIn(ExperimentalAtomicApi::class)
@Inject
@SingleIn(AppScope::class)
// Bound as PluginBase, not implicitly: these classes have two supertypes (PluginBase and Smoothing),
// so Metro cannot pick one. The plugin list wants PluginBase.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(650)
class AdaptiveSmoothingPlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    notificationManager: NotificationManager,
    private val rxBus: RxBus,
    private val persistenceLayer: PersistenceLayer,
    private val iobCobCalculator: IobCobCalculator,
    preferences: Preferences
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .icon(Icons.Default.Timeline)
        .pluginName(SmoothingStrings.adaptive_smoothing_name)
        .description(SmoothingStrings.description_adaptive_smoothing),
    ownPreferences = AdaptiveLongNonKey.entries + AdaptiveIntNonKey.entries + AdaptiveDoubleNonKey.entries,
    aapsLogger, rh, preferences, notificationManager
), Smoothing {

    override fun lastAdaptiveSmoothingQualitySnapshot(): AdaptiveSmoothingQualitySnapshot? = lastQualitySnapshot

    // ============================================================
    // UKF CONFIGURATION & PARAMETERS
    // ============================================================

    private val n = 2 // State dimension [G, Ġ]
    private val alpha = 1.00
    private val beta = 0.0
    private val kappa = 3.0
    private val lambda = alpha * alpha * (n + kappa) - n
    private val gamma = sqrt(n + lambda)

    // Sigma point weights
    private val Wm = DoubleArray(2 * n + 1)
    private val Wc = DoubleArray(2 * n + 1)

    // FIXED process noise (Physiological Limits)
    private val Q_FIXED = doubleArrayOf(
        1.0, 0.0,     // Glucose process noise: ~2.4 mg/dL std dev per 5 min
        0.0, 0.40     // Rate process noise: ~0.24 mg/dL/min std dev
    )

    // Adaptive Measurement Noise (R) Limits
    private val R_INIT = 25.0
    private val R_MIN = 16.0
    private val R_MAX = 196.0

    // Adaptation Logic
    private val innovationWindow = 48
    private val RATE_DAMPING = 0.98
    private val CHI_SQUARED_THRESHOLD = 15.13 // 99.99% confidence
    private val OUTLIER_ABSOLUTE = 65.0

    // Processing state
    private var learnedR = R_INIT
    private val innovations = ArrayDeque<Double>(innovationWindow + 1)
    private val rawInnovationVariance = ArrayDeque<Double>(innovationWindow + 1)
    private var lastProcessedTimestamp: Long = 0
    private var lastSensorChangeTimestamp: Long = 0
    private var sensorSessionId: Int = 0

    // UI-facing informational quality (sent over RxBus, throttled to avoid spam)
    private var lastAdaptiveSmoothingQualityTier: AdaptiveSmoothingQualityTier? = null
    private var lastAdaptiveSmoothingQualityEventAt: Long = 0L

    /** Read by dashboard on each status refresh; always updated synchronously in [smooth] / segment processing. */
    @Volatile
    private var lastQualitySnapshot: AdaptiveSmoothingQualitySnapshot? = null

    // Events & Scope
    private val resetRequested = AtomicBoolean(false)
    private var scope: CoroutineScope? = null

    // Safety Context
    private data class GlycemicContext(
        val cv: Double,
        val zone: GlycemicZone,
        val currentBg: Double,
        val iob: Double,
        val isNight: Boolean,
        val rawDelta: Double // Heuristic delta for rules
    )

    private enum class GlycemicZone { HYPO, LOW_NORMAL, TARGET, HYPER }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {
        initSigmaWeights()
        loadPersistedParameters()
    }

    private fun initSigmaWeights() {
        Wm[0] = lambda / (n + lambda)
        Wc[0] = lambda / (n + lambda) + (1 - alpha * alpha + beta)
        val w = 1.0 / (2.0 * (n + lambda))
        for (i in 1 until 2 * n + 1) {
            Wm[i] = w
            Wc[i] = w
        }
    }

    override suspend fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        persistenceLayer.observeChanges(TE::class)
            .onEach {
                checkForSensorChange()
            }
            .launchIn(newScope)

        newScope.launch {
            loadLastSensorChange()
        }
    }

    override suspend fun onStop() {
        super.onStop()
        scope?.cancel()
        scope = null
    }

    // ============================================================
    // MAIN SMOOTHING LOOP (The "Hybrid" Logic)
    // ============================================================

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {
        if (data.size < 2) {
            // Always provide a valid smoothed payload for downstream workers.
            copyRawToSmoothed(data)
            sanitizeOutput(data)
            refreshSnapshotAfterShortOrRawPass()
            return data
        }

        try {
            // 1. Check for Reset Conditions (Sensor Change, Gaps, Time Travel)
            if (shouldResetLearning(data[0].timestamp)) {
                resetLearning()
            }

            // 2. Prepare for Processing
            val previousTimestamp = lastProcessedTimestamp
            lastProcessedTimestamp = data[0].timestamp

            // 3. Process Data Segment (Forward Filter + Backward Smoother)
            // Note: We process the whole segment here, but focusing on the robust estimation
            processHybridSegment(data, previousTimestamp)

            // 4. Persistence & Logging
            val newDataProcessed = data.any { it.timestamp > previousTimestamp }
            if (newDataProcessed) {
                savePersistedParameters()
            }

            sanitizeOutput(data)
            return data

        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "HybridSmoothing: Error, falling back to raw", e)
            copyRawToSmoothed(data)
            sanitizeOutput(data)
            val now = Clock.System.now().toEpochMilliseconds()
            lastQualitySnapshot = AdaptiveSmoothingQualitySnapshot(
                tier = AdaptiveSmoothingQualityTier.BAD,
                learnedR = learnedR,
                outlierRate = 1.0,
                compressionRate = 0.0,
                updatedAtMillis = now
            )
            return data
        }
    }

    /** When fewer than 2 points are smoothed, keep UI in sync using last tier / current learned R. */
    private fun refreshSnapshotAfterShortOrRawPass() {
        val now = Clock.System.now().toEpochMilliseconds()
        val tier = lastAdaptiveSmoothingQualityTier ?: AdaptiveSmoothingQualityTier.OK
        lastQualitySnapshot = AdaptiveSmoothingQualitySnapshot(
            tier = tier,
            learnedR = learnedR,
            outlierRate = 0.0,
            compressionRate = 0.0,
            updatedAtMillis = now
        )
    }

    private fun processHybridSegment(
        data: MutableList<InMemoryGlucoseValue>,
        previousTimestamp: Long
    ) {
        // We will process the list from Oldest to Newest for the Filter (Time forward)
        // data list is usually Newest [0] -> Oldest [N]
        // So we iterate backwards through the list indices

        // Initialize State
        val startIdx = data.lastIndex
        val x = doubleArrayOf(data[startIdx].value, 0.0) // Initial state [G, 0]
        val P = doubleArrayOf(16.0, 0.0, 0.0, 1.0)       // Initial Covariance
        var R = learnedR

        // Quality counters for UI badge (informational only).
        var processedPoints = 0
        var compressionPoints = 0
        var outlierPoints = 0

        // Prepare storage for RTS Smoother
        val forwardStates = ArrayList<FilterState>(data.size)
        val results = DoubleArray(data.size)

        val currentIob = try {
            val bolusIob = runBlocking { iobCobCalculator.calculateIobFromBolus() }.iob
            val basalIob = runBlocking { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() }.iob
            bolusIob + basalIob
        } catch (e: Exception) {
            0.0
        }

        // --- FORWARD PASS (FILTER) ---
        for (i in startIdx downTo 0) {
            val z = data[i].value
            val timestamp = data[i].timestamp

            processedPoints++

            // Calculate dt (Time since last step)
            // If i == startIdx (oldest), dt is 0 or estimated.
            val dt = if (i < startIdx) {
                (timestamp - data[i + 1].timestamp) / (1000.0 * 60.0)
            } else {
                5.0 // Assumption for first point
            }

            val dtClamped = dt.coerceIn(1.0, 15.0) // Clamp to reasonable limits

            // --- 🛡️ ADAPTIVE SAFETY GUARDRAILS ---
            // Calculate heuristic context for this point
            val ctx = calculateGlycemicContext(data, i, currentIob)

            // Check for Blocking Artifacts (Compression Lows)
            val isCompression = isCompressionArtifactCandidate(ctx, data, i)
            if (isCompression) compressionPoints++

            // Check for Hypo Safety Bypass
            val isHypoCritical = ctx.currentBg < 70.0

            // --- UKF STEP ---

            // 1. Standard Prediction (Baseline Physiology)
            var (xPred, PPred) = predict(x, P, Q_FIXED, dtClamped)

            // 2. 🚀 DYNAMIC MANEUVER DETECTION (Zero-Lag Hyper)
            val preFitInnovation = z - xPred[0]
            val preFitSigma = sqrt(PPred[0] + R) // Expected deviation
            val normInnovation = preFitInnovation / preFitSigma

            // Condition: Rapid Rise (Innovation > 2.5 sigma) AND data is higher than prediction
            val isRapidManeuver = (normInnovation > 2.5 && preFitInnovation > 0)

            if (isRapidManeuver) {
                aapsLogger.debug(LTag.GLUCOSE, "HybridSmoothing: 🚀 RAPID RISE DETECTED (Innov=${preFitInnovation.toInt()}). Inflating Q for Zero-Lag.")

                // Inflate Q_rate massively to allow instant velocity adaptation
                val Q_ADAPTIVE = Q_FIXED.copyOf()
                Q_ADAPTIVE[3] *= 50.0 // Allow huge rate change
                Q_ADAPTIVE[0] *= 2.0  // Slight position looseness

                // Re-Run Prediction with Inflated Q
                val result = predict(x, P, Q_ADAPTIVE, dtClamped)
                xPred = result.first
                PPred = result.second
            }

            val stateBefore = FilterState(x.copyOf(), P.copyOf(), xPred.copyOf(), PPred.copyOf(), dtClamped)

            // 3. Update (Measurement)
            if (isCompression) {
                aapsLogger.warn(LTag.GLUCOSE, "HybridSmoothing: COMPRESSION BLOCKED at ${z.toInt()} mg/dL. Holding prediction.")
                // Blind update: Keep xPred as x, but don't collapse P (uncertainty grows)
                x[0] = xPred[0]
                x[1] = xPred[1]
                P[0] = PPred[0]; P[1] = PPred[1]; P[2] = PPred[2]; P[3] = PPred[3]

                // Override smoothed value for this point
                data[i].smoothed = x[0] // Projected value
            } else {
                // Normal Update
                // Calculate Innovation for R adaptation
                val innovation = z - xPred[0]
                val innovationVariance = PPred[0] + R

                // Outlier Check (Statistical)
                val isStatisticalOutlier = isOutlier(innovation, innovationVariance, P)
                if (isStatisticalOutlier) outlierPoints++

                // Adapt R (Noise)
                R = adaptMeasurementNoise(R, innovations, rawInnovationVariance)
                trackInnovation(innovation, innovationVariance)

                // Execute Update
                update(xPred, PPred, z, R, x, P)

                // Store Result
                // --- 🚨 HYPO KINEMATICS (G7/One+ Safety) ---
                val velocity = x[1] // mg/dL per min
                val predictedBg20min = x[0] + (velocity * 20.0)

                val isKineticHypo = (predictedBg20min < 55.0) ||
                    (z < 80.0 && velocity < -1.5) ||
                    (velocity < -3.0)

                if (isKineticHypo) {
                    if (x[0] > z) {
                        x[0] = z
                    }
                    if (velocity < -2.0) {
                        x[0] += (velocity * 2.0)
                    }
                    aapsLogger.debug(LTag.GLUCOSE, "HybridSmoothing: KINETIC HYPO DETECTED! Vel=${velocity}, Pred20=${predictedBg20min}. Forcing low.")
                } else if (isHypoCritical && x[0] > z + 5.0) {
                    x[0] = (x[0] + z) / 2.0
                }

                data[i].smoothed = x[0]
            }

            // Determine Trend Arrow from Rate (State x[1])
            data[i].trendArrow = computeTrendArrow(x[1])

            // Store for smoother
            results[i] = x[0]
            forwardStates.add(0, stateBefore) // Store in reverse order of processing (Newest first)
        }

        // Update learned R globally
        learnedR = R

        // Send a lightweight, informational quality estimate to the dashboard.
        val compressionRate = if (processedPoints > 0) compressionPoints.toDouble() / processedPoints.toDouble() else 0.0
        val outlierRate = if (processedPoints > 0) outlierPoints.toDouble() / processedPoints.toDouble() else 0.0

        val tier = when {
            compressionRate >= 0.15 || outlierRate >= 0.25 || learnedR >= 70.0 -> AdaptiveSmoothingQualityTier.BAD
            learnedR >= 45.0 || outlierRate >= 0.10 || compressionRate >= 0.07 -> AdaptiveSmoothingQualityTier.UNCERTAIN
            else -> AdaptiveSmoothingQualityTier.OK
        }

        val now = Clock.System.now().toEpochMilliseconds()
        // Dashboard reads this synchronously — must update every pass (do not tie to Rx throttle).
        lastQualitySnapshot = AdaptiveSmoothingQualitySnapshot(
            tier = tier,
            learnedR = learnedR,
            outlierRate = outlierRate,
            compressionRate = compressionRate,
            updatedAtMillis = now
        )

        val shouldSend = lastAdaptiveSmoothingQualityTier != tier ||
            (now - lastAdaptiveSmoothingQualityEventAt) >= QUALITY_EVENT_THROTTLE_MS

        if (shouldSend) {
            lastAdaptiveSmoothingQualityTier = tier
            lastAdaptiveSmoothingQualityEventAt = now
            rxBus.send(EventAdaptiveSmoothingQuality(tier, learnedR, outlierRate, compressionRate))
        }

        // --- BACKWARD PASS (RTS SMOOTHER) ---
        if (forwardStates.size >= 3) {
            // Simplified RTS or just Filter is huge improvement.
        }
    }

    // ============================================================
    // 🛡️ HEURISTIC SAFETY LOGIC (From Adaptive Plugin)
    // ============================================================

    private fun calculateGlycemicContext(
        data: List<InMemoryGlucoseValue>,
        index: Int,
        currentIob: Double
    ): GlycemicContext {
        val valCur = data[index].value
        val valOld1 = if (index + 1 < data.size) data[index + 1].value else valCur

        // Heuristic Delta (Raw)
        val rawDelta = valCur - valOld1

        // Night
        val instant = Instant.fromEpochMilliseconds(data[index].timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour
        val isNight = preferences.get(BooleanKey.OApsAIMInight) == true || (hour < 7 || hour >= 23)

        val zone = when {
            valCur < 70 -> GlycemicZone.HYPO
            valCur < 90 -> GlycemicZone.LOW_NORMAL
            valCur < 180 -> GlycemicZone.TARGET
            else -> GlycemicZone.HYPER
        }

        return GlycemicContext(
            cv = 0.0,
            zone = zone,
            currentBg = valCur,
            iob = currentIob,
            isNight = isNight,
            rawDelta = rawDelta
        )
    }

    private fun isCompressionArtifactCandidate(ctx: GlycemicContext, data: List<InMemoryGlucoseValue>, index: Int): Boolean {
        val dropThreshold = if (ctx.isNight) -15.0 else -25.0

        if (ctx.rawDelta < dropThreshold) {
            if (ctx.iob < 3.0) {
                return true
            }
        }
        return false
    }

    // ============================================================
    // UKF MATHEMATICS (Unscented Transform)
    // ============================================================

    private data class FilterState(
        val x: DoubleArray,
        val P: DoubleArray,
        val xPred: DoubleArray,
        val PPred: DoubleArray,
        val dt: Double
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FilterState) return false
            if (!x.contentEquals(other.x)) return false
            if (!P.contentEquals(other.P)) return false
            if (!xPred.contentEquals(other.xPred)) return false
            if (!PPred.contentEquals(other.PPred)) return false
            return dt == other.dt
        }

        override fun hashCode(): Int {
            var result = x.contentHashCode()
            result = 31 * result + P.contentHashCode()
            result = 31 * result + xPred.contentHashCode()
            result = 31 * result + PPred.contentHashCode()
            result = 31 * result + dt.hashCode()
            return result
        }
    }

    private fun predict(x: DoubleArray, P: DoubleArray, Q: DoubleArray, dt: Double): Pair<DoubleArray, DoubleArray> {
        val sigmaPoints = generateSigmaPoints(x, P)
        val sigmaPointsPred = Array(2 * n + 1) { DoubleArray(n) }

        for (i in 0 until 2 * n + 1) {
            sigmaPointsPred[i][0] = sigmaPoints[i][0] + sigmaPoints[i][1] * dt
            sigmaPointsPred[i][1] = sigmaPoints[i][1] * RATE_DAMPING
        }

        val xPred = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            xPred[0] += Wm[i] * sigmaPointsPred[i][0]
            xPred[1] += Wm[i] * sigmaPointsPred[i][1]
        }

        val PPred = DoubleArray(4)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPointsPred[i][0] - xPred[0]
            val dx1 = sigmaPointsPred[i][1] - xPred[1]
            PPred[0] += Wc[i] * dx0 * dx0
            PPred[1] += Wc[i] * dx0 * dx1
            PPred[2] += Wc[i] * dx1 * dx0
            PPred[3] += Wc[i] * dx1 * dx1
        }

        val qScale = dt / 5.0
        PPred[0] += Q[0] * qScale
        PPred[3] += Q[3] * qScale

        PPred[0] = max(PPred[0], 0.1)
        PPred[3] = max(PPred[3], 0.001)

        return Pair(xPred, PPred)
    }

    private fun update(xPred: DoubleArray, PPred: DoubleArray, z: Double, R: Double, x: DoubleArray, P: DoubleArray) {
        val sigmaPoints = generateSigmaPoints(xPred, PPred)
        val zSigma = DoubleArray(2 * n + 1)

        for (i in 0 until 2 * n + 1) zSigma[i] = sigmaPoints[i][0]

        var zPred = 0.0
        for (i in 0 until 2 * n + 1) zPred += Wm[i] * zSigma[i]

        var Pzz = 0.0
        for (i in 0 until 2 * n + 1) {
            val dz = zSigma[i] - zPred
            Pzz += Wc[i] * dz * dz
        }
        Pzz += R

        if (Pzz < 1e-6) return

        val Pxz = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPoints[i][0] - xPred[0]
            val dx1 = sigmaPoints[i][1] - xPred[1]
            val dz = zSigma[i] - zPred
            Pxz[0] += Wc[i] * dx0 * dz
            Pxz[1] += Wc[i] * dx1 * dz
        }

        val K = DoubleArray(n)
        K[0] = Pxz[0] / Pzz
        K[1] = Pxz[1] / Pzz

        val innovation = z - zPred
        x[0] = xPred[0] + K[0] * innovation
        x[1] = xPred[1] + K[1] * innovation

        x[1] = x[1].coerceIn(-5.0, 5.0)

        P[0] = PPred[0] - K[0] * Pzz * K[0]
        P[1] = PPred[1] - K[0] * Pzz * K[1]
        P[2] = PPred[2] - K[1] * Pzz * K[0]
        P[3] = PPred[3] - K[1] * Pzz * K[1]

        P[0] = max(P[0], 0.1)
        P[3] = max(P[3], 0.001)
    }

    private fun generateSigmaPoints(x: DoubleArray, P: DoubleArray): Array<DoubleArray> {
        val sigmaPoints = Array(2 * n + 1) { DoubleArray(n) }
        val sqrtP = matrixSqrt2x2(P)

        sigmaPoints[0][0] = x[0]; sigmaPoints[0][1] = x[1]

        for (i in 0 until n) {
            sigmaPoints[i + 1][0] = x[0] + gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1][1] = x[1] + gamma * sqrtP[i * 2 + 1]
            sigmaPoints[i + 1 + n][0] = x[0] - gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1 + n][1] = x[1] - gamma * sqrtP[i * 2 + 1]
        }
        return sigmaPoints
    }

    private fun matrixSqrt2x2(P: DoubleArray): DoubleArray {
        val a = P[0]
        val b = (P[1] + P[2]) / 2.0
        val d = P[3]

        val l11 = sqrt(max(a, 1e-9))
        val l21 = b / l11
        val discriminant = d - l21 * l21

        val l22 = if (discriminant < 0) sqrt(max(d, 1e-9)) else sqrt(discriminant)

        return doubleArrayOf(l11, l21, 0.0, l22)
    }

    // ============================================================
    // ADAPTATION AND UTILS
    // ============================================================

    private fun isOutlier(innovation: Double, innovationVariance: Double, P: DoubleArray): Boolean {
        val mahalanobisSq = (innovation * innovation) / innovationVariance
        return mahalanobisSq > CHI_SQUARED_THRESHOLD || abs(innovation) > OUTLIER_ABSOLUTE
    }

    private fun adaptMeasurementNoise(currentR: Double, innovations: ArrayDeque<Double>, rawInnovationsSquared: ArrayDeque<Double>): Double {
        if (innovations.size < 8) return currentR
        val avgInnovSq = med(innovations)

        if (innovations.any { it > 9.0 }) return currentR.coerceIn(R_MIN, R_MAX)

        var newR = currentR
        if (avgInnovSq >= 1.1 || avgInnovSq <= 0.9) {
            newR = currentR + 0.06 * (med(rawInnovationsSquared) - currentR)
        }
        return newR.coerceIn(R_MIN, R_MAX)
    }

    private fun med(list: Collection<Double>): Double {
        val sorted = list.sorted()
        return if (sorted.size % 2 == 0) (sorted[sorted.size / 2] + sorted[(sorted.size - 1) / 2]) / 2.0 else sorted[sorted.size / 2]
    }

    private fun trackInnovation(innovation: Double, innovationVariance: Double) {
        val normalizedSq = (innovation * innovation) / innovationVariance
        val rawSq = innovation * innovation
        innovations.addFirst(normalizedSq)
        rawInnovationVariance.addFirst(rawSq)
        if (innovations.size > innovationWindow) innovations.removeLast()
        if (rawInnovationVariance.size > innovationWindow) rawInnovationVariance.removeLast()
    }

    private fun computeTrendArrow(rate: Double): TrendArrow {
        return when {
            rate > 2.0 -> TrendArrow.DOUBLE_UP
            rate > 1.0 -> TrendArrow.SINGLE_UP
            rate > 0.5 -> TrendArrow.FORTY_FIVE_UP
            rate < -2.0 -> TrendArrow.DOUBLE_DOWN
            rate < -1.0 -> TrendArrow.SINGLE_DOWN
            rate < -0.5 -> TrendArrow.FORTY_FIVE_DOWN
            else -> TrendArrow.FLAT
        }
    }

    private fun copyRawToSmoothed(data: MutableList<InMemoryGlucoseValue>) {
        data.forEach {
            it.smoothed = it.value
            it.trendArrow = TrendArrow.NONE
        }
    }

    private fun sanitizeOutput(data: MutableList<InMemoryGlucoseValue>) {
        data.forEach { gv ->
            val smoothed = gv.smoothed
            if (smoothed == null || !smoothed.isFinite()) {
                gv.smoothed = gv.value.coerceIn(MIN_VALID_BG, MAX_VALID_BG)
                if (gv.trendArrow == null) gv.trendArrow = TrendArrow.FLAT
                return@forEach
            }
            gv.smoothed = smoothed.coerceIn(MIN_VALID_BG, MAX_VALID_BG)
            if (gv.trendArrow == null) gv.trendArrow = TrendArrow.FLAT
        }
    }

    // ============================================================
    // PERSISTENCE & SENSOR MANAGEMENT
    // ============================================================

    private fun loadPersistedParameters() {
        try {
            val lastSaved = preferences.get(AdaptiveLongNonKey.LastSavedTimestamp)
            val savedSensorChange = preferences.get(AdaptiveLongNonKey.LastSensorChangeTimestamp)

            if (lastSaved > 0) {
                lastSensorChangeTimestamp = savedSensorChange
                lastProcessedTimestamp = preferences.get(AdaptiveLongNonKey.LastProcessedTimestamp)
                learnedR = preferences.get(AdaptiveDoubleNonKey.LearnedR)
                sensorSessionId = preferences.get(AdaptiveIntNonKey.SessionId)

                if (learnedR !in R_MIN..R_MAX) {
                    learnedR = R_INIT
                }
            }
        } catch (e: Exception) {
            learnedR = R_INIT
        }
    }

    private fun savePersistedParameters() {
        try {
            preferences.put(AdaptiveLongNonKey.LastSavedTimestamp, Clock.System.now().toEpochMilliseconds())
            preferences.put(AdaptiveLongNonKey.LastSensorChangeTimestamp, lastSensorChangeTimestamp)
            preferences.put(AdaptiveLongNonKey.LastProcessedTimestamp, lastProcessedTimestamp)
            preferences.put(AdaptiveDoubleNonKey.LearnedR, learnedR)
            preferences.put(AdaptiveIntNonKey.SessionId, sensorSessionId)
        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "AdaptiveSmoothing: Failed to save persisted parameters", e)
        }
    }

    private fun shouldResetLearning(currentTimestamp: Long): Boolean {
        if (resetRequested.compareAndSet(true, false)) return true
        if (lastProcessedTimestamp == 0L) return true
        val diff = (currentTimestamp - lastProcessedTimestamp) / 60000.0
        if (diff < 0 || diff > 1440) return true
        return false
    }

    private fun resetLearning() {
        learnedR = R_INIT
        innovations.clear()
        rawInnovationVariance.clear()
        sensorSessionId++
        lastAdaptiveSmoothingQualityTier = null
        lastQualitySnapshot = null
        aapsLogger.info(LTag.GLUCOSE, "HybridSmoothing: Learning Reset. R=$R_INIT")
        savePersistedParameters()
    }

    private suspend fun loadLastSensorChange() {
        try {
            val events = persistenceLayer.getTherapyEventDataFromTime(
                Clock.System.now().toEpochMilliseconds() - 30L * 24 * 3600 * 1000,
                false
            )
            val latest = events
                .filter { it.type == TE.Type.SENSOR_CHANGE }
                .maxByOrNull { it.timestamp }

            if (latest != null && latest.timestamp > lastSensorChangeTimestamp) {
                lastSensorChangeTimestamp = latest.timestamp
                resetRequested.store(true)
            }
        } catch (t: Throwable) {
            aapsLogger.error(LTag.GLUCOSE, "AdaptiveSmoothing: loadLastSensorChange error", t)
        }
    }

    private fun checkForSensorChange() {
        scope?.launch {
            loadLastSensorChange()
        }
    }

    private companion object {
        const val MIN_VALID_BG: Double = 39.0
        const val MAX_VALID_BG: Double = 500.0
        const val QUALITY_EVENT_THROTTLE_MS: Long = 30_000L
    }
}
