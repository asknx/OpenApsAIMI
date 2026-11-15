package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.ui.AimiModeSettingsScreen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AimiModeSettingsActivity : MetroAppCompatActivity() {

    @Inject lateinit var automation: Automation
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var persistenceLayer: app.aaps.core.interfaces.db.PersistenceLayer
    private val sp by lazy { androidx.preference.PreferenceManager.getDefaultSharedPreferences(this) }

    enum class ModeType { LUNCH, DINNER, BFAST, HIGHCARB }

    private val selectedMode = mutableStateOf(ModeType.LUNCH)
    private val prebolus1Text = mutableStateOf("")
    private val prebolus2Text = mutableStateOf("")
    private val reactivityText = mutableStateOf("")
    private val durationText = mutableStateOf("")
    private val intervalText = mutableStateOf("")

    private val showActivateDialog = mutableStateOf(false)
    private val activateDialogTitle = mutableStateOf("")
    private val activateDialogMessage = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadValues(ModeType.LUNCH)

        setContent {
            AapsTheme {
                AimiModeSettingsScreen(
                    selectedMode = selectedMode.value,
                    onSelectMode = { switchMode(it) },
                    prebolus1Text = prebolus1Text.value,
                    onPrebolus1Change = { prebolus1Text.value = it },
                    prebolus2Text = prebolus2Text.value,
                    onPrebolus2Change = { prebolus2Text.value = it },
                    reactivityText = reactivityText.value,
                    onReactivityChange = { reactivityText.value = it },
                    durationText = durationText.value,
                    onDurationChange = { durationText.value = it },
                    intervalText = intervalText.value,
                    onIntervalChange = { intervalText.value = it },
                    onSaveClick = { saveValues(finishAfterSave = true) },
                    onActivateClick = { promptActivateMode() },
                    onBackClick = { finish() },
                    showActivateDialog = showActivateDialog.value,
                    activateDialogTitle = activateDialogTitle.value,
                    activateDialogMessage = activateDialogMessage.value,
                    onConfirmActivate = { confirmActivateMode() },
                    onDismissActivateDialog = { showActivateDialog.value = false }
                )
            }
        }
    }

    private fun switchMode(mode: ModeType) {
        if (selectedMode.value == mode) return
        saveValues(finishAfterSave = false)
        selectedMode.value = mode
        loadValues(mode)
    }

    private fun loadValues(mode: ModeType) {
        when (mode) {
            ModeType.LUNCH -> {
                prebolus1Text.value = getStringPref(DoubleKey.OApsAIMILunchPrebolus)
                prebolus2Text.value = getStringPref(DoubleKey.OApsAIMILunchPrebolus2)
                reactivityText.value = getStringPref(DoubleKey.OApsAIMILunchFactor)
                durationText.value = sp.getInt("aimi_mode_lunch_duration", 60).toString()
                intervalText.value = getStringPref(IntKey.OApsAIMILunchinterval)
            }
            ModeType.DINNER -> {
                prebolus1Text.value = getStringPref(DoubleKey.OApsAIMIDinnerPrebolus)
                prebolus2Text.value = getStringPref(DoubleKey.OApsAIMIDinnerPrebolus2)
                reactivityText.value = getStringPref(DoubleKey.OApsAIMIDinnerFactor)
                durationText.value = sp.getInt("aimi_mode_dinner_duration", 60).toString()
                intervalText.value = getStringPref(IntKey.OApsAIMIDinnerinterval)
            }
            ModeType.BFAST -> {
                prebolus1Text.value = getStringPref(DoubleKey.OApsAIMIBFPrebolus)
                prebolus2Text.value = getStringPref(DoubleKey.OApsAIMIBFPrebolus2)
                reactivityText.value = getStringPref(DoubleKey.OApsAIMIBFFactor)
                durationText.value = sp.getInt("aimi_mode_bfast_duration", 60).toString()
                intervalText.value = getStringPref(IntKey.OApsAIMIBFinterval)
            }
            ModeType.HIGHCARB -> {
                prebolus1Text.value = getStringPref(DoubleKey.OApsAIMIHighCarbPrebolus)
                prebolus2Text.value = getStringPref(DoubleKey.OApsAIMIHighCarbPrebolus2)
                reactivityText.value = getStringPref(DoubleKey.OApsAIMIHCFactor)
                durationText.value = sp.getInt("aimi_mode_hc_duration", 60).toString()
                intervalText.value = getStringPref(IntKey.OApsAIMIHCinterval)
            }
        }
    }

    private fun saveValues(finishAfterSave: Boolean) {
        val p1 = prebolus1Text.value.toDoubleOrNull() ?: 0.0
        val p2 = prebolus2Text.value.toDoubleOrNull() ?: 0.0
        val react = reactivityText.value.toDoubleOrNull() ?: 100.0
        val dur = durationText.value.toIntOrNull() ?: 60
        val interv = intervalText.value.toIntOrNull() ?: 5

        when (selectedMode.value) {
            ModeType.LUNCH -> {
                preferences.put(DoubleKey.OApsAIMILunchPrebolus, p1)
                preferences.put(DoubleKey.OApsAIMILunchPrebolus2, p2)
                preferences.put(DoubleKey.OApsAIMILunchFactor, react)
                sp.edit().putInt("aimi_mode_lunch_duration", dur).apply()
                preferences.put(IntKey.OApsAIMILunchinterval, interv)
            }
            ModeType.DINNER -> {
                preferences.put(DoubleKey.OApsAIMIDinnerPrebolus, p1)
                preferences.put(DoubleKey.OApsAIMIDinnerPrebolus2, p2)
                preferences.put(DoubleKey.OApsAIMIDinnerFactor, react)
                sp.edit().putInt("aimi_mode_dinner_duration", dur).apply()
                preferences.put(IntKey.OApsAIMIDinnerinterval, interv)
            }
            ModeType.BFAST -> {
                preferences.put(DoubleKey.OApsAIMIBFPrebolus, p1)
                preferences.put(DoubleKey.OApsAIMIBFPrebolus2, p2)
                preferences.put(DoubleKey.OApsAIMIBFFactor, react)
                sp.edit().putInt("aimi_mode_bfast_duration", dur).apply()
                preferences.put(IntKey.OApsAIMIBFinterval, interv)
            }
            ModeType.HIGHCARB -> {
                preferences.put(DoubleKey.OApsAIMIHighCarbPrebolus, p1)
                preferences.put(DoubleKey.OApsAIMIHighCarbPrebolus2, p2)
                preferences.put(DoubleKey.OApsAIMIHCFactor, react)
                sp.edit().putInt("aimi_mode_hc_duration", dur).apply()
                preferences.put(IntKey.OApsAIMIHCinterval, interv)
            }
        }

        if (finishAfterSave) {
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun promptActivateMode() {
        saveValues(finishAfterSave = false)

        val modeNote = when (selectedMode.value) {
            ModeType.LUNCH -> "Lunch"
            ModeType.DINNER -> "Dinner"
            ModeType.BFAST -> "Breakfast"
            ModeType.HIGHCARB -> "High Carb"
        }

        val durationMin = durationText.value.toIntOrNull() ?: 60
        activateDialogTitle.value = "Activate $modeNote mode?"
        activateDialogMessage.value = "This will create a Note '$modeNote' ($durationMin min) to trigger AIMI logic."
        showActivateDialog.value = true
    }

    private fun confirmActivateMode() {
        showActivateDialog.value = false
        val modeNote = when (selectedMode.value) {
            ModeType.LUNCH -> "Lunch"
            ModeType.DINNER -> "Dinner"
            ModeType.BFAST -> "Breakfast"
            ModeType.HIGHCARB -> "High Carb"
        }
        val durationMin = durationText.value.toIntOrNull() ?: 60
        val durationMs = durationMin * 60 * 1000L

        val te = app.aaps.core.data.model.TE(
            timestamp = System.currentTimeMillis(),
            type = app.aaps.core.data.model.TE.Type.NOTE,
            note = modeNote,
            duration = durationMs,
            enteredBy = "AIMI Advisor",
            glucoseUnit = app.aaps.core.data.model.GlucoseUnit.MGDL
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                persistenceLayer.insertOrUpdateTherapyEvent(te)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AimiModeSettingsActivity, "$modeNote Mode Activated ($durationMin min)!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AimiModeSettingsActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
                error.printStackTrace()
            }
        }
    }

    private fun getStringPref(key: DoubleKey): String {
        return try {
            preferences.get(key).toString()
        } catch (e: Exception) {
            key.defaultValue.toString()
        }
    }

    private fun getStringPref(key: IntKey): String {
        return try {
            preferences.get(key).toString()
        } catch (e: Exception) {
            key.defaultValue.toString()
        }
    }
}
