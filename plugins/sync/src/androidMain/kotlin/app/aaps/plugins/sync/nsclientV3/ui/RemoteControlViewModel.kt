package app.aaps.plugins.sync.nsclientV3.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.openAPSAIMI.context.ContextManager
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.ActiveModeItem
import app.aaps.plugins.sync.nsclientV3.LicenseKeyValidator
import app.aaps.plugins.sync.nsclientV3.ModeCategory
import app.aaps.plugins.sync.nsclientV3.ModePreset
import app.aaps.plugins.sync.nsclientV3.ModePresets
import app.aaps.plugins.sync.nsclientV3.NSClientPinManager
import app.aaps.plugins.sync.nsclientV3.RemoteAccessAuthManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Immutable
data class RemoteControlUiState(
    val isLicensed: Boolean = false,
    val isPasswordConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val pinInput: String = "",
    val selectedTab: Int = 0,
    val activeModes: List<ActiveModeItem> = emptyList(),
    val customDurations: Map<String, Int> = emptyMap(),
    val llmText: String = "",
    val llmIsLoading: Boolean = false,
    val statusMessage: String? = null,
    val showLicenseDialog: Boolean = false,
    val showPasswordSetupDialog: Boolean = false,
    val showPasswordLoginDialog: Boolean = false,
    val showPinSetupDialog: Boolean = false,
    val sharePasswordPrompt: String? = null,
    val showConfirmStopDialog: Boolean = false
)

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey(RemoteControlViewModel::class)
@Inject
class RemoteControlViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val pinManager: NSClientPinManager,
    private val contextManager: ContextManager,
    private val accessAuthManager: RemoteAccessAuthManager,
    private val licenseValidator: LicenseKeyValidator,
    private val nsClientRepository: NSClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    init {
        checkSecurityState()
        startActiveModesPolling()
    }

    private fun checkSecurityState() {
        val isLicensed = licenseValidator.isLicenseActivated()
        val isPasswordConfigured = accessAuthManager.isPasswordConfigured()
        val isUnlocked = accessAuthManager.isUnlocked()
        val isPinConfigured = !pinManager.needsConfiguration()

        _uiState.update {
            it.copy(
                isLicensed = isLicensed,
                isPasswordConfigured = isPasswordConfigured,
                isUnlocked = isUnlocked,
                isPinConfigured = isPinConfigured,
                showLicenseDialog = !isLicensed,
                showPasswordSetupDialog = isLicensed && !isPasswordConfigured,
                showPasswordLoginDialog = isLicensed && isPasswordConfigured && !isUnlocked,
                showPinSetupDialog = isLicensed && isPasswordConfigured && isUnlocked && !isPinConfigured
            )
        }

        if (isLicensed && isUnlocked) {
            refreshActiveModes()
        }
    }

    private fun startActiveModesPolling() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isUnlocked) {
                    refreshActiveModes()
                }
                delay(30_000)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onPinChanged(pin: String) {
        _uiState.update { it.copy(pinInput = pin) }
    }

    fun onLlmTextChanged(text: String) {
        _uiState.update { it.copy(llmText = text) }
    }

    fun setCustomDuration(modeId: String, duration: Int) {
        _uiState.update {
            val updated = it.customDurations.toMutableMap()
            updated[modeId] = duration
            it.copy(customDurations = updated)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Authentication & License ---

    fun activateLicense(key: String): Boolean {
        if (key.isBlank()) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.license_key_required)) }
            return false
        }
        val valid = licenseValidator.validateLicenseKey(key)
        if (valid) {
            aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] License activated successfully")
            val isPasswordConfigured = accessAuthManager.isPasswordConfigured()
            val isUnlocked = accessAuthManager.isUnlocked()
            _uiState.update {
                it.copy(
                    isLicensed = true,
                    showLicenseDialog = false,
                    showPasswordSetupDialog = !isPasswordConfigured,
                    showPasswordLoginDialog = isPasswordConfigured && !isUnlocked,
                    statusMessage = rh.gs(R.string.license_activated)
                )
            }
            return true
        } else {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.license_key_invalid)) }
            return false
        }
    }

    fun configurePassword(password: String, confirm: String): Boolean {
        if (password.isBlank()) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.password_required)) }
            return false
        }
        if (password.length < 6) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.password_min_length)) }
            return false
        }
        if (password != confirm) {
            _uiState.update { it.copy(statusMessage = "Passwords do not match") }
            return false
        }
        try {
            accessAuthManager.configurePassword(password)
            aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Access password configured")
            _uiState.update {
                it.copy(
                    isPasswordConfigured = true,
                    showPasswordSetupDialog = false,
                    sharePasswordPrompt = password,
                    statusMessage = rh.gs(R.string.remote_access_password_configured)
                )
            }
            return true
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] Error configuring password", e)
            _uiState.update { it.copy(statusMessage = "Error: ${e.message}") }
            return false
        }
    }

    fun dismissSharePasswordPrompt() {
        _uiState.update {
            it.copy(
                sharePasswordPrompt = null,
                showPasswordLoginDialog = true
            )
        }
    }

    fun unlockWithPassword(password: String): Boolean {
        if (password.isBlank()) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.password_required)) }
            return false
        }
        val valid = accessAuthManager.verifyPassword(password)
        if (valid) {
            aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Access granted via password")
            val isPinConfigured = !pinManager.needsConfiguration()
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    showPasswordLoginDialog = false,
                    showPinSetupDialog = !isPinConfigured
                )
            }
            refreshActiveModes()
            return true
        } else {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.remote_access_invalid_password)) }
            return false
        }
    }

    fun saveNewPin(newPin: String, confirmPin: String): Boolean {
        if (newPin.isEmpty()) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.pin_cannot_be_empty)) }
            return false
        }
        if (newPin.length < 4) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.pin_too_short)) }
            return false
        }
        if (newPin != confirmPin) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.pins_do_not_match)) }
            return false
        }
        pinManager.savePin(newPin)
        aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] PIN configured successfully")
        _uiState.update {
            it.copy(
                isPinConfigured = true,
                showPinSetupDialog = false,
                statusMessage = rh.gs(R.string.pin_saved_successfully)
            )
        }
        return true
    }

    fun showPinSetupDialog(show: Boolean) {
        _uiState.update { it.copy(showPinSetupDialog = show) }
    }

    fun showConfirmStopDialog(show: Boolean) {
        _uiState.update { it.copy(showConfirmStopDialog = show) }
    }

    // --- Modes & Execution ---

    fun onModeClick(mode: ModePreset, durationMin: Int) {
        val pin = _uiState.value.pinInput
        if (!pinManager.validatePin(pin)) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.invalid_pin)) }
            return
        }

        if (mode.id == "stop") {
            _uiState.update { it.copy(showConfirmStopDialog = true) }
            return
        }

        executeSendMode(mode, durationMin, pin)
    }

    fun confirmCancelAll() {
        val stopMode = ModePresets.ALL_MODES.find { it.id == "stop" }
        _uiState.update { it.copy(showConfirmStopDialog = false) }
        if (stopMode != null) {
            executeSendMode(stopMode, 0, _uiState.value.pinInput)
        }
    }

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

        viewModelScope.launch {
            try {
                persistenceLayer.insertOrUpdateTherapyEvent(aimiNote)
                persistenceLayer.insertOrUpdateTherapyEvent(carePortalTE)
                nsClientRepository.addLog("► REMOTE", "${mode.displayName} activé ($durationMin min)")
                _uiState.update { it.copy(statusMessage = "✅ ${mode.displayName} activé ($durationMin min)") }
                refreshActiveModes()
            } catch (error: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "Failed to send AIMI command", error)
                _uiState.update { it.copy(statusMessage = "Erreur: ${error.message}") }
            }
        }
    }

    private fun sendContextMode(mode: ModePreset, durationMin: Int) {
        viewModelScope.launch {
            try {
                val intentText = "${mode.therapyKeyword} ${durationMin}min"
                aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Sending ACTIVITY/PHYSIO via ContextManager: '$intentText'")

                val ids = contextManager.addIntent(intentText, forceLLM = false)
                if (ids.isNotEmpty()) {
                    aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Context created: $ids")
                    _uiState.update { it.copy(statusMessage = "✅ ${mode.displayName} activé ($durationMin min)") }
                    refreshActiveModes()
                } else {
                    aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] ContextManager returned no IDs for: '$intentText'")
                    _uiState.update { it.copy(statusMessage = "Échec: Context non reconnu par parser") }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] Failed to send Context", e)
                _uiState.update { it.copy(statusMessage = "Erreur context: ${e.message}") }
            }
        }
    }

    fun refreshActiveModes() {
        val fromTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

        viewModelScope.launch {
            try {
                val events = persistenceLayer.getTherapyEventDataFromTime(fromTime, true)
                val activeModes = parseActiveModes(events)
                _uiState.update { it.copy(activeModes = activeModes) }
            } catch (error: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "Failed to refresh active modes", error)
            }
        }
    }

    private fun parseActiveModes(events: List<TE>): List<ActiveModeItem> {
        val activeModes = mutableListOf<ActiveModeItem>()
        val now = System.currentTimeMillis()
        val aimiRegex = Regex("AIMI:\\d+\\s+(CONTEXT|mode)\\s+(\\w+)(?:\\s+(\\d+))?", RegexOption.IGNORE_CASE)

        events.filter { it.type == TE.Type.NOTE }
            .forEach { event ->
                val note = event.note ?: return@forEach
                val expiresAt = event.timestamp + event.duration
                if (expiresAt <= now) return@forEach

                val aimiMatch = aimiRegex.find(note)
                if (aimiMatch != null) {
                    val commandType = aimiMatch.groupValues[1]
                    val contextName = aimiMatch.groupValues[2]

                    val therapyKeyword = when (contextName.uppercase()) {
                        "SPORT" -> "sport"
                        "MEAL" -> "lunch"
                        "STRESS" -> "stress"
                        "CANCEL" -> "stop"
                        else -> contextName.lowercase()
                    }

                    val mode = ModePresets.findByKeyword(therapyKeyword)
                    if (mode != null) {
                        activeModes.add(
                            ActiveModeItem(
                                mode = mode,
                                startedAt = event.timestamp,
                                durationMs = event.duration
                            )
                        )
                        return@forEach
                    }
                }

                ModePresets.ALL_MODES.forEach { mode ->
                    if (note.contains(mode.therapyKeyword, ignoreCase = true)) {
                        activeModes.add(
                            ActiveModeItem(
                                mode = mode,
                                startedAt = event.timestamp,
                                durationMs = event.duration
                            )
                        )
                        return@forEach
                    }
                }
            }

        return activeModes
            .groupBy { it.mode.id }
            .mapNotNull { (_, items) -> items.maxByOrNull { it.startedAt } }
            .filterNot { it.isExpired }
            .sortedByDescending { it.startedAt }
    }

    fun sendLlmContext() {
        val text = _uiState.value.llmText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(statusMessage = rh.gs(R.string.command_cannot_be_empty)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(llmIsLoading = true) }
            try {
                aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] Sending LLM text: '$text'")
                val ids = contextManager.addIntent(text, forceLLM = true)
                if (ids.isNotEmpty()) {
                    aapsLogger.info(LTag.NSCLIENT, "[RemoteControl] LLM context created: $ids")
                    _uiState.update {
                        it.copy(
                            llmText = "",
                            llmIsLoading = false,
                            statusMessage = rh.gs(R.string.llm_status_success)
                        )
                    }
                    refreshActiveModes()
                } else {
                    aapsLogger.warn(LTag.NSCLIENT, "[RemoteControl] LLM returned no contexts for: '$text'")
                    _uiState.update {
                        it.copy(
                            llmIsLoading = false,
                            statusMessage = "Aucun context détecté. Essayez une description plus précise."
                        )
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.NSCLIENT, "[RemoteControl] LLM context generation failed", e)
                _uiState.update {
                    it.copy(
                        llmIsLoading = false,
                        statusMessage = rh.gs(R.string.llm_status_error, e.message ?: "Unknown error")
                    )
                }
            }
        }
    }
}
