package app.aaps.plugins.aps.openAPSAIMI.context.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent
import app.aaps.plugins.aps.openAPSAIMI.context.ContextPreset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContextScreen(
    isContextEnabled: Boolean,
    onContextEnabledChange: (Boolean) -> Unit,
    isLLMEnabled: Boolean,
    onLLMEnabledChange: (Boolean) -> Unit,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isParsing: Boolean,
    onSendClick: () -> Unit,
    onClearInputClick: () -> Unit,
    presets: List<ContextPreset>,
    onPresetClick: (ContextPreset) -> Unit,
    activeIntents: List<Pair<String, ContextIntent>>,
    onRemoveIntent: (String) -> Unit,
    onExtendIntent: (String) -> Unit,
    onClearAllClick: () -> Unit,
    onBackClick: () -> Unit,
    formatTimeRemaining: (ContextIntent) -> String,
    formatDisplayString: (ContextIntent) -> String,
    // Dialog states
    showExtendDialog: Boolean,
    onSelectExtendMinutes: (Int) -> Unit,
    onDismissExtendDialog: () -> Unit,
    showClearAllDialog: Boolean,
    onConfirmClearAll: () -> Unit,
    onDismissClearAllDialog: () -> Unit,
    parseFailureMessage: String?,
    onDismissParseFailureDialog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Context Module") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Settings Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Context Module",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isContextEnabled,
                            onCheckedChange = onContextEnabledChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI LLM Context Parsing",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isLLMEnabled,
                            onCheckedChange = onLLMEnabledChange
                        )
                    }
                }
            }

            // --- Input Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        label = { Text("Tell AIMI what is happening...") },
                        placeholder = { Text("e.g. 'Cardio 45 min', 'Feeling sick', 'High stress'") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (isParsing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onClearInputClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSendClick,
                            enabled = inputText.isNotBlank() && !isParsing,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send")
                        }
                    }
                }
            }

            // --- Quick Presets Section ---
            if (presets.isNotEmpty()) {
                Text(
                    text = "QUICK PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = { onPresetClick(preset) },
                            label = { Text("${preset.icon} ${preset.displayName}") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // --- Active Contexts Header & List ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE CONTEXTS (${activeIntents.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (activeIntents.isNotEmpty()) {
                    TextButton(onClick = onClearAllClick) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (activeIntents.isEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Text(
                        text = "Aucun contexte actif",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            } else {
                activeIntents.forEach { (id, intent) ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatDisplayString(intent),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatTimeRemaining(intent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { onExtendIntent(id) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Extend", style = MaterialTheme.typography.labelSmall)
                            }

                            IconButton(onClick = { onRemoveIntent(id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- Extend Duration Dialog ---
    if (showExtendDialog) {
        val options = listOf(
            15 to "15 min",
            30 to "30 min",
            60 to "1 heure",
            120 to "2 heures"
        )
        AlertDialog(
            onDismissRequest = onDismissExtendDialog,
            title = { Text("Prolonger le contexte") },
            text = {
                Column {
                    options.forEach { (mins, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectExtendMinutes(mins) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissExtendDialog) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- Clear All Dialog ---
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearAllDialog,
            title = { Text("Supprimer tous les contextes ?") },
            text = { Text("Voulez-vous vraiment annuler tous les contextes actifs ?") },
            confirmButton = {
                Button(onClick = onConfirmClearAll) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissClearAllDialog) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- Parse Failure Dialog ---
    parseFailureMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissParseFailureDialog,
            title = { Text("Analyse échouée") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = onDismissParseFailureDialog) {
                    Text("OK")
                }
            }
        )
    }
}
