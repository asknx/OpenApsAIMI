package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.context.ContextManager
import app.aaps.plugins.aps.openAPSAIMI.context.ContextPreset
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Context Activity - UI for Context Module.
 * 
 * Simplified version without ViewModel for quick implementation.
 */
class ContextActivity : MetroAppCompatActivity() {
    
    @Inject lateinit var contextManager: ContextManager
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    
    private lateinit var editChatInput: EditText
    private lateinit var btnSendChat: Button
    private lateinit var btnClearInput: Button
    private lateinit var progressParsing: ProgressBar
    private lateinit var recyclerActiveIntents: RecyclerView
    private lateinit var textEmptyState: TextView
    private lateinit var btnClearAll: Button
    private lateinit var switchContextEnabled: CompoundButton
    private lateinit var switchLLMEnabled: CompoundButton
    private lateinit var adapter: ContextIntentAdapter
    
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_context)
        editChatInput = findViewById(R.id.editChatInput)
        btnSendChat = findViewById(R.id.btnSendChat)
        btnClearInput = findViewById(R.id.btnClearInput)
        progressParsing = findViewById(R.id.progressParsing)
        recyclerActiveIntents = findViewById(R.id.recyclerActiveIntents)
        textEmptyState = findViewById(R.id.textEmptyState)
        btnClearAll = findViewById(R.id.btnClearAll)
        switchContextEnabled = findViewById(R.id.switchContextEnabled)
        switchLLMEnabled = findViewById(R.id.switchLLMEnabled)
        
        // Toolbar
        title = rh.gs(R.string.context_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        setupUI()
        setupPresets()
        refreshUI()
    }
    
    private fun setupUI() {
        // RecyclerView
        adapter = ContextIntentAdapter(
            onRemove = { id -> 
                contextManager.removeIntent(id)
                refreshUI()
            },
            onExtend = { id -> showExtendDialog(id) },
            getTimeRemaining = { intent -> getTimeRemaining(intent) },
            getDisplayString = { intent -> getDisplayString(intent) }
        )
        
        recyclerActiveIntents.layoutManager = LinearLayoutManager(this)
        recyclerActiveIntents.adapter = adapter
        
        // Send button
        btnSendChat.setOnClickListener {
            val text = editChatInput.text.toString()
            if (text.isNotBlank()) {
                activityScope.launch {
                    progressParsing.visibility = View.VISIBLE
                    btnSendChat.isEnabled = false
                    
                    try {
                        val ids = contextManager.addIntent(text)
                        
                        if (ids.isNotEmpty()) {
                            editChatInput.text?.clear()
                            Toast.makeText(this@ContextActivity, "${ids.size} contexte(s) ajouté(s)", Toast.LENGTH_SHORT).show()
                        } else {
                            // Feedback detailed on failure
                            val isLLMEnabled = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
                            val provider = sp.getString(app.aaps.core.keys.StringKey.AimiAdvisorProvider.key, "OPENAI")
                            
                            val msg = if (isLLMEnabled) {
                                "Aucun contexte détecté via IA ($provider).\n\nCauses possibles :\n1. Clé API manquante ou invalide (Préférences AIMI > Advisor)\n2. Timeout réseau\n3. Description trop vague\n\nFallback : Essayez des mots-clés simples (ex: 'Sport 1h', 'Malade')."
                            } else {
                                "Aucun contexte détecté via mots-clés.\nEssayez des commandes simples :\n- 'Cardio 1h'\n- 'Malade'\n- 'Stress'\n- 'Repas surprise'"
                            }
                            
                            MaterialAlertDialogBuilder(this@ContextActivity)
                                .setTitle("Analyse échouée")
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                        
                        refreshUI()
                    } catch (e: Exception) {
                        Toast.makeText(this@ContextActivity, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        progressParsing.visibility = View.GONE
                        btnSendChat.isEnabled = true
                    }
                }
            }
        }
        
        // Clear button
        btnClearInput.setOnClickListener {
            editChatInput.text?.clear()
        }
        
        // Clear all button
        btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Supprimer tous les contextes ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    contextManager.clearAll()
                    refreshUI()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
        
        // Module toggle
        switchContextEnabled.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, isChecked)
        }
        
        // LLM toggle
        switchLLMEnabled.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, isChecked)
        }
    }
    
    private fun setupPresets() {
        if (ContextPreset.ALL_PRESETS.size >= 10) {
            findViewById<View>(R.id.chipCardio).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[0]) }
            findViewById<View>(R.id.chipStrength).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[1]) }
            findViewById<View>(R.id.chipYoga).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[2]) }
            findViewById<View>(R.id.chipSport).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[3]) }
            findViewById<View>(R.id.chipWalking).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[4]) }
            findViewById<View>(R.id.chipSick).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[5]) }
            findViewById<View>(R.id.chipStress).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[6]) }
            findViewById<View>(R.id.chipMealRisk).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[7]) }
            findViewById<View>(R.id.chipAlcohol).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[8]) }
            findViewById<View>(R.id.chipTravel).setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[9]) }
        }
    }
    
    private fun refreshUI() {
        // Get intents
        val intents = contextManager.getAllIntents().toList()
        adapter.submitList(intents)
        
        // Show/hide empty state
        if (intents.isEmpty()) {
            recyclerActiveIntents.visibility = View.GONE
            textEmptyState.visibility = View.VISIBLE
        } else {
            recyclerActiveIntents.visibility = View.VISIBLE
            textEmptyState.visibility = View.GONE
        }
        
        // Settings
        switchContextEnabled.isChecked = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, false)
        switchLLMEnabled.isChecked = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
    }
    
    private fun addPreset(preset: ContextPreset) {
        activityScope.launch {
            try {
                contextManager.addPreset(preset)
                Toast.makeText(this@ContextActivity, "Contexte ajouté", Toast.LENGTH_SHORT).show()
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this@ContextActivity, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showExtendDialog(intentId: String) {
        val options = arrayOf("15 min", "30 min", "1 heure", "2 heures")
        val durations = arrayOf(15, 30, 60, 120)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Prolonger le contexte")
            .setItems(options) { _, which ->
                contextManager.extendDuration(intentId, durations[which].minutes)
                refreshUI()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun getTimeRemaining(intent: app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent): String {
        val now = System.currentTimeMillis()
        val remaining = (intent.endTimeMs - now) / 1000 / 60 // minutes
        
        return when {
            remaining <= 0 -> "Expiré"
            remaining < 60 -> "${remaining}min restantes"
            else -> "${remaining / 60}h ${remaining % 60}min"
        }
    }
    
    private fun getDisplayString(intent: app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent): String {
        return when (intent) {
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Activity -> 
                "🏃 Activity: ${intent.activityType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Illness -> 
                "🤒 Illness: ${intent.symptomType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Stress -> 
                "😰 Stress: ${intent.stressType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.UnannouncedMealRisk -> 
                "🍕 Meal Risk: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Alcohol -> 
                "🍷 Alcohol: ${intent.units}U ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Travel -> 
                "✈️ Travel: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.MenstrualCycle -> 
                "🔄 Cycle: ${intent.phase.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Custom -> 
                "📝 ${intent.description}"
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
