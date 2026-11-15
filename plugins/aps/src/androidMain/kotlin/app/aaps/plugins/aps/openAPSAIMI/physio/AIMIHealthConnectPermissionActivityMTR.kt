package app.aaps.plugins.aps.openAPSAIMI.physio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import app.aaps.core.ui.compose.AapsTheme
import kotlinx.coroutines.launch

/**
 * 🔐 AIMI Health Connect Permission Request Activity
 * Migrated to Jetpack Compose + Material 3.
 */
class AIMIHealthConnectPermissionActivityMTR : AppCompatActivity() {

    companion object {
        private const val TAG = "AIMI_HC_Activity"
        val REQUIRED_PERMISSIONS = AIMIHealthConnectPermissions.ALL_REQUIRED_PERMISSIONS
    }

    private val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HC client", e)
            null
        }
    }

    private val grantedPermissionsState = mutableStateOf<Set<String>>(emptySet())
    private val sdkStatusState = mutableStateOf<Int>(HealthConnectClient.SDK_AVAILABLE)

    private val requestPermissionLauncher: ActivityResultLauncher<Set<String>> =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { grantedFromUi ->
            Log.i(TAG, "HC: permission result callback grantedFromUi=${grantedFromUi.size}/${REQUIRED_PERMISSIONS.size}")

            val client = healthConnectClient
            if (client == null) {
                grantedPermissionsState.value = grantedFromUi
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                val nowGranted = client.permissionController.getGrantedPermissions()
                Log.i(TAG, "HC: nowGranted=${nowGranted.size}/${REQUIRED_PERMISSIONS.size} -> $nowGranted")
                grantedPermissionsState.value = nowGranted
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkInitialStatus()

        setContent {
            AapsTheme {
                HealthConnectPermissionScreen(
                    grantedPermissions = grantedPermissionsState.value,
                    sdkStatus = sdkStatusState.value,
                    onRequestPermissions = { requestPermissions() },
                    onOpenSettings = { openHealthConnectSettings() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun checkInitialStatus() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        sdkStatusState.value = sdkStatus

        val client = healthConnectClient ?: return
        lifecycleScope.launch {
            try {
                val granted = client.permissionController.getGrantedPermissions()
                grantedPermissionsState.value = granted
            } catch (e: Exception) {
                Log.e(TAG, "Error checking granted permissions", e)
            }
        }
    }

    private fun requestPermissions() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                } catch (e: Exception) {
                    Log.e(TAG, "HC: Cannot open store", e)
                }
            } else {
                openHealthConnectSettings()
            }
            return
        }

        try {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch HC request", e)
            openHealthConnectSettings()
        }
    }

    private fun openHealthConnectSettings() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthConnectPermissionScreen(
    grantedPermissions: Set<String>,
    sdkStatus: Int,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val allGranted = AIMIHealthConnectPermissionActivityMTR.REQUIRED_PERMISSIONS.all { it in grantedPermissions }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect Permissions") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Status Card
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (allGranted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (allGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = if (allGranted) "All Permissions Granted" else "Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (allGranted) "AIMI can access steps, HR, HRV and sleep data."
                            else "AIMI needs health data access to predict sensitivity changes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Permissions list
            Text("Requested Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            val permissions = listOf(
                "Steps" to "Allows AIMI to observe physical activity and step count",
                "Heart Rate" to "Detects exercise intensity and autonomic changes",
                "Heart Rate Variability" to "Tracks physiological stress and recovery",
                "Sleep" to "Differentiates sleep periods for overnight basal modulation"
            )

            permissions.forEach { (name, desc) ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (allGranted) Icons.Default.Check else Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (allGranted) "Re-grant Permissions" else "Grant Permissions")
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Health Connect Settings")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
