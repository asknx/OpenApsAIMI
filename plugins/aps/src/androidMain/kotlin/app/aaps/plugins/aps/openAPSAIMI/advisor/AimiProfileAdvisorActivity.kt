package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner
import app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import app.aaps.core.ui.compose.MetroAppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.zacsweers.metro.Inject

/**
 * 🏥 AimiProfileAdvisorActivity
 * 
 * Clinical Dashboard for user profile analysis.
 * Integrates metrics, observations, and unified application of recommendations.
 */
class AimiProfileAdvisorActivity : MetroAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var unifiedReactivityLearner: UnifiedReactivityLearner
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var geminiOAuthManager: GeminiOAuthManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var auditorAIService: app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
    
    private lateinit var advisorService: AimiAdvisorService
    private lateinit var historyRepo: AdvisorHistoryRepository
    
    private var lastReport: AdvisorReport? = null
    private var lastContext: AdvisorContext? = null

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
        historyRepo = app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository(this)
        title = rh.gs(R.string.aimi_advisor_title)
        
        val bgColor = "#10141C".toColorInt()
        val cardColor = "#1E293B".toColorInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(bgColor)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        val scrollView = ScrollView(this).apply {
            addView(rootLayout)
            setBackgroundColor(bgColor)
        }
        setContentView(scrollView)

        val loadingText = TextView(this).apply {
            text = rh.gs(R.string.aimi_adv_loading)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }
        rootLayout.addView(loadingText)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val history = historyRepo.getRecentActions(7)
                val report = advisorService.generateReport(periodDays = 7, history = history)
                val context = advisorService.collectContext(periodDays = 7)
                
                lastReport = report
                lastContext = context

                withContext(Dispatchers.Main) {
                    rootLayout.removeView(loadingText)
                    rootLayout.addView(createDashboardHeader(report))
                    rootLayout.addView(createMetricsGrid(report.metrics, cardColor))
                    
                    report.metrics.therapyStats?.let { stats ->
                        rootLayout.addView(createSectionHeader("Therapy Analysis"))
                        rootLayout.addView(createTherapyCard(stats, cardColor))
                    }
                    
                    val standardRecs = report.recommendations.filter { it.domain != AimiDomain.Pkpd }
                    val pkpdRecs = report.recommendations.filter { it.domain == AimiDomain.Pkpd }

                    if (standardRecs.isNotEmpty()) {
                        rootLayout.addView(createSectionHeader(rh.gs(R.string.aimi_adv_section_obs)))
                        standardRecs.forEach { rec ->
                            rootLayout.addView(createObservationCard(rec, cardColor))
                        }
                    }

                    if (pkpdRecs.isNotEmpty()) {
                        rootLayout.addView(createSectionHeader(rh.gs(R.string.aimi_adv_section_pkpd)))
                        pkpdRecs.forEach { rec ->
                            rootLayout.addView(createObservationCard(rec, cardColor))
                        }
                    }

                    rootLayout.addView(createSectionHeader(rh.gs(R.string.aimi_adv_section_brain)))
                    rootLayout.addView(createCognitiveCard(context.prefs.unifiedReactivityFactor, cardColor))

                    rootLayout.addView(createSectionHeader(rh.gs(R.string.aimi_adv_section_coach)))
                    rootLayout.addView(createCoachCard(context, report))
            
                    rootLayout.addView(createFooter(report))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingText.text = rh.gs(R.string.aimi_adv_error_prefix, e.localizedMessage)
                    loadingText.setTextColor("#F87171".toColorInt()) 
                }
                e.printStackTrace()
            }
        }
    }

    private fun createDashboardHeader(report: AdvisorReport): LinearLayout {
        val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 48)
            
            val infoLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            infoLayout.addView(TextView(activity).apply {
                text = rh.gs(R.string.aimi_adv_report_weekly)
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            
            infoLayout.addView(TextView(activity).apply {
                text = report.metrics.periodLabel
                textSize = 14f
                setTextColor("#94A3B8".toColorInt()) 
                setPadding(0, 4, 0, 0)
            })
            
            addView(infoLayout)
            
            val pill = CardView(activity).apply {
                radius = 50f
                setCardBackgroundColor("#0F392B".toColorInt()) 
                cardElevation = 0f
            }
            
            val scoreText = TextView(activity).apply {
                text = rh.gs(R.string.aimi_adv_score_label, report.overallScore)
                setTextColor("#4ADE80".toColorInt()) 
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                setPadding(32, 12, 32, 12)
            }
            pill.addView(scoreText)
            addView(pill)

            val supportBtn = TextView(activity).apply {
                text = "🩺"
                textSize = 22f
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    showSupportDialog()
                }
            }
            addView(supportBtn)

            val settingsBtn = TextView(activity).apply {
                text = "⚙️"
                textSize = 22f
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    showModelSelectorDialog()
                }
            }
            addView(settingsBtn)

            val applyBtn = TextView(activity).apply {
                text = "✅"
                textSize = 22f
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    showClinicalReportDialog()
                }
            }
            addView(applyBtn)
        }
    }

    private fun showClinicalReportDialog() {
        val report = lastReport ?: return
        val context = lastContext ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val proposal = advisorService.generateBasalProfileProposal(7)
                withContext(Dispatchers.Main) {
                    val container = LinearLayout(this@AimiProfileAdvisorActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 32, 48, 32)
                    }

                    val scroll = ScrollView(this@AimiProfileAdvisorActivity).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, 1000)
                        addView(container)
                    }

                    container.addView(TextView(this@AimiProfileAdvisorActivity).apply {
                        text = "Клинический отчет и рекомендации"
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.BLACK)
                        setPadding(0, 0, 0, 24)
                    })

                    // 1. Basal Strategy
                    val strategyRus = when(proposal.strategy) {
                        "SAFETY_REDUCTION" -> "Снижение для безопасности (риск гипо)"
                        "GENTLE_INCREASE" -> "Мягкое повышение (коррекция гипер)"
                        else -> "Сохранение текущей базы"
                    }
                    container.addView(createCategoryLabel("ПРЕДЛОЖЕНИЕ ПО БАЗАЛУ"))
                    container.addView(TextView(this@AimiProfileAdvisorActivity).apply {
                        text = "Стратегия: $strategyRus\nОбоснование: ${proposal.rationale}\n"
                        textSize = 14f
                        setTextColor(Color.DKGRAY)
                    })

                    proposal.rows.forEach { row ->
                        container.addView(TextView(this@AimiProfileAdvisorActivity).apply {
                            val diff = if (row.current > 0) (row.proposed - row.current) / row.current * 100 else 0.0
                            text = String.format(Locale.getDefault(), "%02dч: %.2f -> %.2f ед/ч (%+.1f%%)", row.hour, row.current, row.proposed, diff)
                            textSize = 13f
                            setTextColor(if (diff > 0) Color.parseColor("#B91C1C") else if (diff < 0) Color.parseColor("#1D4ED8") else Color.BLACK)
                            setPadding(0, 4, 0, 4)
                        })
                    }

                    // 2. Tactical Settings
                    val tacticalActions = report.recommendations.filter { it.action != null }
                    if (tacticalActions.isNotEmpty()) {
                        container.addView(createCategoryLabel("ИЗМЕНЕНИЯ НАСТРОЕК"))
                        tacticalActions.forEach { rec ->
                            container.addView(TextView(this@AimiProfileAdvisorActivity).apply {
                                val action = rec.action!!
                                text = when (action) {
                                    is app.aaps.plugins.aps.openAPSAIMI.model.AimiAction.PreferenceUpdate -> "🔹 ${action.reason}\n   Новое значение: ${action.newValue}"
                                    else -> "🔹 ${action.reason}"
                                }
                                textSize = 13f
                                setTextColor(Color.BLACK)
                                setPadding(0, 8, 0, 8)
                            })
                        }
                    }

                    AlertDialog.Builder(this@AimiProfileAdvisorActivity)
                        .setTitle("Анализ и рекомендации")
                        .setView(scroll)
                        .setPositiveButton("Экспорт / Поделиться") { _, _ ->
                            val text = advisorService.exportBasalProfileProposalText(proposal)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            startActivity(android.content.Intent.createChooser(intent, "Поделиться отчетом"))
                        }
                        .setNeutralButton("Применить Базал") { _, _ ->
                             advisorService.applyProfileRecommendations(report.recommendations)
                             android.widget.Toast.makeText(this@AimiProfileAdvisorActivity, "Новый профиль создан!", android.widget.Toast.LENGTH_LONG).show()
                             finish()
                        }
                        .setNegativeButton("Применить Настройки") { _, _ ->
                             advisorService.applySettingRecommendations(report.recommendations)
                             android.widget.Toast.makeText(this@AimiProfileAdvisorActivity, "Настройки AIMI обновлены!", android.widget.Toast.LENGTH_LONG).show()
                             finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@AimiProfileAdvisorActivity, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUnifiedApplyDialog(recs: List<AimiRecommendation>) {
        val actions = recs.mapNotNull { it.action }
        val profileActions = actions.filter { it.domain in listOf(AimiDomain.Profile, AimiDomain.Basal, AimiDomain.Isf, AimiDomain.Target) }
        val settingsActions = actions.filterIsInstance<AimiAction.PreferenceUpdate>()

        if (actions.isEmpty()) {
            android.widget.Toast.makeText(this, rh.gs(R.string.aimi_adv_no_change_msg), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000) 
            addView(container)
        }

        container.addView(TextView(this).apply {
            text = rh.gs(R.string.aimi_adv_apply_dialog_prefix)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 24)
        })

        if (profileActions.isNotEmpty()) {
            container.addView(createCategoryLabel("PROFILE CHANGES"))
            profileActions.forEach { action ->
                container.addView(createChangeItem(action))
            }
        }

        if (settingsActions.isNotEmpty()) {
            container.addView(createCategoryLabel("SETTINGS CHANGES"))
            settingsActions.forEach { action ->
                container.addView(createChangeItem(action))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(rh.gs(R.string.aimi_adv_report_title))
            .setView(scroll)
            .setNeutralButton(rh.gs(R.string.aimi_adv_apply_profile)) { _, _ ->
                advisorService.applyProfileRecommendations(recs)
                android.widget.Toast.makeText(this, "Новый профиль создан!", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
            .setPositiveButton(rh.gs(R.string.aimi_adv_apply_settings)) { _, _ ->
                advisorService.applySettingRecommendations(recs)
                android.widget.Toast.makeText(this, "Настройки обновлены!", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun createCategoryLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.GRAY)
        setPadding(0, 24, 0, 8)
    }

    private fun createChangeItem(action: AimiAction) = TextView(this).apply {
        this.text = when (action) {
            is AimiAction.PreferenceUpdate -> "🔹 ${action.reason}\n   → ${action.newValue}"
            else -> "🔹 ${action.reason}"
        }
        textSize = 14f
        setTextColor(Color.DKGRAY)
        setPadding(0, 8, 0, 8)
    }

    private fun showSupportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Professional Support")
            .setMessage("Share this data with your endocrinologist for a remote review.")
            .setPositiveButton("Share Report") { _, _ -> generateAndShareReport() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateAndShareReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val content = advisorService.generateClinicalExport(14)
            withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, content)
                }
                startActivity(android.content.Intent.createChooser(intent, "Share Clinical Data"))
            }
        }
    }

    private fun showModelSelectorDialog() {
        val providers = AuditorAIService.Provider.entries
        val names = providers.map { it.displayName }.toTypedArray()
        val current = preferences.get(app.aaps.core.keys.StringKey.AimiAdvisorProvider)
        val selectedIndex = providers.indexOfFirst { it.id.uppercase() == current.uppercase() }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(rh.gs(R.string.aimi_advisor_model_title))
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val selected = providers[which]
                preferences.put(app.aaps.core.keys.StringKey.AimiAdvisorProvider, selected.id.uppercase())
                android.widget.Toast.makeText(this, "Provider set to ${selected.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createMetricsGrid(m: AdvisorMetrics, cardColor: Int): LinearLayout {
        val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            
            val row1 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(createMetricCard(rh.gs(R.string.aimi_adv_metric_tir), "${(m.tir70_180*100).toInt()}%", cardColor))
            row1.addView(createMetricCard("Avg TDD", String.format(Locale.getDefault(), "%.1f U", m.tdd), cardColor))
            
            val row2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
            row2.addView(createMetricCard("GMI (Est. HbA1c)", String.format(Locale.getDefault(), "%.2f%%", m.gmi), cardColor))
            row2.addView(createMetricCard("Hypo < 70", "${(m.timeBelow70*100).toInt()}%", cardColor))
            
            addView(row1)
            addView(row2)

            if (m.todayTir != null) {
                val row3 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
                row3.addView(createMetricCard("Today TIR", "${(m.todayTir*100).toInt()}%", cardColor))
                row3.addView(createMetricCard("Today TDD", String.format(Locale.getDefault(), "%.1f U", m.todayTdd ?: 0.0), cardColor))
                addView(row3)
            }
        }
    }

    private fun createMetricCard(title: String, value: String, color: Int): CardView {
        val activity = this
        return CardView(this).apply {
            layoutParams = paramHalf()
            radius = 16f
            setCardBackgroundColor(color)
            cardElevation = 0f
            
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }
            
            layout.addView(TextView(activity).apply {
                text = value
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            
            layout.addView(TextView(activity).apply {
                text = title
                textSize = 12f
                setTextColor("#94A3B8".toColorInt())
                setPadding(0, 4, 0, 0)
            })
            
            addView(layout)
        }
    }

    private fun createTherapyCard(stats: TherapyStats, cardColor: Int): CardView {
        val activity = this
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(cardColor)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 32) }
            
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            layout.addView(TextView(activity).apply {
                text = "Meal Success Rate: ${((stats.meals.filter { it.success }.size.toDouble() / stats.meals.size.coerceAtLeast(1)) * 100).toInt()}%"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })
            
            layout.addView(TextView(activity).apply {
                text = "Total Carbs: ${stats.totalCarbs.toInt()}g | Total Insulin: ${String.format(Locale.getDefault(), "%.1f U", stats.totalInsulin)}"
                setTextColor("#94A3B8".toColorInt())
                textSize = 12f
                setPadding(0, 8, 0, 0)
            })
            
            addView(layout)
        }
    }

    private fun paramHalf() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(8, 0, 8, 0)
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title.uppercase(Locale.getDefault())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor("#64748B".toColorInt()) 
            setPadding(0, 48, 0, 16)
        }
    }

    private fun createObservationCard(rec: AimiRecommendation, cardColor: Int): CardView {
        val activity = this
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(cardColor)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            
            val mainLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val icon = TextView(activity).apply {
                text = if (rec.priority == AimiPriority.Low) "✅" else "⚠️"
                textSize = 20f
                setPadding(0, 0, 32, 0)
            }
            mainLayout.addView(icon)
            
            val textLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            textLayout.addView(TextView(activity).apply {
                text = rh.gs(rec.titleResId)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            
            textLayout.addView(TextView(activity).apply {
                text = if (rec.descriptionResId != 0) {
                    if (rec.descriptionArgs.isNotEmpty()) {
                        String.format(rh.gs(rec.descriptionResId), *rec.descriptionArgs.toTypedArray())
                    } else {
                        rh.gs(rec.descriptionResId)
                    }
                } else {
                    rec.action?.reason ?: ""
                }
                textSize = 13f
                setTextColor("#94A3B8".toColorInt())
                setPadding(0, 4, 0, 0)
            })
            
            mainLayout.addView(textLayout)
            
            if (rec.action is AimiAction.PreferenceUpdate) {
                val btn = TextView(activity).apply {
                    text = "APPLY"
                    setTextColor("#38BDF8".toColorInt()) 
                    setTypeface(null, Typeface.BOLD)
                    setPadding(16, 16, 16, 16)
                    setOnClickListener {
                        showApplyActionDialog(rec.action as AimiAction.PreferenceUpdate)
                    }
                }
                mainLayout.addView(btn)
            }
            
            addView(mainLayout)
        }
    }

    private fun showApplyActionDialog(update: AimiAction.PreferenceUpdate) {
        AlertDialog.Builder(this)
            .setTitle("Apply Change")
            .setMessage("Reason: ${update.reason}\n\nNew Value: ${update.newValue}")
            .setPositiveButton("Apply") { _, _ ->
                applyAction(update)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyAction(update: AimiAction.PreferenceUpdate) {
        try {
            when (val key = update.key) {
                is app.aaps.core.keys.BooleanKey -> preferences.put(key, update.newValue as Boolean)
                is app.aaps.core.keys.DoubleKey -> preferences.put(key, update.newValue as Double)
                is app.aaps.core.keys.IntKey -> preferences.put(key, update.newValue as Int)
                is app.aaps.core.keys.StringKey -> preferences.put(key, update.newValue as String)
                is app.aaps.core.keys.UnitDoubleKey -> preferences.put(key, update.newValue as Double)
            }
            logAction(update)
            android.widget.Toast.makeText(this, "Setting updated!", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun logAction(update: AimiAction.PreferenceUpdate) {
        CoroutineScope(Dispatchers.IO).launch {
            historyRepo.logAction(
                app.aaps.plugins.aps.openAPSAIMI.advisor.data.AdvisorHistoryRepository.ActionType.PREFERENCE_CHANGE,
                update.reason,
                update.key.key,
                "Manual",
                update.newValue.toString()
            )
        }
    }

    private fun createCognitiveCard(reactivity: Double, cardColor: Int): CardView {
        val activity = this
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(cardColor)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL
            }
            
            layout.addView(TextView(activity).apply {
                text = "🧠"
                textSize = 32f
                setPadding(0, 0, 32, 0)
            })
            
            val textLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            textLayout.addView(TextView(activity).apply {
                text = String.format(Locale.getDefault(), "NEUTRAL (x%.2f)", reactivity)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor("#4ADE80".toColorInt())
            })
            
            textLayout.addView(TextView(activity).apply {
                text = "System is operating with baseline parameters. No anomalies detected."
                textSize = 13f
                setTextColor("#94A3B8".toColorInt())
                setPadding(0, 4, 0, 0)
            })
            
            layout.addView(textLayout)
            addView(layout)
        }
    }

    private suspend fun createCoachCard(ctx: AdvisorContext, report: AdvisorReport): CardView {
        val activity = this
        val analysis = advisorService.generatePlainTextAnalysis(ctx, report)
        
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor("#1E293B".toColorInt())
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            layout.addView(TextView(activity).apply {
                text = "✨ AI Coach"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor("#A855F7".toColorInt()) 
            })
            
            layout.addView(TextView(activity).apply {
                text = analysis
                textSize = 13f
                setTextColor("#E2E8F0".toColorInt())
                setPadding(0, 16, 0, 0)
            })
            
            val aiHint = TextView(activity).apply {
                text = "\n⚙️ Configure AI Provider to enable Pro coaching."
                textSize = 11f
                setTextColor("#64748B".toColorInt())
            }
            layout.addView(aiHint)
            
            addView(layout)
        }
    }

    private fun createFooter(report: AdvisorReport): TextView {
        return TextView(this).apply {
            text = rh.gs(R.string.aimi_adv_generated_footer, SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(report.generatedAt)))
            textSize = 10f
            setTextColor("#475569".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 64)
        }
    }
}
