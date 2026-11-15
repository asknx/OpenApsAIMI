package app.aaps.plugins.aps.openAPSAIMI.advisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.plugins.aps.openAPSAIMI.advisor.AdvisorContext
import app.aaps.plugins.aps.openAPSAIMI.advisor.AdvisorMetrics
import app.aaps.plugins.aps.openAPSAIMI.advisor.AdvisorReport
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiRecommendation
import app.aaps.plugins.aps.openAPSAIMI.advisor.TherapyStats
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AimiProfileAdvisorScreen(
    report: AdvisorReport?,
    context: AdvisorContext?,
    coachAnalysis: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onSupportClick: () -> Unit,
    onModelSelectorClick: () -> Unit,
    onClinicalReportClick: () -> Unit,
    onApplyRecClick: (AimiAction.PreferenceUpdate) -> Unit,
    resolveTitle: (Int) -> String,
    resolveDesc: (Int, List<Any>) -> String,
    // Dialogs state
    showModelDialog: Boolean,
    providers: List<AuditorAIService.Provider>,
    selectedProviderIndex: Int,
    onSelectProvider: (AuditorAIService.Provider) -> Unit,
    onDismissModelDialog: () -> Unit,
    showSupportDialog: Boolean,
    onShareReport: () -> Unit,
    onDismissSupportDialog: () -> Unit,
    showClinicalDialog: Boolean,
    clinicalDialogContent: @Composable () -> Unit,
    onExportClinicalProposal: () -> Unit,
    onApplyBasalProfile: () -> Unit,
    onApplySettingsProposal: () -> Unit,
    onDismissClinicalDialog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AIMI Advisor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSupportClick) {
                        Icon(Icons.Default.MedicalServices, contentDescription = "Support")
                    }
                    IconButton(onClick = onModelSelectorClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Model")
                    }
                    IconButton(onClick = onClinicalReportClick) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Clinical Report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing profile data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                report != null && context != null -> {
                    AdvisorContent(
                        report = report,
                        context = context,
                        coachAnalysis = coachAnalysis,
                        resolveTitle = resolveTitle,
                        resolveDesc = resolveDesc,
                        onApplyRecClick = onApplyRecClick
                    )
                }
            }
        }
    }

    // --- Model Selector Dialog ---
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = onDismissModelDialog,
            title = { Text("AI Advisor Model") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    providers.forEachIndexed { index, provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProvider(provider) }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = index == selectedProviderIndex,
                                onClick = { onSelectProvider(provider) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = provider.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissModelDialog) {
                    Text("Close")
                }
            }
        )
    }

    // --- Support Dialog ---
    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = onDismissSupportDialog,
            title = { Text("Professional Support") },
            text = { Text("Share this data with your endocrinologist for remote review.") },
            confirmButton = {
                Button(onClick = onShareReport) {
                    Text("Share Report")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissSupportDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Clinical Report Dialog ---
    if (showClinicalDialog) {
        AlertDialog(
            onDismissRequest = onDismissClinicalDialog,
            title = { Text("Клинический отчет и рекомендации") },
            text = {
                Box(modifier = Modifier.height(400.dp)) {
                    clinicalDialogContent()
                }
            },
            confirmButton = {
                Button(onClick = onApplyBasalProfile) {
                    Text("Применить Базал")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportClinicalProposal) {
                        Text("Экспорт")
                    }
                    FilledTonalButton(onClick = onApplySettingsProposal) {
                        Text("Применить Настройки")
                    }
                }
            }
        )
    }
}

