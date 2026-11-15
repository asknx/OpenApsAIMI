package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository
import app.aaps.plugins.aps.openAPSAIMI.advisor.ui.AimiProfileAdvisorScreen
import app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner
import app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AimiProfileAdvisorActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var unifiedReactivityLearner: UnifiedReactivityLearner
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var auditorAIService: AuditorAIService
    @Inject lateinit var geminiOAuthManager: GeminiOAuthManager

    private lateinit var advisorService: AimiAdvisorService
    private lateinit var historyRepo: AdvisorHistoryRepository

    private val reportState = mutableStateOf<AdvisorReport?>(null)
    private val contextState = mutableStateOf<AdvisorContext?>(null)
    private val coachAnalysisState = mutableStateOf("")
    private val isLoadingState = mutableStateOf(true)
    private val errorMessageState = mutableStateOf<String?>(null)
    private val selectedPeriodDaysState = mutableStateOf(7)

    private val showModelDialogState = mutableStateOf(false)
    private val showSupportDialogState = mutableStateOf(false)
    private val showClinicalDialogState = mutableStateOf(false)
    private val clinicalProposalState = mutableStateOf<FullAdvisorProposal?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        advisorService = AimiAdvisorService(
            profileFunction = profileFunction,
            persistenceLayer = persistenceLayer,
            preferences = preferences,
            rh = rh,
            unifiedReactivityLearner = unifiedReactivityLearner,
            tddCalculator = tddCalculator,
            tirCalculator = tirCalculator,
            auditorAIService = auditorAIService,
            aapsLogger = aapsLogger,
            dateUtil = dateUtil,
            rxBus = null
        )
        historyRepo = AdvisorHistoryRepository(this)

        setContent {
            AapsTheme {
                val providers = AuditorAIService.Provider.entries
                val current = preferences.get(app.aaps.core.keys.StringKey.AimiAdvisorProvider)
                val selectedIndex = providers.indexOfFirst { it.id.uppercase() == current.uppercase() }.coerceAtLeast(0)

                AimiProfileAdvisorScreen(
                    report = reportState.value,
                    context = contextState.value,
                    coachAnalysis = coachAnalysisState.value,
                    isLoading = isLoadingState.value,
                    errorMessage = errorMessageState.value,
                    selectedPeriodDays = selectedPeriodDaysState.value,
                    onSelectPeriodDays = { days ->
                        selectedPeriodDaysState.value = days
                        loadReport(days)
                    },
                    onBackClick = { finish() },
                    onSupportClick = { showSupportDialogState.value = true },
                    onModelSelectorClick = { showModelDialogState.value = true },
                    onClinicalReportClick = { fetchAndShowClinicalProposal() },
                    onApplyRecClick = { applyAction(it) },
                    resolveTitle = { rh.gs(it) },
                    resolveDesc = { resId, args ->
                        if (args.isNotEmpty()) String.format(rh.gs(resId), *args.toTypedArray())
                        else rh.gs(resId)
                    },
                    showModelDialog = showModelDialogState.value,
                    providers = providers,
                    selectedProviderIndex = selectedIndex,
                    onSelectProvider = { provider ->
                        preferences.put(app.aaps.core.keys.StringKey.AimiAdvisorProvider, provider.id.uppercase())
                        Toast.makeText(this@AimiProfileAdvisorActivity, "Provider set to ${provider.displayName}", Toast.LENGTH_SHORT).show()
                        showModelDialogState.value = false
                    },
                    onDismissModelDialog = { showModelDialogState.value = false },
                    showSupportDialog = showSupportDialogState.value,
                    onShareReport = {
                        showSupportDialogState.value = false
                        generateAndShareReport()
                    },
                    onDismissSupportDialog = { showSupportDialogState.value = false },
                    showClinicalDialog = showClinicalDialogState.value,
                    clinicalDialogContent = {
                        ClinicalProposalContent(
                            proposal = clinicalProposalState.value,
                            report = reportState.value,
                            onApplyFull = { applyBasal, applyIc, applyIsf, checkedSettings ->
                                applyFullProposal(applyBasal, applyIc, applyIsf, checkedSettings)
                            },
                            onApplySettingsOnly = { checkedSettings ->
                                applySettingsOnly(checkedSettings)
                            },
                            onExportProposal = { exportProposalText() }
                        )
                    },
                    onExportClinicalProposal = { exportProposalText() },
                    onApplyBasalProfile = {},
                    onApplySettingsProposal = {},
                    onDismissClinicalDialog = { showClinicalDialogState.value = false }
                )
            }
        }

        loadReport(7)
    }

    private fun loadReport(days: Int = 7) {
        isLoadingState.value = true
        errorMessageState.value = null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val history = historyRepo.getRecentActions(days)
                val report = advisorService.generateReport(periodDays = days, history = history)
                val context = advisorService.collectContext(periodDays = days)
                val coachAnalysis = advisorService.generatePlainTextAnalysis(context, report)

                withContext(Dispatchers.Main) {
                    reportState.value = report
                    contextState.value = context
                    coachAnalysisState.value = coachAnalysis
                    isLoadingState.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessageState.value = rh.gs(R.string.aimi_adv_error_prefix, e.localizedMessage)
                    isLoadingState.value = false
                }
                e.printStackTrace()
            }
        }
    }

    private fun fetchAndShowClinicalProposal() {
        if (reportState.value == null || contextState.value == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val proposal = advisorService.generateFullProposal(selectedPeriodDaysState.value)
                withContext(Dispatchers.Main) {
                    clinicalProposalState.value = proposal
                    showClinicalDialogState.value = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AimiProfileAdvisorActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyFullProposal(
        applyBasal: Boolean,
        applyIc: Boolean,
        applyIsf: Boolean,
        checkedSettings: Map<String, Boolean>
    ) {
        val proposal = clinicalProposalState.value ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val createdProfileName = advisorService.createAndActivateAimiProfile(
                    proposal = proposal,
                    applyBasal = applyBasal,
                    applyIc = applyIc,
                    applyIsf = applyIsf,
                    checkedSettings = checkedSettings
                )
                historyRepo.logAction(
                    AdvisorHistoryRepository.ActionType.PROFILE_CHANGE,
                    createdProfileName,
                    "Applied via AIMI Clinical Proposal",
                    "User Accepted",
                    "Activated"
                )
                withContext(Dispatchers.Main) {
                    showClinicalDialogState.value = false
                    Toast.makeText(this@AimiProfileAdvisorActivity, "Создан и активирован профиль $createdProfileName", Toast.LENGTH_LONG).show()
                    loadReport(selectedPeriodDaysState.value)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AimiProfileAdvisorActivity, "Ошибка создания профиля: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applySettingsOnly(checkedSettings: Map<String, Boolean>) {
        val proposal = clinicalProposalState.value ?: return
        try {
            proposal.aimiSettings.forEach { setting ->
                if (checkedSettings[setting.prefKey] == true) {
                    when (setting.prefKey) {
                        app.aaps.core.keys.DoubleKey.OApsAIMILunchFactor.key -> preferences.put(app.aaps.core.keys.DoubleKey.OApsAIMILunchFactor, setting.proposedValue.toDoubleOrNull() ?: 1.0)
                        app.aaps.core.keys.DoubleKey.OApsAIMIMaxSMB.key -> {
                            val num = setting.proposedValue.replace("U", "").trim().toDoubleOrNull()
                            if (num != null) preferences.put(app.aaps.core.keys.DoubleKey.OApsAIMIMaxSMB, num)
                        }
                        app.aaps.core.keys.BooleanKey.OApsAIMIPkpdEnabled.key -> preferences.put(app.aaps.core.keys.BooleanKey.OApsAIMIPkpdEnabled, setting.proposedValue.equals("Enabled", ignoreCase = true))
                    }
                }
            }
            showClinicalDialogState.value = false
            Toast.makeText(this, "Применены настройки AIMI", Toast.LENGTH_SHORT).show()
            loadReport(selectedPeriodDaysState.value)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка применения настроек: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportProposalText() {
        val proposal = clinicalProposalState.value ?: return
        val text = buildString {
            appendLine("=== КЛИНИЧЕСКИЙ ОТЧЕТ И ПРЕДЛОЖЕНИЕ AIMI ===")
            appendLine("Период: ${proposal.periodDays} дней")
            appendLine("Сводка: ${proposal.clinicalSummary}")
            appendLine()
            appendLine("ПОЧАСОВОЙ БАЗАЛ (24 часа):")
            proposal.hourlyBasal.forEach { h ->
                appendLine(String.format(Locale.US, "%02d:00 | %.2f -> %.2f U/h | Median: %.1f | %s", h.hour, h.currentRate, h.proposedRate, h.agpMedian, h.reason))
            }
            appendLine()
            appendLine("КОЭФФИЦИЕНТЫ:")
            appendLine("IC (Carb Ratio): %.1f -> %.1f g/U".format(proposal.currentIc, proposal.proposedIc))
            appendLine("ISF: %.1f -> %.1f mg/dL/U".format(proposal.currentIsf, proposal.proposedIsf))
            appendLine()
            appendLine("НАСТРОЙКИ AIMI:")
            proposal.aimiSettings.forEach { s ->
                appendLine("${s.title}: ${s.currentValue} -> ${s.proposedValue} (${s.reason})")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Экспорт клинического предложения AIMI"))
    }

    @Composable
    private fun ClinicalProposalContent(
        proposal: FullAdvisorProposal?,
        report: AdvisorReport?,
        onApplyFull: (Boolean, Boolean, Boolean, Map<String, Boolean>) -> Unit,
        onApplySettingsOnly: (Map<String, Boolean>) -> Unit,
        onExportProposal: () -> Unit
    ) {
        if (proposal == null) {
            Text("Загрузка клинических данных...")
            return
        }

        var applyBasal by remember { mutableStateOf(true) }
        var applyIc by remember { mutableStateOf(true) }
        var applyIsf by remember { mutableStateOf(true) }
        val checkedSettings = remember {
            mutableStateMapOf<String, Boolean>().apply {
                proposal.aimiSettings.forEach { put(it.prefKey, it.isEnabledByDefault) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = proposal.clinicalSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // 1. Basal Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyBasal, onCheckedChange = { applyBasal = it })
                Text(
                    text = "Почасовой базал (24 часа) — адаптация к AGP",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    proposal.hourlyBasal.forEach { h ->
                        val deltaPct = if (h.currentRate > 0) ((h.proposedRate / h.currentRate) - 1.0) * 100.0 else 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "%02d:00  %.2f -> %.2f U/h (%+.0f%%)".format(h.hour, h.currentRate, h.proposedRate, deltaPct),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = h.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 2. Coefficients (IC & ISF)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyIc, onCheckedChange = { applyIc = it })
                Text(
                    text = "Углеводный коэффициент (IC): %.1f -> %.1f г/U".format(proposal.currentIc, proposal.proposedIc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyIsf, onCheckedChange = { applyIsf = it })
                Text(
                    text = "Фактор чувствительности (ISF): %.1f -> %.1f".format(proposal.currentIsf, proposal.proposedIsf),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (proposal.aimiSettings.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Корректировка параметров AIMI:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                proposal.aimiSettings.forEach { setting ->
                    val isChecked = checkedSettings[setting.prefKey] ?: true
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checkedSettings[setting.prefKey] = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${setting.title}: ${setting.currentValue} -> ${setting.proposedValue}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = setting.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onApplyFull(applyBasal, applyIc, applyIsf, checkedSettings) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Создать профиль (с датой) и применить")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onApplySettingsOnly(checkedSettings) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Только настройки AIMI")
                    }
                    OutlinedButton(
                        onClick = onExportProposal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Экспорт")
                    }
                }
            }
        }
    }

    private fun generateAndShareReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val content = advisorService.generateClinicalExport(selectedPeriodDaysState.value)
            withContext(Dispatchers.Main) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, content)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, "Отчет консультанта AIMI"))
            }
        }
    }

    private fun applyAction(action: AimiAction) {
        if (action is AimiAction.PreferenceUpdate) {
            try {
                when (val key = action.key) {
                    is app.aaps.core.keys.BooleanKey -> preferences.put(key, action.newValue as Boolean)
                    is app.aaps.core.keys.DoubleKey -> preferences.put(key, action.newValue as Double)
                    is app.aaps.core.keys.IntKey -> preferences.put(key, action.newValue as Int)
                    is app.aaps.core.keys.StringKey -> preferences.put(key, action.newValue as String)
                    is app.aaps.core.keys.UnitDoubleKey -> preferences.put(key, action.newValue as Double)
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    historyRepo.logAction(
                        AdvisorHistoryRepository.ActionType.PREFERENCE_CHANGE,
                        action.key.key,
                        action.reason,
                        "Manual",
                        action.newValue.toString()
                    )
                }
                Toast.makeText(this, "Настройка обновлена!", Toast.LENGTH_SHORT).show()
                loadReport(selectedPeriodDaysState.value)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка применения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
