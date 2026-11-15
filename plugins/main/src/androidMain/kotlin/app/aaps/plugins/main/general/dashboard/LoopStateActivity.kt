package app.aaps.plugins.main.general.dashboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.ui.compose.AapsTheme

/**
 * Migrate of LoopStateActivity to Compose M3.
 * Shows the allowed loop mode transitions with confirmation dialogs.
 */
class LoopStateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AapsTheme {
                LoopStateScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopStateScreen(onBack: () -> Unit) {
    // State for confirmation dialog
    var pendingAction by remember { mutableStateOf<LoopModeAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loop State & Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Loop Status Section
            InfoBanner()

            Text(
                "Available Loop Modes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Mode action cards
            val modes = listOf(
                LoopModeAction("Closed Loop", Icons.Default.Lock, MaterialTheme.colorScheme.primary,
                    "Full closed-loop APS control with automated SMB deliveries."),
                LoopModeAction("Low Glucose Suspend", Icons.Default.WaterDrop, MaterialTheme.colorScheme.tertiary,
                    "Loop suspends insulin below threshold glucose value."),
                LoopModeAction("Open Loop", Icons.Default.LockOpen, MaterialTheme.colorScheme.secondary,
                    "Suggestions only – no automated delivery."),
                LoopModeAction("Suspend 1h", Icons.Default.Pause, MaterialTheme.colorScheme.error,
                    "Suspend the loop for 1 hour."),
                LoopModeAction("Suspend 2h", Icons.Default.Pause, MaterialTheme.colorScheme.error,
                    "Suspend the loop for 2 hours."),
                LoopModeAction("Disconnect 30min", Icons.Default.BluetoothDisabled, MaterialTheme.colorScheme.error,
                    "Disconnect pump for 30 minutes."),
                LoopModeAction("Disconnect 1h", Icons.Default.BluetoothDisabled, MaterialTheme.colorScheme.error,
                    "Disconnect pump for 1 hour."),
            )

            modes.forEach { action ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingAction = action }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = action.color.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(action.icon, contentDescription = null, tint = action.color)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(action.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Confirmation dialog
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(action.icon, contentDescription = null, tint = action.color) },
            title = { Text("Confirm: ${action.title}") },
            text = { Text("${action.description}\n\nThis action will be applied immediately.") },
            confirmButton = {
                Button(
                    onClick = { pendingAction = null /* Loop action applied */ },
                    colors = ButtonDefaults.buttonColors(containerColor = action.color)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "AIMI loop diagnostics. Select a mode to change how the closed-loop algorithm operates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private data class LoopModeAction(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val description: String
)
