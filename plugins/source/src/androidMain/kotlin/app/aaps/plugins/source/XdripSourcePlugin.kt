package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.ui.compose.icons.IcXDrip
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.core.utils.receivers.Inbox
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(400)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<XDripSource>())
@Inject
class XdripSourcePlugin(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager,
    private val persistenceLayer: PersistenceLayer,
    private val context: Context,
    private val dateUtil: DateUtil
) : AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.source_xdrip)
            )
        }
        .icon(IcXDrip)
        .pluginName(TextRef.AndroidRes(R.string.source_xdrip))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_xdrip)),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences,
    notificationManager = notificationManager
), BgSource, XDripSource {

    override var sensorBatteryLevel = -1

    private var pollingJob: Job? = null
    private var prefObserverJob: Job? = null
    internal var lastPolledTimestamp: Long = 0L
    private var isReceiverRegistered = false

    private val directBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: android.content.Intent) {
            val bundle = intent.extras ?: return
            pluginScope.launch(Dispatchers.IO) {
                handleDirectBroadcast(intent.action, bundle)
            }
        }
    }

    internal val isPollingActive: Boolean get() = pollingJob?.isActive == true
    internal val currentPollingJob: Job? get() = pollingJob
    internal val currentPrefObserverJob: Job? get() = prefObserverJob
    internal val isDirectReceiverRegistered: Boolean get() = isReceiverRegistered

    fun registerDirectReceiver() {
        if (isReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(Intents.ACTION_NEW_BG_ESTIMATE)
            addAction(Intents.GLUCODATA_MINUTE)
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                directBroadcastReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
            aapsLogger.info(LTag.BGSOURCE, "Registered direct dynamic xDrip/Glucodata broadcast receiver")
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Failed to register direct receiver", e)
        }
    }

    fun unregisterDirectReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(directBroadcastReceiver)
        } catch (e: Exception) {
            aapsLogger.debug(LTag.BGSOURCE, "Error unregistering direct receiver: ${e.message}")
        }
        isReceiverRegistered = false
    }

    override suspend fun onStart() {
        super.onStart()
        if (isEnabled()) {
            registerDirectReceiver()
        }
        prefObserverJob?.cancel()
        prefObserverJob = pluginScope.launch {
            preferences.observe(BooleanKey.OApsxdriponeminute).collect { enabled ->
                if (enabled && isEnabled()) {
                    registerDirectReceiver()
                    if (pollingJob?.isActive == true) return@collect
                    startPolling()
                } else if (!isEnabled()) {
                    unregisterDirectReceiver()
                    stopPolling()
                } else {
                    stopPolling()
                }
            }
        }
    }

    override suspend fun onStop() {
        unregisterDirectReceiver()
        prefObserverJob?.cancel()
        prefObserverJob = null
        stopPolling()
        super.onStop()
    }

    internal fun mapRateToTrendArrow(rate: Float): TrendArrow = when {
        rate >= 3.5f  -> TrendArrow.DOUBLE_UP
        rate >= 2.0f  -> TrendArrow.SINGLE_UP
        rate >= 1.0f  -> TrendArrow.FORTY_FIVE_UP
        rate > -1.0f  -> TrendArrow.FLAT
        rate > -2.0f  -> TrendArrow.FORTY_FIVE_DOWN
        rate > -3.5f  -> TrendArrow.SINGLE_DOWN
        else          -> TrendArrow.DOUBLE_DOWN
    }

    internal suspend fun handleDirectBroadcast(action: String?, bundle: Bundle) {
        if (!isEnabled()) return
        val gv: GV
        val timestamp: Long
        when (action) {
            Intents.ACTION_NEW_BG_ESTIMATE -> {
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0L)
                val bgValue = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)
                if (timestamp <= 0L || bgValue <= 0.0) return
                val slopeName = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)
                val raw = bundle.getDouble(Intents.EXTRA_RAW, 0.0)
                val source = bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: ""
                gv = GV(
                    timestamp = timestamp,
                    value = round(bgValue),
                    raw = round(raw),
                    noise = null,
                    trendArrow = TrendArrow.fromString(slopeName),
                    sourceSensor = SourceSensor.fromString(source)
                )
                sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            }
            Intents.GLUCODATA_MINUTE -> {
                timestamp = bundle.getLong("glucodata.Minute.Time", 0L).takeIf { it > 0L } ?: dateUtil.now()
                val bgMgdl = if (bundle.containsKey("glucodata.Minute.mgdl")) {
                    bundle.getInt("glucodata.Minute.mgdl").toDouble()
                } else if (bundle.containsKey("glucodata.Minute.glucose")) {
                    val g = bundle.getFloat("glucodata.Minute.glucose").toDouble()
                    if (g < 35.0) g * app.aaps.core.data.configuration.Constants.MMOLL_TO_MGDL else g
                } else 0.0
                if (timestamp <= 0L || bgMgdl <= 0.0) return
                val rate = bundle.getFloat("glucodata.Minute.Rate", 0.0f)
                val trend = mapRateToTrendArrow(rate)
                val serial = bundle.getString("glucodata.Minute.SerialNumber") ?: "Juggluco"
                gv = GV(
                    timestamp = timestamp,
                    value = round(bgMgdl),
                    raw = 0.0,
                    noise = null,
                    trendArrow = trend,
                    sourceSensor = SourceSensor.fromString(serial)
                )
            }
            else -> return
        }

        if (timestamp == XdripSourceWorker.lastDataTimestamp || timestamp == lastPolledTimestamp) {
            aapsLogger.debug(LTag.BGSOURCE, "Direct broadcast: skipping duplicate timestamp $timestamp")
            return
        }

        if (!preferences.get(BooleanKey.OApsxdriponeminute) && timestamp - XdripSourceWorker.lastDataTimestamp < 240000L) {
            aapsLogger.debug(LTag.BGSOURCE, "Direct broadcast: ignoring data, not enough time passed since last processing")
            return
        }

        XdripSourceWorker.lastDataTimestamp = maxOf(XdripSourceWorker.lastDataTimestamp, timestamp)
        lastPolledTimestamp = maxOf(lastPolledTimestamp, timestamp)

        try {
            persistenceLayer.insertCgmSourceData(Sources.Xdrip, listOf(gv), emptyList(), null)
            aapsLogger.info(LTag.BGSOURCE, "Direct broadcast: inserted new SGV ${gv.value} at $timestamp from $action")
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Direct broadcast: error inserting data", e)
        }
    }

    internal fun isValidPolledReading(
        timestamp: Long,
        sgv: Double,
        now: Long,
        lastPolledTs: Long,
        lastWorkerTs: Long
    ): Boolean {
        if (timestamp <= 0 || sgv <= 0.0) return false
        if (now - timestamp > 600_000L) return false
        if (timestamp - now > 60_000L) return false
        if (timestamp <= lastPolledTs || timestamp <= lastWorkerTs) return false
        return true
    }

    fun startPollingIfEnabled() {
        if (isEnabled() && preferences.get(BooleanKey.OApsxdriponeminute)) {
            if (pollingJob?.isActive == true) return
            startPolling()
        } else {
            stopPolling()
        }
    }

    private fun startPolling() {
        stopPolling()
        aapsLogger.info(LTag.BGSOURCE, "Starting xDrip 1-min local web server poller (127.0.0.1:17580)")
        pollingJob = pluginScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollXdripWebServer()
                } catch (e: Exception) {
                    aapsLogger.debug(LTag.BGSOURCE, "xDrip 1-min poller check failed: ${e.message}")
                }
                delay(15_000L)
            }
        }
    }

    fun stopPolling() {
        val job = pollingJob
        pollingJob = null
        job?.cancel()
    }

    private suspend fun pollXdripWebServer() {
        val url = java.net.URL("http://127.0.0.1:17580/sgv.json?count=1")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(jsonText)
            if (array.length() > 0) {
                val item = array.getJSONObject(0)
                val timestamp = item.optLong("date", 0L)
                val sgv = item.optDouble("sgv", 0.0)
                val direction = item.optString("direction", "")
                val device = item.optString("device", "xDrip")

                val now = System.currentTimeMillis()
                if (isValidPolledReading(timestamp, sgv, now, lastPolledTimestamp, XdripSourceWorker.lastDataTimestamp)) {
                    lastPolledTimestamp = timestamp
                    XdripSourceWorker.lastDataTimestamp = timestamp

                    val gv = GV(
                        timestamp = timestamp,
                        value = round(sgv),
                        raw = round(item.optDouble("raw", 0.0)),
                        noise = null,
                        trendArrow = TrendArrow.fromString(direction),
                        sourceSensor = SourceSensor.fromString(device)
                    )
                    persistenceLayer.insertCgmSourceData(Sources.Xdrip, listOf(gv), emptyList(), null)
                    aapsLogger.info(LTag.BGSOURCE, "xDrip 1-min poller: inserted new SGV $sgv at $timestamp, loop triggered")
                }
            }
        }
    }

    // cannot be inner class because of needed injection

    @AssistedInject
    class XdripSourceWorker(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val xdripSourcePlugin: XdripSourcePlugin,
        private val persistenceLayer: PersistenceLayer,
        private val preferences: Preferences,
        private val dateUtil: DateUtil,
        private val dataInbox: DataInbox
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        /**
         * Metro builds this worker. The parameter names must match [MetroWorkerCreator],
         * because Metro matches assisted parameters by name and not only by type.
         */
        @AssistedFactory
        fun interface Factory : MetroWorkerCreator {

            override fun create(context: Context, params: WorkerParameters): XdripSourceWorker
        }

        fun getSensorStartTime(bundle: Bundle): Long? {
            val now = dateUtil.now()
            var sensorStartTime: Long? = if (preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
                bundle.getLong(Intents.EXTRA_SENSOR_STARTED_AT, 0)
            } else {
                null
            }
            // check start time validity
            sensorStartTime?.let {
                if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
            }
            return sensorStartTime
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            // Drain first, unconditionally: drain() clears DataInbox's pending-work gate, so every
            // enqueued worker MUST reach it. If we returned early (plugin disabled) before draining,
            // the gate would stay set and silently block all future enqueues for this slot until
            // the process restarts. Bundles drained while disabled are intentionally discarded.
            val bundles = dataInbox.drain(XdripInbox)
            if (!xdripSourcePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            if (bundles.isEmpty()) return Result.success(workDataOf("Result" to "no data"))

            var hadFailure = false
            for ((index, bundle) in bundles.withIndex()) {
                try {
                    processBundle(bundle)
                } catch (e: CancellationException) {
                    // WorkManager stopped this run (e.g. chain overload / run-attempt limit). The
                    // coroutine contract requires CancellationException to propagate, otherwise the
                    // loop keeps fighting a cancelled Job and spams failures for every remaining
                    // bundle. drain() already removed the whole batch, so re-queue this bundle plus
                    // everything not yet processed before propagating — otherwise those readings are
                    // lost. requeue/enqueue are non-suspending, so they complete despite cancellation.
                    dataInbox.requeue(XdripInbox, bundles.subList(index, bundles.size))
                    throw e
                } catch (e: Exception) {
                    // processBundle early-returns on malformed-bundle conditions; anything that
                    // reaches the catch is a real exception (typically a DB write). Surface as
                    // failure so WorkInfo reflects the truth.
                    aapsLogger.error(LTag.BGSOURCE, "Failed processing xDrip bundle", e)
                    hadFailure = true
                }
            }
            return if (hadFailure) Result.failure(workDataOf("Error" to "one or more bundles failed")) else Result.success()
        }

        companion object {
            var lastDataTimestamp: Long = 0L
        }

        private suspend fun processBundle(bundle: Bundle) {
            val currentTimestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0)
            if (currentTimestamp <= 0) {
                aapsLogger.warn(LTag.BGSOURCE, "Skipping xDrip bundle: missing or invalid timestamp")
                return
            }
            if (currentTimestamp == lastDataTimestamp) {
                aapsLogger.debug(LTag.BGSOURCE, "Ignoring duplicate xDrip data: $currentTimestamp == $lastDataTimestamp")
                return
            }
            if (!preferences.get(BooleanKey.OApsxdriponeminute) && currentTimestamp - lastDataTimestamp < 240000) {
                aapsLogger.debug(LTag.BGSOURCE, "Ignoring data, not enough time passed since last processing")
                return
            }
            lastDataTimestamp = maxOf(lastDataTimestamp, currentTimestamp)

            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = currentTimestamp,
                value = round(bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)),
                raw = round(bundle.getDouble(Intents.EXTRA_RAW, 0.0)),
                noise = null,
                trendArrow = TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: "")
            )
            val newSensorStartTime = getSensorStartTime(bundle)
            // Retrieve last stored sensorStartTime from the database
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            // Decide whether to update sensorStartTime or keep the last stored one
            val finalSensorStartTime = when {
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    abs(newSensorStartTime - lastStoredSensorStartTime) <= 300_000 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is within 5 minutes range, skipping update.")
                    null
                }

                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    newSensorStartTime < lastStoredSensorStartTime                 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is older than last stored time, skipping update.")
                    null
                }

                else                                                               -> newSensorStartTime
            }
            // Always update glucoseValues, but use the decided sensorStartTime
            if (glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
            } else {
                aapsLogger.warn(LTag.BGSOURCE, "Skipping xDrip bundle: missing glucoseValue")
                return
            }
            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
        }
    }
}

object XdripInbox : Inbox<Bundle>("xdrip-bg", XdripSourcePlugin.XdripSourceWorker::class.java)
