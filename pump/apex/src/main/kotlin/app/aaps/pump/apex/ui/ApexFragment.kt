package app.aaps.pump.apex.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.apex.ApexDriverStatus
import app.aaps.pump.apex.ApexPump
import app.aaps.pump.apex.R
import app.aaps.pump.apex.databinding.ApexFragmentBinding
import app.aaps.pump.apex.events.EventApexPumpDataChanged
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.di.injectMetroMembers
import androidx.fragment.app.Fragment
import dev.zacsweers.metro.Inject

class ApexFragment : Fragment() {
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pump: ApexPump
    @Inject lateinit var status: ApexDriverStatus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var config: Config
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var preferences: Preferences

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        context.injectMetroMembers(this)
    }

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var refreshLoop: Runnable = Runnable {
        activity?.runOnUiThread { updateGUI() }
    }

    private var _binding: ApexFragmentBinding? = null
    val binding: ApexFragmentBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ApexFragmentBinding.inflate(inflater, container, false)
        binding.refresh.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                commandQueue.readStatus("ApexFragment-manualRefresh")
            }
        }
        binding.cancelBolus.setOnClickListener {
            uel.log(Action.CANCEL_BOLUS, Sources.Pump)
            commandQueue.cancelAllBoluses(null)
        }
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()

        rxBus.toFlow(EventInitializationChanged::class).collectResilient(viewLifecycleOwner.lifecycleScope, aapsLogger, LTag.UI) { updateGUI() }
        rxBus.toFlow(EventPumpStatusChanged::class).collectResilient(viewLifecycleOwner.lifecycleScope, aapsLogger, LTag.UI) { updateGUI() }
        rxBus.toFlow(EventApexPumpDataChanged::class).collectResilient(viewLifecycleOwner.lifecycleScope, aapsLogger, LTag.UI) { updateGUI() }
        rxBus.toFlow(EventQueueChanged::class).collectResilient(viewLifecycleOwner.lifecycleScope, aapsLogger, LTag.UI) { updateGUI() }

        updateGUI()
        handler.postDelayed(refreshLoop, T.secs(15).msecs())
    }

    override fun onPause() {
        handler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    @SuppressLint("SetTextI18n")
    private fun updateGUI() {
        aapsLogger.error(LTag.UI, "updateGUI")

        val status = pump.status
        if (status == null)
            aapsLogger.error(LTag.UI, "No status available!")

        binding.connectionStatus.text = when {
            status == null -> "{fa-question} " + rh.gs(app.aaps.core.ui.R.string.unknown)
            activePlugin.activePump.isConnected() -> "{fa-bluetooth-b} " + rh.gs(R.string.overview_connection_status_connected)
            activePlugin.activePump.isConnecting() ->"{fa-bluetooth-b spin} " + rh.gs(R.string.overview_connection_status_connecting)
            else -> "{fa-plug} " + rh.gs(R.string.overview_connection_status_disconnected)
        }
        binding.serialNumber.text = pump.serialNumber
        binding.pumpStatus.text = status?.let { it.getPumpStatusIcon() + " " + it.getPumpStatus(rh) } ?: "?"
        binding.battery.text = status?.let { it.getBatteryIcon() + " " + it.getBatteryLevel(rh) } ?: "?"
        binding.reservoir.text = status?.getReservoirLevel(rh) ?: "?"
        binding.tempbasal.text = status?.getTBR(rh) ?: "?"
        binding.baseBasalRate.text = status?.getBasal(rh) ?: "?"
        binding.firmwareVersion.text =  pump.firmwareVersion?.toLocalString(rh) ?: "?"
        binding.lastBolus.text = pump.lastBolus?.toShortLocalString(rh) ?: "?"

        val msg = this.status.message
        binding.currAction.text = msg ?: ""
        binding.currAction.visibility = if (msg != null) View.VISIBLE else View.GONE

        binding.cancelBolus.visibility = if (pump.inProgressBolus != null) View.VISIBLE else View.GONE
        binding.serial.visibility = if (preferences.get(ApexBooleanKey.HideSerial) == false) View.VISIBLE else View.GONE

        if (status == null)
            binding.lastUpdate.text = "?"
        else if (config.isEngineeringMode())
            binding.lastUpdate.text = "${status.dateTime.hourOfDay.toString().padStart(2, '0')}:${status.dateTime.minuteOfHour.toString().padStart(2, '0')}:${status.dateTime.secondOfMinute.toString().padStart(2, '0')}"
        else
            binding.lastUpdate.text = "${status.dateTime.hourOfDay.toString().padStart(2, '0')}:${status.dateTime.minuteOfHour.toString().padStart(2, '0')}"
    }
}
