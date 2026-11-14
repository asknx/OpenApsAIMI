package app.aaps.plugins.sync.nsclientV3

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.R
import dev.zacsweers.metro.Inject
import java.util.concurrent.TimeUnit

/**
 * Remote Control Fragment v2 - Complete Mode System.
 * 
 * Features:
 * - All meal/activity/physio modes
 * - Editable durations
 * - Dual-format command sending (AIMI + CarePortal compatible)
 * - Active modes display with auto-refresh
 * - Parent-friendly UI
 */
class RemoteControlFragment : Fragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pinManager: NSClientPinManager
    @Inject lateinit var contextManager: app.aaps.plugins.aps.openAPSAIMI.context.ContextManager
    @Inject lateinit var accessAuthManager: RemoteAccessAuthManager
    @Inject lateinit var licenseValidator: LicenseKeyValidator
    @Inject lateinit var nsClientRepository: NSClientRepository

    private var pinSetupCard: MaterialCardView? = null
    private var newPinInput: TextInputEditText? = null
    private var confirmPinInput: TextInputEditText? = null
    private var btnSavePin: Button? = null
    private var pinInput: TextInputEditText? = null
    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager2? = null

    private lateinit var modesAdapter: ModesAdapter
    private lateinit var activeModesAdapter: ActiveModesAdapter
    
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshActiveModes()
            refreshHandler.postDelayed(this, 30000) // Refresh every 30s
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        context.injectMetroMembers(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_remote_control, container, false)
        pinSetupCard = root.findViewById(R.id.pin_setup_card)
        newPinInput = root.findViewById(R.id.new_pin_input)
        confirmPinInput = root.findViewById(R.id.confirm_pin_input)
        btnSavePin = root.findViewById(R.id.btn_save_pin)
        pinInput = root.findViewById(R.id.pin_input)
        tabLayout = root.findViewById(R.id.tab_layout)
        viewPager = root.findViewById(R.id.view_pager)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // ✅ STEP 1: Check premium license FIRST
            if (!licenseValidator.isLicenseActivated()) {
                showLicenseKeyDialog()
                return
            }
            
            // ✅ STEP 2: Check if access password needs configuration
            if (!accessAuthManager.isPasswordConfigured()) {
                showPasswordSetupDialog()
                return
            }
            
            // ✅ STEP 3: Check if unlocked (session active)
            if (!accessAuthManager.isUnlocked()) {
                showAccessLoginDialog()
                return
            }
            
            // ✅ STEP 4: Initialize UI (only if licensed and authenticated)
            initializeUI()
            
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] CRASH in onViewCreated", e)
            fabricPrivacy.logException(e)
            
            // Show error to user
            context?.let {
                Toast.makeText(it, "Erreur d'initialisation: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Initialize UI after successful authentication.
     */
    private fun initializeUI() {
        try {
            // Check if PIN needs configuration
            if (pinManager.needsConfiguration()) {
                showPinSetupDialog()
            }

            setupPinInput()
            setupTabs()
            
            // Initial refresh - delayed to allow fragment/NSClient to initialize
            refreshHandler.postDelayed({
                refreshActiveModes()
            }, 500)
            
            // Start auto-refresh
            refreshHandler.postDelayed(refreshRunnable, 1000)
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] Error initializing UI", e)
            fabricPrivacy.logException(e)
        }
    }

    private fun showPinSetupDialog() {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle(rh.gs(R.string.remote_control_title))
                .setMessage(rh.gs(R.string.remote_pin_setup_required))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            pinSetupCard?.visibility = View.VISIBLE
        }
    }
    
    /**
     * Show access password login dialog.
     */
    private fun showAccessLoginDialog() {
        val dialog = RemoteAccessLoginDialog()
        dialog.onUnlocked = {
            // Re-initialize UI after successful unlock
            aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] UI initialization after unlock")
            initializeUI()
        }
        dialog.show(childFragmentManager, "access_login")
        
        aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Login dialog shown")
    }
    
    /**
     * Show password setup dialog (first time configuration).
     */
    private fun showPasswordSetupDialog() {
        context?.let { ctx ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_password_setup, null)
            val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.setup_password_input)
            val confirmInput = dialogView.findViewById<TextInputEditText>(R.id.setup_confirm_input)
            
            MaterialAlertDialogBuilder(ctx)
                .setTitle(rh.gs(R.string.configure_access_password))
                .setMessage(rh.gs(R.string.access_password_setup_message))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val password = passwordInput?.text?.toString() ?: ""
                    val confirm = confirmInput?.text?.toString() ?: ""
                    
                    when {
                        password.isEmpty() -> {
                            showError(rh.gs(R.string.password_required))
                            showPasswordSetupDialog() // Show again
                        }
                        password.length < 6 -> {
                            showError(rh.gs(R.string.password_min_length))
                            showPasswordSetupDialog()
                        }
                        password != confirm -> {
                            showError("Passwords do not match")
                            showPasswordSetupDialog()
                        }
                        else -> {
                            try {
                                accessAuthManager.configurePassword(password)
                                showSuccess(rh.gs(R.string.remote_access_password_configured))
                                aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Password configured")
                                
                                // Show password to admin
                                showPasswordToAdmin(password)
                                
                                // Now show login dialog
                                showAccessLoginDialog()
                            } catch (e: Exception) {
                                showError("Error: ${e.message}")
                                aapsLogger.error(LTag.NSCLIENT, "[RemoteAccess] Error configuring password", e)
                            }
                        }
                    }
                }
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * Show configured password to admin (once, to share with clients).
     */
    private fun showPasswordToAdmin(password: String) {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle("⚠️ Share This Password")
                .setMessage("Access Password: $password\n\nShare this password securely with authorized users.\n\nThis will only be shown once!")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
    
    /**
     * Show license key dialog for premium activation.
     */
    private fun showLicenseKeyDialog() {
        val dialog = LicenseKeyDialog()
        dialog.onLicenseActivated = {
            // License activated successfully
            aapsLogger.info(LTag.NSCLIENT, "[License] License activated, proceeding to password setup")
            
            // Show success message
            context?.let { ctx ->
                Toast.makeText(ctx, rh.gs(R.string.license_activated), Toast.LENGTH_LONG).show()
            }
            
            // Now check password configuration
            if (!accessAuthManager.isPasswordConfigured()) {
                showPasswordSetupDialog()
            } else if (!accessAuthManager.isUnlocked()) {
                showAccessLoginDialog()
            } else {
                initializeUI()
            }
        }
        dialog.show(childFragmentManager, "license_key")
        
        aapsLogger.info(LTag.NSCLIENT, "[License] License key dialog shown")
    }

    private fun setupPinInput() {
        val pinConfigured = !pinManager.needsConfiguration()
        if (pinConfigured) {
            pinInput?.hint = rh.gs(R.string.enter_your_pin)
        }

        pinInput?.addTextChangedListener {
            // PIN validation happens on mode send
        }

        // PIN Setup Card
        btnSavePin?.setOnClickListener {
            val newPin = newPinInput?.text?.toString() ?: ""
            val confirmPin = confirmPinInput?.text?.toString() ?: ""

            when {
                newPin.isEmpty() -> showError(rh.gs(R.string.pin_cannot_be_empty))
                newPin.length < 4 -> showError(rh.gs(R.string.pin_too_short))
                newPin != confirmPin -> showError(rh.gs(R.string.pins_do_not_match))
                else -> {
                    pinManager.savePin(newPin)
                    showSuccess(rh.gs(R.string.pin_saved_successfully))
                    pinSetupCard?.visibility = View.GONE
                    pinInput?.hint = rh.gs(R.string.enter_your_pin)
                }
            }
        }
    }

    private fun setupTabs() {
        val vp = viewPager ?: return
        val tl = tabLayout ?: return

        // Create adapters
        val modesAdapter = ModesAdapter { mode, duration ->
            sendModeCommand(mode, duration)
        }
        
        val contextsAdapter = ModesAdapter { mode, duration ->
            sendModeCommand(mode, duration)
        }
        
        activeModesAdapter = ActiveModesAdapter()
        
        // Create ViewPager adapter
        val pagerAdapter = RemoteControlPagerAdapter(
            modesAdapter = modesAdapter,
            contextsAdapter = contextsAdapter,
            activeAdapter = activeModesAdapter,
            onRefreshActive = { refreshActiveModes() },
            onSendLLM = { text -> sendLLMContext(text) }
        )
        
        // Setup ViewPager2
        vp.adapter = pagerAdapter
        
        // Link TabLayout with ViewPager2
        TabLayoutMediator(tl, vp) { tab, position ->
            tab.text = when (position) {
                0 -> rh.gs(R.string.tab_modes)
                1 -> rh.gs(R.string.tab_contexts)
                2 -> rh.gs(R.string.tab_active)
                3 -> rh.gs(R.string.tab_llm)
                else -> ""
            }
        }.attach()
    }

    /**
     * Send mode command with DUAL FORMAT:
     * 1. AIMI command for audit trail
     * 2. CarePortal-compatible note for Therapy.kt execution
     */
    private fun sendModeCommand(mode: ModePreset, durationMin: Int) {
        val pin = pinInput?.text?.toString() ?: ""

        // Validate PIN
        if (!pinManager.validatePin(pin)) {
            showError(rh.gs(R.string.invalid_pin))
            return
        }

        // Confirmation dialog for destructive actions
        if (mode.id == "stop") {
            MaterialAlertDialogBuilder(requireActivity())
                .setMessage("Annuler tous les modes actifs ?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    executeSendMode(mode, durationMin, pin)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        executeSendMode(mode, durationMin, pin)
    }

    /**
     * Execute sending mode - routes based on category:
     * - MEAL/CONTROL → TherapyEvent (existing system)
     * - ACTIVITY/PHYSIO → ContextManager (AIMI contexts)
     */
    private fun executeSendMode(mode: ModePreset, durationMin: Int, pin: String) {
        when (mode.category) {
            ModeCategory.MEAL, ModeCategory.CONTROL -> {
                sendTherapyEventMode(mode, durationMin, pin)
            }
            
            ModeCategory.ACTIVITY, ModeCategory.CONTEXT_ONLY, ModeCategory.PHYSIO -> {
                sendContextMode(mode, durationMin)
            }
        }
    }
    
    /**
     * Send MEAL/CONTROL mode via TherapyEvent (keeps existing behavior).
     */
    private fun sendTherapyEventMode(mode: ModePreset, durationMin: Int, pin: String) {
        val now = dateUtil.now()
        val durationMs = TimeUnit.MINUTES.toMillis(durationMin.toLong())

        val aimiCommand = "AIMI:$pin mode ${mode.therapyKeyword} $durationMin"
        val aimiNote = TE(
            timestamp = now,
            type = TE.Type.NOTE,
            glucoseUnit = GlucoseUnit.MGDL,
            note = aimiCommand,
            duration = durationMs
        )

        val carePortalNote = if (mode.id == "stop") {
            "stop"
        } else {
            "${mode.therapyKeyword} $durationMin"
        }
        
        val carePortalTE = TE(
            timestamp = now + 1000,
            type = TE.Type.NOTE,
            glucoseUnit = GlucoseUnit.MGDL,
            note = carePortalNote,
            duration = durationMs
        )

        aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Sending MEAL/CONTROL via TherapyEvent: '$aimiCommand'")
        
        lifecycleScope.launch {
            try {
                persistenceLayer.insertOrUpdateTherapyEvent(aimiNote)
                sendCarePortalNote(carePortalTE, mode, durationMin)
            } catch (error: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "Failed to send AIMI command", error)
                showError("Erreur: ${error.message}")
            }
        }
    }
    
    /**
     * Send ACTIVITY/PHYSIO mode via ContextManager (AIMI contexts).
     */
    private fun sendContextMode(mode: ModePreset, durationMin: Int) {
        lifecycleScope.launch {
            try {
                val intentText = "${mode.therapyKeyword} ${durationMin}min"
                
                aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Sending ACTIVITY/PHYSIO via ContextManager: '$intentText'")
                
                val ids = contextManager.addIntent(intentText, forceLLM = false)
                
                if (ids.isNotEmpty()) {
                    aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Context created: $ids")
                    showSuccess("✅ ${mode.displayName} activé ($durationMin min)")
                    refreshActiveModes()
                } else {
                    aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] ContextManager returned no IDs for: '$intentText'")
                    showError("Échec: Context non reconnu par parser")
                }
                
            } catch (e: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] Failed to send Context", e)
                showError("Erreur context: ${e.message}")
            }
        }
    }

    private fun sendCarePortalNote(note: TE, mode: ModePreset, durationMin: Int) {
        lifecycleScope.launch {
            try {
                persistenceLayer.insertOrUpdateTherapyEvent(note)
                logCommandSent(mode, durationMin)
                showSuccess("✅ ${mode.displayName} activé ($durationMin min)")
                refreshActiveModes()
            } catch (error: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "Failed to send CarePortal note", error)
                showError("Erreur: ${error.message}")
            }
        }
    }

    /**
     * Refresh active modes from TherapyEvents.
     */
    private fun refreshActiveModes() {
        if (view == null) return
        
        val fromTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        
        lifecycleScope.launch {
            try {
                val events = persistenceLayer.getTherapyEventDataFromTime(fromTime, true)
                if (view != null) {
                    val activeModes = parseActiveModes(events)
                    activeModesAdapter.updateModes(activeModes)
                }
            } catch (error: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "Failed to refresh active modes", error)
            }
        }
    }

    /**
     * Parse TherapyEvents to extract active modes.
     * Detects both simple keywords ("lunch 60") and AIMI format ("AIMI:PIN CONTEXT SPORT 60").
     */
    private fun parseActiveModes(events: List<TE>): List<ActiveModeItem> {
        val activeModes = mutableListOf<ActiveModeItem>()
        val now = System.currentTimeMillis()

        // Regex to parse AIMI commands: "AIMI:1234 CONTEXT SPORT 60" or "AIMI:1234 mode lunch 60"
        val aimiRegex = Regex("AIMI:\\d+\\s+(CONTEXT|mode)\\s+(\\w+)(?:\\s+(\\d+))?", RegexOption.IGNORE_CASE)

        events.filter { it.type == TE.Type.NOTE }
            .forEach { event ->
                val note = event.note ?: return@forEach
                val expiresAt = event.timestamp + event.duration
                
                // Skip expired events
                if (expiresAt <= now) return@forEach
                
                // TRY 1: Parse AIMI format first (higher priority)
                val aimiMatch = aimiRegex.find(note)
                if (aimiMatch != null) {
                    val commandType = aimiMatch.groupValues[1] // "CONTEXT" or "mode"
                    val contextName = aimiMatch.groupValues[2] // "SPORT", "MEAL", "lunch", etc.
                    
                    aapsLogger.debug(LTag.NSCLIENT, "[RemoteControl] Parsed AIMI $commandType: $contextName")
                    
                    // Map CONTEXT names to therapy keywords
                    val therapyKeyword = when (contextName.uppercase()) {
                        "SPORT" -> "sport"
                        "MEAL" -> "lunch" // Default MEAL to lunch
                        "STRESS" -> "stress"
                        "CANCEL" -> "stop"
                        else -> contextName.lowercase() // Use as-is (lunch, dinner, etc.)
                    }
                    
                    // Find matching mode preset
                    val mode = ModePresets.findByKeyword(therapyKeyword)
                    if (mode != null) {
                        activeModes.add(
                            ActiveModeItem(
                                mode = mode,
                                startedAt = event.timestamp,
                                durationMs = event.duration
                            )
                        )
                        aapsLogger.debug(LTag.NSCLIENT, "[RemoteControl] Parsed AIMI command: $contextName -> ${mode.displayName}")
                        return@forEach // Matched, skip fallback
                    }
                }
                
                // TRY 2: Fallback to simple keyword matching
                ModePresets.ALL_MODES.forEach { mode ->
                    if (note.contains(mode.therapyKeyword, ignoreCase = true)) {
                        activeModes.add(
                            ActiveModeItem(
                                mode = mode,
                                startedAt = event.timestamp,
                                durationMs = event.duration
                            )
                        )
                        aapsLogger.debug(LTag.NSCLIENT, "[RemoteControl] Parsed keyword: ${mode.therapyKeyword} -> ${mode.displayName}")
                        return@forEach // Matched one mode, skip others
                    }
                }
            }

        // Remove duplicates (keep most recent per mode)
        return activeModes
            .groupBy { it.mode.id }
            .mapNotNull { (_, items) -> items.maxByOrNull { it.startedAt } }
            .filterNot { it.isExpired }  // Filter out expired modes
            .sortedByDescending { it.startedAt }
    }

    private fun logCommandSent(mode: ModePreset, durationMin: Int) {
        nsClientRepository.addLog("► REMOTE", "${mode.displayName} activé ($durationMin min)")
    }

    private fun showSuccess(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Send LLM text for intelligent context generation.
     * Uses ContextManager with forceLLM=true for AI parsing.
     */
    private fun sendLLMContext(text: String) {
        if (text.isBlank()) {
            showError(rh.gs(R.string.command_cannot_be_empty))
            return
        }
        
        lifecycleScope.launch {
            try {
                aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Sending LLM text: '$text'")
                
                // Call ContextManager with forceLLM=true for AI parsing
                val ids = contextManager.addIntent(text, forceLLM = true)
                
                if (ids.isNotEmpty()) {
                    aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] LLM context created: $ids")
                    showSuccess(rh.gs(R.string.llm_status_success))
                    
                    // Refresh to show new context
                    refreshActiveModes()
                } else {
                    aapsLogger.warn(LTag.NSCLIENT, "[RemoteControl] LLM returned no contexts for: '$text'")
                    showError("Aucun context détecté. Essayez une description plus précise.")
                }
                
            } catch (e: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] LLM context generation failed", e)
                showError(rh.gs(R.string.llm_status_error, e.message ?: "Unknown error"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refreshHandler.removeCallbacks(refreshRunnable)
        pinSetupCard = null
        newPinInput = null
        confirmPinInput = null
        btnSavePin = null
        pinInput = null
        tabLayout = null
        viewPager = null
    }

    override fun onResume() {
        super.onResume()
        // Refresh immediately on resume
        refreshActiveModes()
    }
}