@Composable
private fun AdvisorContent(
    report: AdvisorReport,
    context: AdvisorContext,
    coachAnalysis: String,
    resolveTitle: (Int) -> String,
    resolveDesc: (Int, List<Any>) -> String,
    onApplyRecClick: (AimiAction.PreferenceUpdate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Card ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Weekly Profile Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = report.metrics.periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F392B))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Score: ${report.overallScore}",
                    color = Color(0xFF4ADE80),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- Metrics Grid ---
        MetricsSection(report.metrics)

        // --- Therapy Stats ---
        report.metrics.therapyStats?.let { stats ->
            Text(
                text = "THERAPY ANALYSIS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TherapyStatsCard(stats)
        }

        // --- Standard Recommendations ---
        val standardRecs = report.recommendations.filter { it.domain != AimiDomain.Pkpd }
        if (standardRecs.isNotEmpty()) {
            Text(
                text = "OBSERVATIONS & ADVICE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            standardRecs.forEach { rec ->
                RecommendationCard(
                    rec = rec,
                    resolveTitle = resolveTitle,
                    resolveDesc = resolveDesc,
                    onApplyClick = onApplyRecClick
                )
            }
        }

        // --- PKPD Recommendations ---
        val pkpdRecs = report.recommendations.filter { it.domain == AimiDomain.Pkpd }
        if (pkpdRecs.isNotEmpty()) {
            Text(
                text = "PHARMACOKINETICS (PK/PD)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            pkpdRecs.forEach { rec ->
                RecommendationCard(
                    rec = rec,
                    resolveTitle = resolveTitle,
                    resolveDesc = resolveDesc,
                    onApplyClick = onApplyRecClick
                )
            }
        }

        // --- Brain / Cognitive Card ---
        Text(
            text = "AI NEURAL CONTROLLER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        CognitiveCard(context.prefs.unifiedReactivityFactor)

        // --- AI Coach Card ---
        Text(
            text = "AI COACH",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        CoachCard(coachAnalysis)

        // --- Footer ---
        val formattedDate = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(report.generatedAt))
        Text(
            text = "Report generated: $formattedDate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun MetricsSection(m: AdvisorMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "TIR 70-180",
                value = "${(m.tir70_180 * 100).toInt()}%",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Avg TDD",
                value = String.format(Locale.getDefault(), "%.1f U", m.tdd),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "GMI (HbA1c)",
                value = String.format(Locale.getDefault(), "%.2f%%", m.gmi),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Hypo < 70",
                value = "${(m.timeBelow70 * 100).toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }
        if (m.todayTir != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Today TIR",
                    value = "${(m.todayTir * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Today TDD",
                    value = String.format(Locale.getDefault(), "%.1f U", m.todayTdd ?: 0.0),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TherapyStatsCard(stats: TherapyStats) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val successMeals = stats.meals.count { it.success }
            val rate = (successMeals.toDouble() / stats.meals.size.coerceAtLeast(1) * 100).toInt()
            Text(
                text = "Meal Success Rate: $rate%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Total Carbs: ${stats.totalCarbs.toInt()}g | Total Insulin: ${String.format(Locale.getDefault(), "%.1f U", stats.totalInsulin)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: AimiRecommendation,
    resolveTitle: (Int) -> String,
    resolveDesc: (Int, List<Any>) -> String,
    onApplyClick: (AimiAction.PreferenceUpdate) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (rec.priority == AimiPriority.Low) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (rec.priority == AimiPriority.Low) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resolveTitle(rec.titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val desc = if (rec.descriptionResId != 0) {
                    resolveDesc(rec.descriptionResId, rec.descriptionArgs)
                } else {
                    rec.action?.reason ?: ""
                }
                if (desc.isNotEmpty()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (rec.action is AimiAction.PreferenceUpdate) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { onApplyClick(rec.action as AimiAction.PreferenceUpdate) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("APPLY", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CognitiveCard(reactivity: Double) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color(0xFF4ADE80)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = String.format(Locale.getDefault(), "NEUTRAL (x%.2f)", reactivity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ADE80)
                )
                Text(
                    text = "System is operating with baseline parameters. No anomalies detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoachCard(coachAnalysis: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✨ AI Coach Guidance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA855F7)
            )
            Text(
                text = coachAnalysis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "⚙️ Configure AI Provider to enable Pro coaching.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
