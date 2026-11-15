package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent
import app.aaps.plugins.aps.openAPSAIMI.context.ContextManager
import app.aaps.plugins.aps.openAPSAIMI.context.ContextPreset
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class ContextActivity : MetroAppCompatActivity() {

    @Inject lateinit var contextManager: ContextManager
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    private val isContextEnabled = mutableStateOf(false)
    private val isLLMEnabled = mutableStateOf(false)
    private val inputText = mutableStateOf("")
    private val isParsing = mutableStateOf(false)
    private val activeIntents = mutableStateOf<List<Pair<String, ContextIntent>>>(emptyList())

    private val showExtendDialog = mutableStateOf(false)
    private val extendingIntentId = mutableStateOf<String?>(null)
    private val showClearAllDialog = mutableStateOf(false)
    private val parseFailureMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshState()

        setContent {
            AapsTheme {
                ContextScreen(
                    isContextEnabled = isContextEnabled.value,
                    onContextEnabledChange = { enabled ->
                        isContextEnabled.value = enabled
                        sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, enabled)
                    },
                    isLLMEnabled = isLLMEnabled.value,
                    onLLMEnabledChange = { enabled ->
                        isLLMEnabled.value = enabled
                        sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, enabled)
                    },
                    inputText = inputText.value,
                    onInputTextChange = { inputText.value = it },
                    isParsing = isParsing.value,
                    onSendClick = { sendContextChat() },
                    onClearInputClick = { inputText.value = "" },
                    presets = ContextPreset.ALL_PRESETS,
                    onPresetClick = { addPreset(it) },
                    activeIntents = activeIntents.value,
                    onRemoveIntent = { id ->
                        contextManager.removeIntent(id)
                        refreshState()
                    },
                    onExtendIntent = { id ->
                        extendingIntentId.value = id
                        showExtendDialog.value = true
                    },
                    onClearAllClick = { showClearAllDialog.value = true },
                    onBackClick = { finish() },
                    formatTimeRemaining = { getTimeRemaining(it) },
                    formatDisplayString = { getDisplayString(it) },
                    showExtendDialog = showExtendDialog.value,
                    onSelectExtendMinutes = { mins ->
                        extendingIntentId.value?.let { id ->
                            contextManager.extendDuration(id, mins.minutes)
                            refreshState()
                        }
                        showExtendDialog.value = false
                        extendingIntentId.value = null
                    },
                    onDismissExtendDialog = {
                        showExtendDialog.value = false
                        extendingIntentId.value = null
                    },
                    showClearAllDialog = showClearAllDialog.value,
                    onConfirmClearAll = {
                        contextManager.clearAll()
                        showClearAllDialog.value = false
                        refreshState()
                    },
                    onDismissClearAllDialog = { showClearAllDialog.value = false },
                    parseFailureMessage = parseFailureMessage.value,
                    onDismissParseFailureDialog = { parseFailureMessage.value = null }
                )
            }
        }
    }

    private fun refreshState() {
        activeIntents.value = contextManager.getAllIntents().toList()
        isContextEnabled.value = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, false)
        isLLMEnabled.value = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
    }

    private fun sendContextChat() {
        val text = inputText.value.trim()
        if (text.isBlank()) return

        lifecycleScope.launch {
            isParsing.value = true
            try {
                val ids = contextManager.addIntent(text)
                if (ids.isNotEmpty()) {
                    inputText.value = ""
                    Toast.makeText(this@ContextActivity, "${ids.size} context(s) added", Toast.LENGTH_SHORT).show()
                } else {
                    val isLLM = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
                    val provider = sp.getString(app.aaps.core.keys.StringKey.AimiAdvisorProvider.key, "GEMINI")
                    val llmErr = contextManager.getLastLLMError()

                    parseFailureMessage.value = if (isLLM) {
                        if (!llmErr.isNullOrBlank()) {
                            "AI Context Error ($provider):\n$llmErr\n\nPlease check your API key, network connection or custom endpoint URL in Preferences > AIMI > Advisor."
                        } else {
                            "No context detected via AI ($provider).\n\nPossible causes:\n1. Missing or invalid API key (Preferences > AIMI > Advisor)\n2. Network timeout\n3. Description too vague\n\nFallback: Try simple keywords (e.g. 'Sport 1h', 'Sick')."
                        }
                    } else {
                        "No context detected via keywords.\nTry simple commands:\n- 'Cardio 1h'\n- 'Sick'\n- 'Stress'\n- 'Heavy meal'"
                    }
                }
                refreshState()
            } catch (e: Exception) {
                Toast.makeText(this@ContextActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isParsing.value = false
            }
        }
    }

    private fun addPreset(preset: ContextPreset) {
        lifecycleScope.launch {
            try {
                contextManager.addPreset(preset)
                Toast.makeText(this@ContextActivity, "Context added: ${preset.displayName}", Toast.LENGTH_SHORT).show()
                refreshState()
            } catch (e: Exception) {
                Toast.makeText(this@ContextActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getTimeRemaining(intent: ContextIntent): String {
        val now = System.currentTimeMillis()
        val remaining = (intent.endTimeMs - now) / 1000 / 60
        return when {
            remaining <= 0 -> "Expired"
            remaining < 60 -> "${remaining}m remaining"
            else -> "${remaining / 60}h ${remaining % 60}m"
        }
    }

    private fun getDisplayString(intent: ContextIntent): String {
        return when (intent) {
            is ContextIntent.Activity ->
                "🏃 Activity: ${intent.activityType.name} ${intent.intensity.name}"
            is ContextIntent.Illness ->
                "🤒 Illness: ${intent.symptomType.name} ${intent.intensity.name}"
            is ContextIntent.Stress ->
                "😰 Stress: ${intent.stressType.name} ${intent.intensity.name}"
            is ContextIntent.UnannouncedMealRisk ->
                "🍕 Meal Risk: ${intent.intensity.name}"
            is ContextIntent.Alcohol ->
                "🍷 Alcohol: ${intent.units}U ${intent.intensity.name}"
            is ContextIntent.Travel ->
                "✈️ Travel: ${intent.intensity.name}"
            is ContextIntent.MenstrualCycle ->
                "🔄 Cycle: ${intent.phase.name}"
            is ContextIntent.Custom ->
                "📝 ${intent.description}"
        }
    }
}
