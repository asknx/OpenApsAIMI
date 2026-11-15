package app.aaps.plugins.aps.openAPSAIMI.advisor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.plugins.aps.openAPSAIMI.advisor.*
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
    selectedPeriodDays: Int = 7,
    onSelectPeriodDays: (Int) -> Unit = {},
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
                title = { Text("AIMI Clinical Advisor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSupportClick) {
                        Icon(Icons.Default.MedicalServices, contentDescription = "Поддержка")
                    }
                    IconButton(onClick = onClinicalReportClick) {
                        Icon(Icons.Default.Assignment, contentDescription = "Клиническое предложение")
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
                            text = "Analyzing clinical data & AGP profile...",
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
                        selectedPeriodDays = selectedPeriodDays,
                        onSelectPeriodDays = onSelectPeriodDays,
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
            title = { Text("AI Advisor Provider") },
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
            text = { Text("Share this comprehensive Juggluco NG-style report with your endocrinologist.") },
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
            title = { Text("Клиническое предложение AIMI") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    clinicalDialogContent()
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissClinicalDialog) {
                    Text("Закрыть")
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
    selectedPeriodDays: Int,
    onSelectPeriodDays: (Int) -> Unit,
    resolveTitle: (Int) -> String,
    resolveDesc: (Int, List<Any>) -> String,
    onApplyRecClick: (AimiAction.PreferenceUpdate) -> Unit
) {
    val m = report.metrics
    val isMmol = m.isMmol
    val unitsStr = m.unitsStr

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Period Selector Chips ---
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1, 7, 14, 30, 90).forEach { days ->
                FilterChip(
                    selected = selectedPeriodDays == days,
                    onClick = { onSelectPeriodDays(days) },
                    label = { Text("${days}d") }
                )
            }
        }

        // --- Header Card ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clinical Profile Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${m.periodLabel} • Unit: $unitsStr",
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

        // --- 5-Zone TIR Donut / Bar Section ---
        Tir5ZoneCard(m)

        // --- Day-by-Day Colored Grid ---
        if (m.dailyTirList.isNotEmpty()) {
            DailyTirGridCard(m.dailyTirList)
        }

        // --- Hourly AGP Canvas Chart ---
        if (m.hourlyAgp.isNotEmpty()) {
            AgpChartCard(m.hourlyAgp, isMmol, unitsStr)
        }

        // --- Statistical Metrics Card (Juggluco NG style) ---
        StatisticsCard(m)

        // --- Episode Analysis Card ---
        EpisodeAnalysisCard(m)

        // --- Therapy / Meals Stats Card ---
        m.therapyStats?.let { stats ->
            Text(
                text = "THERAPY & MEAL ANALYSIS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TherapyStatsCard(stats, isMmol, unitsStr)
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

        // --- AI Neural Controller ---
        Text(
            text = "AI NEURAL CONTROLLER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        CognitiveCard(context.prefs.unifiedReactivityFactor)

        // --- AI Coach Card ---
        Text(
            text = "CLINICAL AI COACH",
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
private fun Tir5ZoneCard(m: AdvisorMetrics) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "TIME IN RANGE (5 ZONES)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Horizontal Stacked Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (m.tirVeryLow > 0) {
                    Box(modifier = Modifier.weight(m.tirVeryLow.toFloat()).fillMaxHeight().background(Color(0xFFB71C1C)))
                }
                if (m.tirLow > 0) {
                    Box(modifier = Modifier.weight(m.tirLow.toFloat()).fillMaxHeight().background(Color(0xFFE53935)))
                }
                if (m.tirInRange > 0) {
                    Box(modifier = Modifier.weight(m.tirInRange.toFloat()).fillMaxHeight().background(Color(0xFF43A047)))
                }
                if (m.tirHigh > 0) {
                    Box(modifier = Modifier.weight(m.tirHigh.toFloat()).fillMaxHeight().background(Color(0xFFFB8C00)))
                }
                if (m.tirVeryHigh > 0) {
                    Box(modifier = Modifier.weight(m.tirVeryHigh.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                }
            }

            // Legend rows
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TirLegendRow("Very High (>13.9 / >250)", Color(0xFFD32F2F), "${(m.tirVeryHigh * 100).toInt()}%", "< 5% target")
                TirLegendRow("High (10.0-13.9 / 180-250)", Color(0xFFFB8C00), "${(m.tirHigh * 100).toInt()}%", "< 25% target")
                TirLegendRow("In Target (3.9-10.0 / 70-180)", Color(0xFF43A047), "${(m.tirInRange * 100).toInt()}%", "> 70% target")
                TirLegendRow("Low (3.0-3.9 / 54-70)", Color(0xFFE53935), "${(m.tirLow * 100).toInt()}%", "< 4% target")
                TirLegendRow("Very Low (<3.0 / <54)", Color(0xFFB71C1C), "${(m.tirVeryLow * 100).toInt()}%", "< 1% target")
            }
        }
    }
}

@Composable
private fun TirLegendRow(label: String, color: Color, pct: String, target: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = pct, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "($target)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyTirGridCard(dailyTirList: List<DailyTirItem>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "DAY-BY-DAY TIR HISTORY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dailyTirList.forEach { day ->
                    val color = when {
                        day.tirPct >= 70.0 -> Color(0xFF2E7D32)
                        day.tirPct >= 50.0 -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.15f))
                            .border(1.dp, color, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(text = day.dateStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(text = "${day.tirPct.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgpChartCard(hourlyAgp: List<AgpHourStats>, isMmol: Boolean, unitsStr: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "HOURLY AGP MODAL PROFILE (24h)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Median (line), P25-P75 (inner band), P10-P90 (outer band) in $unitsStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val minVal = 40.0
            val maxVal = 260.0

            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val w = size.width
                val h = size.height

                fun getY(bg: Double): Float {
                    val norm = (bg - minVal) / (maxVal - minVal)
                    return (h - (norm * h).toFloat()).coerceIn(0f, h)
                }
                fun getX(hour: Int): Float = (hour / 23f) * w

                // Target Zone Corridor (70-180 mg/dL)
                val targetTop = getY(180.0)
                val targetBottom = getY(70.0)
                drawRect(
                    color = Color(0xFF43A047).copy(alpha = 0.12f),
                    topLeft = Offset(0f, targetTop),
                    size = Size(w, targetBottom - targetTop)
                )

                // Outer Band (P10-P90)
                val path10_90 = Path()
                path10_90.moveTo(getX(0), getY(hourlyAgp[0].p90))
                for (i in 1..23) path10_90.lineTo(getX(i), getY(hourlyAgp[i].p90))
                for (i in 23 downTo 0) path10_90.lineTo(getX(i), getY(hourlyAgp[i].p10))
                path10_90.close()
                drawPath(path10_90, color = Color(0xFF2196F3).copy(alpha = 0.15f))

                // Inner Band (P25-P75)
                val path25_75 = Path()
                path25_75.moveTo(getX(0), getY(hourlyAgp[0].p75))
                for (i in 1..23) path25_75.lineTo(getX(i), getY(hourlyAgp[i].p75))
                for (i in 23 downTo 0) path25_75.lineTo(getX(i), getY(hourlyAgp[i].p25))
                path25_75.close()
                drawPath(path25_75, color = Color(0xFF1976D2).copy(alpha = 0.25f))

                // Median Curve
                val medianPath = Path()
                medianPath.moveTo(getX(0), getY(hourlyAgp[0].median))
                for (i in 1..23) medianPath.lineTo(getX(i), getY(hourlyAgp[i].median))
                drawPath(medianPath, color = Color(0xFF0D47A1), style = Stroke(width = 3f, cap = StrokeCap.Round))

                // Draw points on median
                for (i in 0..23 step 3) {
                    drawCircle(Color(0xFF0D47A1), radius = 3.5f, center = Offset(getX(i), getY(hourlyAgp[i].median)))
                }
            }

            // X-axis hours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 3, 6, 9, 12, 15, 18, 21, 23).forEach { hour ->
                    Text(text = "%02d:00".format(hour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatisticsCard(m: AdvisorMetrics) {
    val isMmol = m.isMmol
    fun formatBg(mgdl: Double): String = if (isMmol) "%.1f".format(mgdl / 18.01559) else "%.0f".format(mgdl)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "CLINICAL STATISTICS (JUGGLUCO NG)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatItem(label = "Mean BG", value = "${formatBg(m.meanBg)} ${m.unitsStr}", modifier = Modifier.weight(1f))
                StatItem(label = "Median BG", value = "${formatBg(m.medianBg)} ${m.unitsStr}", modifier = Modifier.weight(1f))
                StatItem(label = "GMI (HbA1c)", value = "%.1f%%".format(m.gmi), modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatItem(label = "SD", value = "${formatBg(m.sdBg)} ${m.unitsStr}", modifier = Modifier.weight(1f))
                StatItem(label = "CV (%)", value = "%.1f%%".format(m.cvPct), subtitle = "<36% target", modifier = Modifier.weight(1f))
                StatItem(label = "IQR (P75-P25)", value = "${formatBg(m.iqrBg)} ${m.unitsStr}", modifier = Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatItem(label = "GRI (0-100)", value = "%.0f".format(m.gri), subtitle = m.griZone, modifier = Modifier.weight(1f))
                StatItem(label = "GVI", value = "%.2f".format(m.gvi), subtitle = "Variability", modifier = Modifier.weight(1f))
                StatItem(label = "PSG", value = "%.2f".format(m.psg), subtitle = "Success Index", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EpisodeAnalysisCard(m: AdvisorMetrics) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "EPISODE ANALYSIS (DEVIATIONS >= 15 MIN)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFB71C1C).copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("HYPO EVENTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFFD32F2F))
                        }
                        Text("${m.hypoEvents} (Severe: ${m.severeHypoEvents})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Avg: ${m.avgHypoDurationMin}m | Max: ${m.maxHypoDurationMin}m", style = MaterialTheme.typography.bodySmall)
                        Text("Peak: ${m.hypoPeakIntervalStr}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFB8C00).copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("HYPER EVENTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE65100))
                        }
                        Text("${m.hyperEvents} (Severe: ${m.severeHyperEvents})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Avg: ${m.avgHyperDurationMin}m | Max: ${m.maxHyperDurationMin}m", style = MaterialTheme.typography.bodySmall)
                        Text("Peak: ${m.hyperPeakIntervalStr}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                    }
                }
            }
        }
    }
}

@Composable
private fun TherapyStatsCard(stats: TherapyStats, isMmol: Boolean, unitsStr: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Meal Success Rate: ${stats.mealSuccessRate.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Prebolus: ${"%.0f".format(stats.avgPrebolusMin)} min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            fun formatBg(mgdl: Double?): String = if (mgdl == null) "--" else if (isMmol) "%.1f %s".format(mgdl / 18.01559, unitsStr) else "%.0f %s".format(mgdl, unitsStr)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Breakfast: ${formatBg(stats.bFastAvgPeak)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(text = "Lunch: ${formatBg(stats.lunchAvgPeak)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(text = "Dinner: ${formatBg(stats.dinnerAvgPeak)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }

            if (!stats.therapyEventsSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats.therapyEventsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = resolveTitle(rec.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rec.priority == AimiPriority.Critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = rec.domain.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val desc = resolveDesc(rec.descriptionResId, rec.descriptionArgs)
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (rec.action is AimiAction.PreferenceUpdate) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { onApplyClick(rec.action) },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply Suggestion")
                }
            }
        }
    }
}

@Composable
private fun CognitiveCard(reactivityFactor: Double) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Adaptive Reactivity Multiplier",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "System learned reactivity factor: %.2f".format(reactivityFactor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoachCard(analysis: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Strategic AI Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = analysis.ifBlank { "Analysis will appear after data processing..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
