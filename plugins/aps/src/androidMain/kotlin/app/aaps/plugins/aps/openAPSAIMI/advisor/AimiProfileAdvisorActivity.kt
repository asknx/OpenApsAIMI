package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiAdvisorService.BasalProfileProposal
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

    private val showModelDialogState = mutableStateOf(false)
    private val showSupportDialogState = mutableStateOf(false)
    private val showClinicalDialogState = mutableStateOf(false)
    private val clinicalProposalState = mutableStateOf<BasalProfileProposal?>(null)

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
                            report = reportState.value
                        )
                    },
                    onExportClinicalProposal = {
                        showClinicalDialogState.value = false
                        clinicalProposalState.value?.let { proposal ->
                            val text = advisorService.exportBasalProfileProposalText(proposal)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            startActivity(Intent.createChooser(intent, "Поделиться отчетом"))
                        }
                    },
                    onApplyBasalProfile = {
                        showClinicalDialogState.value = false
                        reportState.value?.let { rep ->
                            advisorService.applyProfileRecommendations(rep.recommendations)
                            Toast.makeText(this@AimiProfileAdvisorActivity, "Новый профиль создан!", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    },
                    onApplySettingsProposal = {
                        showClinicalDialogState.value = false
                        reportState.value?.let { rep ->
                            advisorService.applySettingRecommendations(rep.recommendations)
                            Toast.makeText(this@AimiProfileAdvisorActivity, "Настройки AIMI обновлены!", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    },
                    onDismissClinicalDialog = { showClinicalDialogState.value = false }
                )
            }
        }

        loadReport()
    }

    private fun loadReport() {
        isLoadingState.value = true
        errorMessageState.value = null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val history = historyRepo.getRecentActions(7)
                val report = advisorService.generateReport(periodDays = 7, history = history)
                val context = advisorService.collectContext(periodDays = 7)
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
                val proposal = advisorService.generateBasalProfileProposal(7)
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

    @Composable
    private fun ClinicalProposalContent(
        proposal: BasalProfileProposal?,
        report: AdvisorReport?
    ) {
        if (proposal == null) {
            Text("Загрузка данных...")
            return
        }

        val strategyRus = when (proposal.strategy) {
            "SAFETY_REDUCTION" -> "Снижение для безопасности (риск гипо)"
            "GENTLE_INCREASE" -> "Мягкое повышение (коррекция гипер)"
            else -> "Сохранение текущей базы"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Стратегия: $strategyRus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Обоснование: ${proposal.rationale}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (report?.recommendations?.isNotEmpty() == true) {
                Text(
                    text = "Рекомендации:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                report.recommendations.forEach { rec ->
                    val color = if (rec.priority == app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority.Critical) Color.Red else Color.Unspecified
                    val title = rh.gs(rec.titleResId)
                    val desc = if (rec.descriptionArgs.isNotEmpty()) {
                        String.format(rh.gs(rec.descriptionResId), *rec.descriptionArgs.toTypedArray())
                    } else {
                        rh.gs(rec.descriptionResId)
                    }
                    Text(
                        text = "• $title: $desc",
                        color = color,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = "Предлагаемые базальные слоты:",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            proposal.rows.forEach { slot ->
                val timeStr = String.format(Locale.US, "%02d:00", slot.hour)
                Text(
                    text = "$timeStr : ${slot.current} -> ${slot.proposed} Ед/ч",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    private fun generateAndShareReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val content = advisorService.generateClinicalExport(14)
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
                        app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.ActionType.PREFERENCE_CHANGE,
                        action.key.key,
                        action.reason,
                        "Manual",
                        action.newValue.toString()
                    )
                }
                Toast.makeText(this, "Настройка обновлена!", Toast.LENGTH_SHORT).show()
                loadReport()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка применения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
