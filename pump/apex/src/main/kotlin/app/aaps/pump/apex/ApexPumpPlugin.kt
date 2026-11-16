package app.aaps.pump.apex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.DoseStepSize
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.CoroutineStart
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import app.aaps.pump.apex.connectivity.ApexBluetooth
import app.aaps.pump.apex.connectivity.FirmwareVersion
import app.aaps.pump.apex.connectivity.ProtocolVersion
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength
import app.aaps.pump.apex.misc.BatteryType
import app.aaps.pump.apex.ui.ApexPumpComposeContent
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import app.aaps.pump.apex.utils.keys.ApexDoubleKey
import app.aaps.pump.apex.utils.keys.ApexStringKey
import app.aaps.pump.apex.utils.toApexReadableProfile
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONException

import org.json.JSONObject
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.IntKey as MetroIntKey
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * @author Roman Rikhter (teledurak@gmail.com)
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@MetroIntKey(1140)
@SingleIn(AppScope::class)
class ApexPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    commandQueue: CommandQueue,
    val context: Context,
    preferences: Preferences,
    val rxBus: RxBus,
    val aapsSchedulers: AapsSchedulers,
    val fabricPrivacy: FabricPrivacy,
    val dateUtil: DateUtil,
    val pump: ApexPump,
    val config: Config,
    private val constraintsChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    notificationManager: NotificationManager
): PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            ApexPumpComposeContent(
                pluginName = rh.gs(R.string.apex_plugin_name)
            )
        }
        .pluginName(app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.apex_plugin_name))
        .description(app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.apex_plugin_description)),
    ownPreferences = ApexBooleanKey.entries + ApexDoubleKey.entries + ApexStringKey.entries,
    aapsLogger,
    rh,
    preferences,
    commandQueue,
    notificationManager
), Pump, PluginConstraints {

    private var service: ApexService? = null
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            service = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as ApexService.LocalBinder
            this@ApexPumpPlugin.service = mLocalBinder.serviceInstance.also {
                it.startConnection()
            }
        }
    }


    override suspend fun onStart() {
        super.onStart()
        aapsLogger.debug(LTag.PUMP, "Starting APEX plugin")
        context.bindService(Intent(context, ApexService::class.java), connection, Context.BIND_AUTO_CREATE)
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(pluginScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                context.unbindService(connection)
            }
    }

    override suspend fun onStop() {
        aapsLogger.debug(LTag.PUMP, "Stopping APEX plugin")
        service?.disconnect()
        context.unbindService(connection)
        super.onStop()
    }

    private val _lastDataTime = MutableStateFlow(0L)
    override val lastDataTime: StateFlow<Long> = _lastDataTime

    private val _lastBolusTime = MutableStateFlow<Long?>(null)
    override val lastBolusTime: StateFlow<Long?> = _lastBolusTime

    private val _lastBolusAmount = MutableStateFlow<PumpInsulin?>(null)
    override val lastBolusAmount: StateFlow<PumpInsulin?> = _lastBolusAmount

    override val isFakingTempsByExtendedBoluses = false
    override fun canHandleDST() = false

    override fun manufacturer() = ManufacturerType.Apex
    override fun model() = PumpType.APEX_TRUCARE_III
    override fun serialNumber() = preferences.get(ApexStringKey.LastConnectedSerialNumber)

    override val baseBasalRate: PumpRate
        get() = PumpRate(pump.basal?.rate ?: 0.0)

    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel

    fun updateStatusFlows() {
        _lastDataTime.value = pump.dateTime.millis
        _lastBolusTime.value = pump.lastBolus?.dateTime?.millis
        _lastBolusAmount.value = pump.lastBolus?.let { PumpInsulin((it.standardPerformed + it.extendedPerformed) * 0.025) }
        _reservoirLevel.value = PumpInsulin(pump.reservoirLevel)
        _batteryLevel.value = pump.batteryLevel.percentage
    }

    override val pumpDescription = PumpDescription().fillFor(model())

    override fun isBusy() = false //service?.isBusy ?: false
    override fun isSuspended() = pump.isSuspended
    override fun isInitialized() = pump.isInitialized
    override fun isConnecting() = when (service?.connectionStatus) {
        ApexBluetooth.Status.CONNECTING -> true
        ApexBluetooth.Status.CONNECTED -> service?.isReadyForExecutingCommands == false
        else -> false
    }
    override fun isHandshakeInProgress() = false
    override fun isConnected() = service?.connectionStatus == ApexBluetooth.Status.CONNECTED && service?.isReadyForExecutingCommands == true
    override fun isBatteryChangeLoggingEnabled() = preferences.get(ApexBooleanKey.LogBatteryChange)


    // We should be always connected to the pump.
    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Triggered connect: $reason")
        if (service?.apexBluetooth?.status == ApexBluetooth.Status.DISCONNECTED) {
            service?.startConnection()
        }
    }
    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Triggered disconnect: $reason")
    }
    override fun stopConnecting() {
        aapsLogger.debug(LTag.PUMP, "Triggered stopConnecting")
    }



    override fun pumpSpecificShortStatus(veryShort: Boolean): String {
        if (!isInitialized() || !isConnected()) return rh.gs(app.aaps.pump.common.R.string.pump_status_not_initialized)
        val status = pump.status!!

        val ret = "${rh.gs(R.string.status_conn_status)}: ${service!!.connectionStatus.toLocalString(rh)}\n" +
            "${rh.gs(R.string.status_pump_status)}: ${status.getPumpStatus(rh)}\n" +
            "${rh.gs(R.string.status_last_bolus)}: ${pump.lastBolus?.toShortLocalString(rh) ?: "-"}\n" +
            "${rh.gs(R.string.status_tbr)}: ${status.getTBR(rh)}\n" +
            "${rh.gs(R.string.status_basal)}: ${status.getBasal(rh)}\n" +
            "${rh.gs(R.string.status_reservoir)}: ${status.getReservoirLevel(rh)}\n" +
            "${rh.gs(R.string.status_battery)}: ${status.getBatteryLevel(rh)}"

        aapsLogger.debug(LTag.PUMP, "Short status: $ret")
        return ret
    }

    fun updatePumpDescription() {
        aapsLogger.debug(LTag.PUMP, "Updating pump description")
        pumpDescription.maxTempAbsolute = pump.maxBasal
        pumpDescription.basalMaximumRate = pump.maxBasal
    }

    override suspend fun loadTDDs(): PumpEnactResult {
        val ret = PumpEnactResultImpl()
        if (!isInitialized() || !isConnected()) {
            return ret.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_not_ready)
            }
        }

        val status = service!!.getTDDs("ApexPumpPlugin-loadTDDs")
        return ret.apply {
            success = status
            enacted = status
        }
    }

    override suspend fun getPumpStatus(reason: String) {
        if (!isInitialized() || !isConnected()) return
        aapsLogger.debug(LTag.PUMP, "Requested pump status cause of $reason")
        if (!service!!.getStatus("ApexPumpPlugin-getPumpStatus", force = true)) return
        updateStatusFlows()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
    }

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val ret = PumpEnactResultImpl()
        if (!isInitialized() || !isConnected()) {
            return ret.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_not_ready)
            }
        }

        var result = service!!.updateBasalPatternIndex(ApexService.USED_BASAL_PATTERN_INDEX, "ApexPumpPlugin-setNewBasalProfile")
        if (!result) {
            return ret.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_failed_to_switch_basal_profile_index)
            }
        }

        result = service!!.updateCurrentBasalPattern(profile.toApexReadableProfile(), "ApexPumpPlugin-setNewBasalProfile")
        if (!result) {
            return ret.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_failed_to_update_basal_profile)
            }
        }

        return ret.apply {
            success = true
            enacted = true
        }
    }

    @Synchronized
    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized() || !isConnected()) return true
        val pumpBasalProfiles = service!!.getBasalProfiles("ApexPumpPlugin-isThisProfileSet") ?: return true
        val pumpBasalProfile = pumpBasalProfiles[ApexService.USED_BASAL_PATTERN_INDEX]
        for (i in 0..<48) {
            val profileBasal = profile.getBasalTimeFromMidnight(i * 30 * 60)
            val pumpBasal = pumpBasalProfile!![i]
            val precision = DoseStepSize.Apex.getStepSizeForAmount(profileBasal) / 2
            if (abs(pumpBasal - profileBasal) > precision) {
                aapsLogger.info(LTag.PUMP, "Profiles are not same: req $profileBasal != pump $pumpBasal, block $i, time ${i * 30 * 60}")
                return false
            }
        }
        return true
    }

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }
        val pumpEnactResult = PumpEnactResultImpl()
        detailedBolusInfo.insulin = constraintsChecker
            .applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger))
            .value()

        if (!isInitialized() || !isConnected()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_not_ready)
            }
        }

        if (isSuspended()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_pump_suspended)
            }
        }

        val result = service!!.bolus(detailedBolusInfo, "ApexPumpPlugin-deliverTreatment")
        if (!result) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_bolus_start_failed)
            }
        }

        // We send max of 3 commands for bolus: StatusV1, StatusV2, Bolus
        // Max wait time for any of them is 2.5s => 7.5s in total.
        // We may reconnect pump in order to try to fix connection issues. This takes ~45s.
        // Pump sets boluses in steps of 0.025U/s for boluses <=1U, 0.05U/s for boluses >1U.
        val maxReasonableBolusTime = (
            if (detailedBolusInfo.insulin <= 1.0)
                detailedBolusInfo.insulin / 0.025
            else
                detailedBolusInfo.insulin / 0.05
        ).roundToLong() + 45 + 8
        pump.inProgressBolus?.let {
            synchronized(it) {
                it.waitMillis(maxReasonableBolusTime * 1000)
            }
        }

        val bolus = pump.inProgressBolus
        val successful = bolus != null && !bolus.failed
        if (successful) {
            service?.getStatus("ApexPumpPlugin-deliverTreatment", force = true)
            updateStatusFlows()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        }
        return pumpEnactResult.apply {
            success = successful
            if (successful) {
                enacted = bolus.currentDose > 0.024
                bolusDelivered = bolus.currentDose
            }
        }
    }

    override fun stopBolusDelivering() {
        if (!isInitialized() || !isConnected()) return
        pump.inProgressBolus?.let { ib: ApexPump.InProgressBolus ->
            synchronized(ib) {
                ib.cancelled = true
                ib.notifyAll()
            }
        }
        service!!.cancelBolus("ApexPumpPlugin-stopBolusDelivering")
    }

    override fun getPreferenceScreenContent(): PreferenceItem = PreferenceSubScreenDef(
        key = "apex_settings",
        titleResId = R.string.apex_settings,
        items = listOf(
            ApexStringKey.SerialNumber,
            ApexStringKey.FirmwareVer.withEntries(
                FirmwareVersion.entries.associate { it.name to app.aaps.core.keys.interfaces.TextRef.Literal(it.name) }
            ),
            ApexStringKey.AlarmSoundLength.withEntries(
                mapOf(
                    AlarmLength.Short.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_alarm_length_short),
                    AlarmLength.Medium.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_alarm_length_medium),
                    AlarmLength.Long.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_alarm_length_long)
                )
            ),
            ApexBooleanKey.LogInsulinChange,
            ApexBooleanKey.LogBatteryChange,
            ApexBooleanKey.HideSerial,
            ApexBooleanKey.CalculateBatteryPercentage,
            ApexStringKey.CalcBatteryType.withEntries(
                mapOf(
                    BatteryType.Alkaline.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_calc_battery_type_alkaline),
                    BatteryType.Lithium.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_calc_battery_type_lithium),
                    BatteryType.NiMh.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_calc_battery_type_ni_mh),
                    BatteryType.NiZn.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_calc_battery_type_ni_zn),
                    BatteryType.Custom.name to app.aaps.core.keys.interfaces.TextRef.AndroidRes(R.string.setting_calc_battery_type_custom)
                )
            ),
            ApexDoubleKey.BatteryLowVoltage,
            ApexDoubleKey.BatteryHighVoltage,
            ApexDoubleKey.MaxBasal,
            ApexDoubleKey.MaxBolus
        ),
        icon = pluginDescription.icon
    )

    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val pumpEnactResult = PumpEnactResultImpl()
        val profile = profileFunction.getProfile()
        val rate = if (profile != null) {
            constraintsChecker
                .applyBasalConstraints(ConstraintObject(absoluteRate, aapsLogger), profile)
                .value()
        } else {
            absoluteRate
        }
        val duration = durationInMinutes - durationInMinutes % 15

        if (!isInitialized() || !isConnected()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_not_ready)
            }
        }

        if (isSuspended()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_pump_suspended)
            }
        }

        val status = pump.status!!
        if (enforceNew && status.tbr != null) {
            val result = service!!.cancelTemporaryBasal("ApexPumpPlugin-setTempBasal")
            if (!result) {
                return pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.error_tbr_cancel_failed)
                }
            }
        }

        val result = service!!.temporaryBasal(rate, duration, tbrType, "ApexPumpPlugin-setTempBasal")
        if (!result) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_tbr_set_failed)
            }
        }

        return pumpEnactResult.apply {
            success = true
            enacted = true
        }
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return PumpEnactResultImpl().apply {
            success = false
            enacted = false
            comment = rh.gs(R.string.error_only_absolute_supported)
        }
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val pumpEnactResult = PumpEnactResultImpl()
        if (!isInitialized() || !isConnected()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_not_ready)
            }
        }

        if (isSuspended()) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_pump_suspended)
            }
        }

        val status = service!!.cancelTemporaryBasal("ApexPumpPlugin-cancelTempBasal")
        if (!status) {
            return pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.error_tbr_cancel_failed)
            }
        }

        return pumpEnactResult.apply {
            success = true
            enacted = true
        }
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        // Not yet supported
        return PumpEnactResultImpl().apply {
            success = false
            enacted = false
            comment = rh.gs(R.string.error_not_ready)
        }
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        // Not yet supported
        return PumpEnactResultImpl().apply {
            success = false
            enacted = false
            comment = rh.gs(R.string.error_not_ready)
        }
    }

    override suspend fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        if (!isInitialized() || !isConnected()) return
        service!!.syncDateTime("ApexService-timezoneOrDSTChanged")
    }

    private inner class PumpEnactResultImpl : PumpEnactResult {
        override var success: Boolean = false
        override var enacted: Boolean = false
        override var comment: String = ""
        override var duration: Int = 0
        override var absolute: Double = 0.0
        override var percent: Int = 0
        override var isPercent: Boolean = false
        override var isTempCancel: Boolean = false
        override var bolusDelivered: Double = 0.0
        override var queued: Boolean = false
        override var cancelled: Boolean = false

        override fun success(success: Boolean): PumpEnactResult = apply { this.success = success }
        override fun enacted(enacted: Boolean): PumpEnactResult = apply { this.enacted = enacted }
        override fun comment(comment: String): PumpEnactResult = apply { this.comment = comment }
        override fun comment(ref: app.aaps.core.keys.interfaces.TextRef): PumpEnactResult = apply { this.comment = rh.gs(ref) }
        override fun duration(duration: Int): PumpEnactResult = apply { this.duration = duration }
        override fun absolute(absolute: Double): PumpEnactResult = apply { this.absolute = absolute }
        override fun percent(percent: Int): PumpEnactResult = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean): PumpEnactResult = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean): PumpEnactResult = apply { this.queued = queued }
        override fun cancelled(cancelled: Boolean): PumpEnactResult = apply { this.cancelled = cancelled }

    }
}
