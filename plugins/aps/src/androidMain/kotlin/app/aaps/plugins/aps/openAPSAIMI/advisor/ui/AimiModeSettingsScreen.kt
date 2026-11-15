package app.aaps.plugins.aps.openAPSAIMI.advisor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModeSettingsActivity.ModeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AimiModeSettingsScreen(
    selectedMode: ModeType,
    onSelectMode: (ModeType) -> Unit,
    prebolus1Text: String,
    onPrebolus1Change: (String) -> Unit,
    prebolus2Text: String,
    onPrebolus2Change: (String) -> Unit,
    reactivityText: String,
    onReactivityChange: (String) -> Unit,
    durationText: String,
    onDurationChange: (String) -> Unit,
    intervalText: String,
    onIntervalChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onActivateClick: () -> Unit,
    onBackClick: () -> Unit,
    showActivateDialog: Boolean,
    activateDialogTitle: String,
    activateDialogMessage: String,
    onConfirmActivate: () -> Unit,
    onDismissActivateDialog: () -> Unit
) {
    val modes = listOf(
        ModeType.LUNCH to "LUNCH 🥗",
        ModeType.DINNER to "DINNER 🍽️",
        ModeType.BFAST to "BFAST 🍳",
        ModeType.HIGHCARB to "HIGHCARB 🍕"
    )
    val selectedIndex = modes.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Mode Settings") },
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
            PrimaryTabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { onSelectMode(mode) },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

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
                        value = prebolus1Text,
                        onValueChange = onPrebolus1Change,
                        label = { Text("Prebolus 1 (U)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = prebolus2Text,
                        onValueChange = onPrebolus2Change,
                        label = { Text("Prebolus 2 (U)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = reactivityText,
                        onValueChange = onReactivityChange,
                        label = { Text("Reactivity (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = durationText,
                        onValueChange = onDurationChange,
                        label = { Text("Duration (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = onIntervalChange,
                        label = { Text("Interval (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "SAVE SETTINGS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val modeName = when (selectedMode) {
                ModeType.LUNCH -> "LUNCH"
                ModeType.DINNER -> "DINNER"
                ModeType.BFAST -> "BFAST"
                ModeType.HIGHCARB -> "HIGH CARB"
            }

            OutlinedButton(
                onClick = onActivateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "⚡ ACTIVATE $modeName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showActivateDialog) {
        AlertDialog(
            onDismissRequest = onDismissActivateDialog,
            title = { Text(activateDialogTitle) },
            text = { Text(activateDialogMessage) },
            confirmButton = {
                Button(onClick = onConfirmActivate) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissActivateDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}
