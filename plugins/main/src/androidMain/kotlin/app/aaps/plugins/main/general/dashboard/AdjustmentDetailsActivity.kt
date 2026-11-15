package app.aaps.plugins.main.general.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import java.io.Serializable

import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.data.model.GlucoseUnit
import dev.zacsweers.metro.Inject

data class AdjustmentCardState(
    val glycemiaLine: String = "--",
    val predictionLine: String = "--",
    val iobActivityLine: String = "--",
    val decisionLine: String = "--",
    val peakTime: Double? = null,
    val dia: Double? = null,
    val targetBg: Double? = null,
    val smb: Double? = null,
    val basal: Double? = null,
    val isHypoRisk: Boolean = false,
    val pumpLine: String = "--",
    val safetyLine: String = "--",
    val modeLine: String? = null,
    val trajectoryTitle: String? = null,
    val trajectoryAscii: String? = null,
    val trajectoryMetrics: String? = null,
    val trajectoryRelevance: Double? = null,
    val detailedReason: String? = null,
    val adjustments: List<String> = emptyList()
) : Serializable

class AdjustmentDetailsActivity : MetroAppCompatActivity() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var preferences: Preferences

    private val cardState = mutableStateOf<AdjustmentCardState?>(null)

    companion object {
        const val EXTRA_ADJUSTMENT_STATE = "extra_adjustment_state"

        fun launch(context: Context, state: AdjustmentCardState) {
            context.startActivity(Intent(context, AdjustmentDetailsActivity::class.java).apply {
                putExtra(EXTRA_ADJUSTMENT_STATE, state)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val passedState = intent.getSerializableExtra(EXTRA_ADJUSTMENT_STATE) as? AdjustmentCardState
        cardState.value = passedState ?: buildLiveState()
        setContent {
            AapsTheme {
                AdjustmentDetailsScreen(
                    state = cardState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onRefresh = { cardState.value = buildLiveState() }
                )
            }
        }
    }

    private fun buildLiveState(): AdjustmentCardState {
        val lastRun = loop.lastRun
        val apsResult = lastRun?.constraintsProcessed ?: lastRun?.request
        val isMmol = profileFunction.getUnits() == GlucoseUnit.MMOL
        val unitLabel = if (isMmol) "mmol/L" else "mg/dL"

        val lastGv = kotlinx.coroutines.runBlocking { persistenceLayer.getLastGlucoseValue() }
        val glycemiaLine = if (lastGv != null) {
            val bgDisp = if (isMmol) lastGv.value / 18.01559 else lastGv.value
            val deltaVal = apsResult?.glucoseStatus?.delta
            val deltaStr = if (deltaVal != null && deltaVal != 0.0) {
                val d = if (isMmol) deltaVal / 18.01559 else deltaVal
                (if (d > 0) "+%.1f" else "%.1f").format(d)
            } else ""
            "%.1f %s %s".format(bgDisp, unitLabel, deltaStr).trim()
        } else "--"

        val predLine = if (apsResult != null && apsResult.hasPredictions && apsResult.predictionsAsGv.isNotEmpty()) {
            val lastPred = apsResult.predictionsAsGv.lastOrNull()
            if (lastPred != null) {
                val pVal = if (isMmol) lastPred.value / 18.01559 else lastPred.value
                "Pred: %.1f %s".format(pVal, unitLabel)
            } else "Predictions available"
        } else "--"

        val iobObj = apsResult?.iobData?.firstOrNull()
        val iobVal = iobObj?.iob ?: 0.0
        val actVal = iobObj?.activity ?: 0.0
        val iobActivityLine = "IOB: %.2f U | Act: %.3f U/h".format(iobVal, actVal)

        val smbVal = apsResult?.smb
        val rateVal = apsResult?.rate
        val decisionLine = when {
            smbVal != null && smbVal > 0.0 -> "SMB: %.2f U (Basal: %.2f U/h)".format(smbVal, rateVal ?: 0.0)
            rateVal != null -> "TBR: %.2f U/h".format(rateVal)
            else -> "--"
        }

        val pump = activePlugin.activePumpInternal
        val pumpName = (pump as? app.aaps.core.interfaces.plugin.PluginBase)?.name ?: "Pump"
        val pumpLine = "$pumpName (${if (pump.isSuspended()) "Suspended" else "Active"})"
        val safetyLine = try {
            val clazz = Class.forName("app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorStatusTracker")
            val getStatus = clazz.getMethod("getStatus")
            val pair = getStatus.invoke(null) as? Pair<*, *>
            val status = pair?.first
            val verdict = pair?.second as? String
            val messageProp = status?.javaClass?.getMethod("getMessage")?.invoke(status) as? String
            "Auditor: ${messageProp ?: "ACTIVE"}" + (verdict?.let { " ($it)" } ?: "")
        } catch (_: Throwable) {
            "Auditor: ACTIVE"
        }

        val targetDisp = apsResult?.targetBG?.let { if (isMmol) it / 18.01559 else it }
        val detailedReason = apsResult?.reason ?: lastRun?.request?.reason

        return AdjustmentCardState(
            glycemiaLine = glycemiaLine,
            predictionLine = predLine,
            iobActivityLine = iobActivityLine,
            decisionLine = decisionLine,
            targetBg = targetDisp,
            smb = smbVal,
            basal = rateVal,
            isHypoRisk = apsResult?.isHypoRisk ?: false,
            pumpLine = pumpLine,
            safetyLine = safetyLine,
            detailedReason = detailedReason
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentDetailsScreen(
    state: AdjustmentCardState?,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AIMI Adjustments Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    if (state != null) {
                        IconButton(onClick = {
                            val text = buildExportText(state)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AIMI Adjustments", text))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            val text = buildExportText(state)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra(Intent.EXTRA_SUBJECT, "AIMI Loop Report")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Report"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No adjustment data available", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Glycemia & Prediction
            item {
                InfoCard(title = "Glycemia & Prediction", icon = Icons.Default.MonitorHeart) {
                    InfoRow(label = "BG Status", value = state.glycemiaLine)
                    InfoRow(label = "Prediction (30 min)", value = state.predictionLine)
                }
            }

            // IOB & Activity
            item {
                InfoCard(title = "IOB & Algorithm", icon = Icons.Default.Analytics) {
                    InfoRow(label = "IOB / Activity", value = state.iobActivityLine)
                    InfoRow(label = "Decision (SMB / Basal)", value = state.decisionLine)
                    if (state.peakTime != null) InfoRow(label = "Peak time", value = "%.0f min".format(state.peakTime))
                    if (state.dia != null) InfoRow(label = "DIA", value = "%.1f h".format(state.dia))
                    if (state.targetBg != null) InfoRow(label = "Target BG", value = "%.0f mg/dL".format(state.targetBg))
                    if (state.smb != null) InfoRow(label = "SMB", value = "%.2f U".format(state.smb))
                    if (state.basal != null) InfoRow(label = "Basal rate", value = "%.2f U/h".format(state.basal))
                    if (state.isHypoRisk) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("⚠️ HYPO RISK ACTIVE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Pump & Safety
            item {
                InfoCard(title = "Pump & Safety", icon = Icons.Default.LocalHospital) {
                    InfoRow(label = "Pump status", value = state.pumpLine)
                    InfoRow(label = "Safety analysis", value = state.safetyLine)
                    state.modeLine?.let { InfoRow(label = "Active mode", value = it) }
                }
            }

            // Trajectory
            if (state.trajectoryTitle != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Trajectory: ${state.trajectoryTitle}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            if (state.trajectoryAscii != null) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.trajectoryAscii,
                                        modifier = Modifier.padding(12.dp),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (state.trajectoryMetrics != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(state.trajectoryMetrics, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.trajectoryRelevance != null) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { state.trajectoryRelevance.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Relevance: %.2f".format(state.trajectoryRelevance), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Detailed Reason / Decisions
            if (!state.detailedReason.isNullOrEmpty() || state.adjustments.isNotEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Algorithm Decisions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (!state.detailedReason.isNullOrEmpty()) {
                                Text(state.detailedReason, style = MaterialTheme.typography.bodySmall)
                            } else if (state.adjustments.isNotEmpty()) {
                                state.adjustments.forEach { decision ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp).padding(top = 4.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(decision, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildExportText(state: AdjustmentCardState): String = buildString {
    appendLine("=== AIMI Loop Report ===")
    appendLine()
    appendLine("Glycemia: ${state.glycemiaLine}")
    appendLine("Prediction: ${state.predictionLine}")
    appendLine("IOB/Activity: ${state.iobActivityLine}")
    appendLine("Decision: ${state.decisionLine}")
    if (state.peakTime != null) appendLine("Peak: %.0f min".format(state.peakTime))
    if (state.dia != null) appendLine("DIA: %.1f h".format(state.dia))
    if (state.smb != null) appendLine("SMB: %.2f U".format(state.smb))
    if (state.basal != null) appendLine("Basal: %.2f U/h".format(state.basal))
    if (state.isHypoRisk) appendLine("⚠️ HYPO RISK ACTIVE")
    appendLine()
    appendLine("Pump: ${state.pumpLine}")
    appendLine("Safety: ${state.safetyLine}")
    state.modeLine?.let { appendLine("Mode: $it") }
    if (!state.detailedReason.isNullOrEmpty()) {
        appendLine()
        appendLine("Reason: ${state.detailedReason}")
    }
    if (state.adjustments.isNotEmpty()) {
        appendLine()
        appendLine("Adjustments:")
        state.adjustments.forEach { appendLine("• $it") }
    }
}
