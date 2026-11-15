package app.aaps.plugins.sync.nsclientV3.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.ActiveModeItem
import app.aaps.plugins.sync.nsclientV3.ModeCategory
import app.aaps.plugins.sync.nsclientV3.ModePreset
import app.aaps.plugins.sync.nsclientV3.ModePresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    viewModel: RemoteControlViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = "Remote Control",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    onSettings?.let { settingsClick ->
                        IconButton(onClick = settingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        )
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // --- DIALOGS ---

    if (uiState.showLicenseDialog) {
        LicenseKeyDialog(
            onActivate = { viewModel.activateLicense(it) },
            onDismiss = onNavigateBack
        )
    }

    if (uiState.showPasswordSetupDialog) {
        PasswordSetupDialog(
            onSave = { pwd, confirm -> viewModel.configurePassword(pwd, confirm) }
        )
    }

    if (uiState.sharePasswordPrompt != null) {
        SharePasswordDialog(
            password = uiState.sharePasswordPrompt!!,
            onDismiss = { viewModel.dismissSharePasswordPrompt() }
        )
    }

    if (uiState.showPasswordLoginDialog) {
        PasswordLoginDialog(
            onUnlock = { viewModel.unlockWithPassword(it) },
            onCancel = onNavigateBack
        )
    }

    if (uiState.showPinSetupDialog) {
        PinSetupDialog(
            onSave = { p1, p2 -> viewModel.saveNewPin(p1, p2) },
            onDismiss = { viewModel.showPinSetupDialog(false) }
        )
    }

    if (uiState.showConfirmStopDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showConfirmStopDialog(false) },
            title = { Text("Annuler les modes") },
            text = { Text("Annuler tous les modes actifs ?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmCancelAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Annuler tout")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showConfirmStopDialog(false) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // --- MAIN SCREEN ---

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Security PIN Header Card
            SecurityPinCard(
                pin = uiState.pinInput,
                isPinConfigured = uiState.isPinConfigured,
                onPinChanged = viewModel::onPinChanged,
                onSetupPinClick = { viewModel.showPinSetupDialog(true) }
            )

            // Tabs: MODES, CONTEXTS, EN COURS, LLM
            val tabTitles = listOf(
                stringResource(R.string.tab_modes),
                stringResource(R.string.tab_contexts),
                "${stringResource(R.string.tab_active)} (${uiState.activeModes.size})",
                stringResource(R.string.tab_llm)
            )

            PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.selectedTab) {
                    0 -> ModesTabContent(
                        modes = ModePresets.ALL_MODES.filter {
                            it.category in setOf(ModeCategory.MEAL, ModeCategory.ACTIVITY, ModeCategory.CONTROL)
                        },
                        customDurations = uiState.customDurations,
                        onDurationChanged = viewModel::setCustomDuration,
                        onSendMode = viewModel::onModeClick
                    )
                    1 -> ModesTabContent(
                        modes = ModePresets.ALL_MODES.filter {
                            it.category in setOf(ModeCategory.CONTEXT_ONLY, ModeCategory.PHYSIO)
                        },
                        customDurations = uiState.customDurations,
                        onDurationChanged = viewModel::setCustomDuration,
                        onSendMode = viewModel::onModeClick
                    )
                    2 -> ActiveModesTabContent(
                        activeModes = uiState.activeModes,
                        onRefresh = viewModel::refreshActiveModes
                    )
                    3 -> LlmTabContent(
                        text = uiState.llmText,
                        isLoading = uiState.llmIsLoading,
                        onTextChanged = viewModel::onLlmTextChanged,
                        onSend = viewModel::sendLlmContext
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SECURITY & PIN HEADER
// ═══════════════════════════════════════════════════════════

@Composable
private fun SecurityPinCard(
    pin: String,
    isPinConfigured: Boolean,
    onPinChanged: (String) -> Unit,
    onSetupPinClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = pin,
                onValueChange = onPinChanged,
                label = { Text("Code PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.weight(1f)
            )

            if (!isPinConfigured) {
                OutlinedButton(onClick = onSetupPinClick) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Créer PIN")
                }
            } else {
                IconButton(onClick = onSetupPinClick) {
                    Icon(Icons.Default.Key, contentDescription = "Changer PIN", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MODES TAB CONTENT
// ═══════════════════════════════════════════════════════════

@Composable
private fun ModesTabContent(
    modes: List<ModePreset>,
    customDurations: Map<String, Int>,
    onDurationChanged: (String, Int) -> Unit,
    onSendMode: (ModePreset, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(modes, key = { it.id }) { mode ->
            ModeItemCard(
                mode = mode,
                currentDuration = customDurations[mode.id] ?: mode.defaultDurationMin,
                onDurationChanged = { onDurationChanged(mode.id, it) },
                onSend = { onSendMode(mode, it) }
            )
        }
    }
}

@Composable
private fun ModeItemCard(
    mode: ModePreset,
    currentDuration: Int,
    onDurationChanged: (Int) -> Unit,
    onSend: (Int) -> Unit
) {
    val isStopMode = mode.id == "stop"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isStopMode) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isStopMode) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isStopMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (mode.description.isNotEmpty()) {
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isStopMode) {
                OutlinedTextField(
                    value = currentDuration.toString(),
                    onValueChange = { str ->
                        str.toIntOrNull()?.let { onDurationChanged(it) }
                    },
                    label = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp)
                )

                Button(
                    onClick = { onSend(currentDuration) }
                ) {
                    Text(stringResource(R.string.send_mode_button))
                }
            } else {
                Button(
                    onClick = { onSend(0) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Annuler")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ACTIVE MODES TAB CONTENT
// ═══════════════════════════════════════════════════════════

@Composable
private fun ActiveModesTabContent(
    activeModes: List<ActiveModeItem>,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.active_now_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
            }
        }

        if (activeModes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_active_modes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeModes) { item ->
                    ActiveModeItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun ActiveModeItemCard(item: ActiveModeItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.mode.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val minutes = item.remainingMinutes
                val remainingText = when {
                    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}min restantes"
                    minutes > 0 -> "$minutes min restantes"
                    else -> "Expiré"
                }
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// LLM TAB CONTENT
// ═══════════════════════════════════════════════════════════

@Composable
private fun LlmTabContent(
    text: String,
    isLoading: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.llm_context_header),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = stringResource(R.string.llm_context_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Exemples rapides (cliquez pour remplir) :", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = { onTextChanged("Heavy cardio 1h30") },
                        label = { Text("🏃 Cardio") }
                    )
                    SuggestionChip(
                        onClick = { onTextChanged("Malade avec fièvre") },
                        label = { Text("🤒 Maladie") }
                    )
                    SuggestionChip(
                        onClick = { onTextChanged("Stress examens demain") },
                        label = { Text("📚 Stress") }
                    )
                }
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            label = { Text(stringResource(R.string.llm_input_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            maxLines = 5
        )

        Button(
            onClick = onSend,
            enabled = !isLoading && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.llm_status_processing))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.send_to_llm))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// DIALOGS
// ═══════════════════════════════════════════════════════════

@Composable
private fun LicenseKeyDialog(
    onActivate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Security, contentDescription = null) },
        title = { Text(stringResource(R.string.license_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.license_key_message))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.license_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onActivate(key) }) {
                Text(stringResource(R.string.activate_license))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PasswordSetupDialog(
    onSave: (String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.configure_access_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.access_password_setup_message))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe (min 6 car.)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirmer le mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(password, confirm) }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun SharePasswordDialog(
    password: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ Partager ce mot de passe") },
        text = {
            Text(
                "Mot de passe d'accès : $password\n\nPartagez ce mot de passe de manière sécurisée avec les utilisateurs autorisés.\n\nCet avertissement ne s'affichera qu'une seule fois !"
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun PasswordLoginDialog(
    onUnlock: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.remote_access_login_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.remote_access_login_message))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.access_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onUnlock(password) }) {
                Text(stringResource(R.string.unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PinSetupDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_remote_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.remote_pin_setup_required))
                OutlinedTextField(
                    value = p1,
                    onValueChange = { p1 = it },
                    label = { Text(stringResource(R.string.new_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = p2,
                    onValueChange = { p2 = it },
                    label = { Text(stringResource(R.string.confirm_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(p1, p2) }) {
                Text(stringResource(R.string.save_pin))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
