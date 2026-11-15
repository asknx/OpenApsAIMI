package app.aaps.pump.apex.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.pump.apex.ApexDriverStatus
import app.aaps.pump.apex.ApexPump
import app.aaps.pump.apex.R
import app.aaps.pump.apex.events.EventApexPumpDataChanged
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class ApexPumpViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val pump: ApexPump,
    private val status: ApexDriverStatus,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val preferences: Preferences,
    private val config: Config
) : ViewModel() {

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, viewModelScope)
    private val rxTrigger = MutableStateFlow(0L)

    init {
        rxBus.toFlow(EventInitializationChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.UI) { rxTrigger.value = System.currentTimeMillis() }
        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.UI) { rxTrigger.value = System.currentTimeMillis() }
        rxBus.toFlow(EventApexPumpDataChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.UI) { rxTrigger.value = System.currentTimeMillis() }
        rxBus.toFlow(EventQueueChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.UI) { rxTrigger.value = System.currentTimeMillis() }
    }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        rxTrigger,
        communicationStatus.queueStatusFlow
    ) { _, queueStatus ->
        buildUiState(queueStatus)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildUiState(null)
    )

    private fun buildUiState(queueStatus: androidx.compose.ui.text.AnnotatedString?): PumpOverviewUiState {
        val pumpStatus = pump.status
        val (bannerText, bannerLevel) = when {
            pumpStatus == null -> rh.gs(app.aaps.core.ui.R.string.unknown) to StatusLevel.UNSPECIFIED
            activePlugin.activePump.isConnected() -> rh.gs(R.string.overview_connection_status_connected) to StatusLevel.NORMAL
            activePlugin.activePump.isConnecting() -> rh.gs(R.string.overview_connection_status_connecting) to StatusLevel.WARNING
            else -> rh.gs(R.string.overview_connection_status_disconnected) to StatusLevel.CRITICAL
        }

        val msg = status.message
        val fullBannerText = if (!msg.isNullOrEmpty()) "$bannerText - $msg" else bannerText

        val lastUpdate = when {
            pumpStatus == null -> "?"
            config.isEngineeringMode() -> "${pumpStatus.dateTime.hourOfDay.toString().padStart(2, '0')}:${pumpStatus.dateTime.minuteOfHour.toString().padStart(2, '0')}:${pumpStatus.dateTime.secondOfMinute.toString().padStart(2, '0')}"
            else -> "${pumpStatus.dateTime.hourOfDay.toString().padStart(2, '0')}:${pumpStatus.dateTime.minuteOfHour.toString().padStart(2, '0')}"
        }

        val rows = listOf(
            PumpInfoRow(
                label = rh.gs(R.string.status_pump_status),
                value = pumpStatus?.let { it.getPumpStatusIcon() + " " + it.getPumpStatus(rh) } ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.status_basal),
                value = pumpStatus?.getBasal(rh) ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.status_tbr),
                value = pumpStatus?.getTBR(rh) ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.status_last_bolus),
                value = pump.lastBolus?.toShortLocalString(rh) ?: "-"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.status_reservoir),
                value = pumpStatus?.getReservoirLevel(rh) ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.status_battery),
                value = pumpStatus?.let { it.getBatteryIcon() + " " + it.getBatteryLevel(rh) } ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.setting_serial_number),
                value = pump.serialNumber,
                visible = preferences.get(ApexBooleanKey.HideSerial) != true
            ),
            PumpInfoRow(
                label = rh.gs(R.string.firmware_version),
                value = pump.firmwareVersion?.toLocalString(rh) ?: "?"
            ),
            PumpInfoRow(
                label = rh.gs(R.string.updated_at),
                value = lastUpdate
            )
        )

        val primaryActions = listOf(
            ScreenAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                icon = Icons.Filled.Refresh,
                onClick = { onRefreshClick() }
            ),
            ScreenAction(
                label = rh.gs(app.aaps.pump.common.R.string.pump_cmd_desc_cancel_bolus),
                icon = Icons.Filled.Cancel,
                visible = pump.inProgressBolus != null,
                onClick = { onCancelBolusClick() }
            )
        )

        return PumpOverviewUiState(
            statusBanner = StatusBanner(text = fullBannerText, level = bannerLevel),
            infoRows = rows,
            primaryActions = primaryActions,
            queueStatus = queueStatus
        )
    }

    fun onRefreshClick() {
        viewModelScope.launch {
            commandQueue.readStatus("ApexPump-manualRefresh")
        }
    }

    fun onCancelBolusClick() {
        uel.log(Action.CANCEL_BOLUS, Sources.Pump)
        commandQueue.cancelAllBoluses(null)
    }
}
