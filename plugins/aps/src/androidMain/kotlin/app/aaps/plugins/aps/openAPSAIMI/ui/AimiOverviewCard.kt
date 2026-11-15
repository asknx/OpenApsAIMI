package app.aaps.plugins.aps.openAPSAIMI.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorStatusTracker
import app.aaps.plugins.aps.openAPSAIMI.physio.AIMIPhysioManagerMTR
import kotlinx.coroutines.delay

/**
 * Native Jetpack Compose M3 overview card for OpenAPS AIMI.
 * Integrates:
 * 1. Dual-Brain Auditor Status Badge (clickable -> AdjustmentDetailsActivity)
 * 2. Quick action "Meal Advisor" with camera icon (clickable -> MealAdvisorActivity)
 * 3. Compact physiological metrics row: Steps, HR, HRV, Activity Context (clickable -> ContextActivity)
 */
@Composable
fun AimiOverviewCard(
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var auditorStatus by remember { mutableStateOf(AuditorStatusTracker.getStatus().first) }
    var physioSnapshot by remember {
        mutableStateOf(AIMIPhysioManagerMTR.instance?.repo?.getLastSnapshot())
    }
    var physioContext by remember {
        mutableStateOf(AIMIPhysioManagerMTR.instance?.contextStore?.getLastContextUnsafe())
    }

    LaunchedEffect(Unit) {
        while (true) {
            auditorStatus = AuditorStatusTracker.getStatus().first
            physioSnapshot = AIMIPhysioManagerMTR.instance?.repo?.getLastSnapshot()
            physioContext = AIMIPhysioManagerMTR.instance?.contextStore?.getLastContextUnsafe()
            delay(3000L)
        }
    }

    val badgeInfo: Triple<Color, Color, String> = when {
        auditorStatus.isActive() ->
            Triple(Color(0xFF0F392B), Color(0xFF4ADE80), "Auditor: OK")
        auditorStatus.isSkipped() ->
            Triple(Color(0xFF1E3A8A), Color(0xFF60A5FA), "Auditor: SKIP")
        auditorStatus == AuditorStatusTracker.Status.STALE ->
            Triple(Color(0xFF451A03), Color(0xFFFBBF24), "Auditor: STALE")
        auditorStatus.isError() ->
            Triple(Color(0xFF4C0519), Color(0xFFF87171), "Auditor: ERR")
        auditorStatus.isOffline() ->
            Triple(Color(0xFF27272A), Color(0xFFA1A1AA), "Auditor: OFFLINE")
        else ->
            Triple(Color(0xFF27272A), Color(0xFFA1A1AA), "Auditor: OFF")
    }
    val (badgeBg, badgeFg, badgeText) = badgeInfo

    val currentSnapshot = physioSnapshot
    val currentContext = physioContext
    val stepsStr = if (currentSnapshot != null && currentSnapshot.stepsLast15m > 0) "${currentSnapshot.stepsLast15m}" else "--"
    val hrStr = if (currentSnapshot != null && currentSnapshot.hrNow > 0) "${currentSnapshot.hrNow.toInt()}" else "--"
    val hrvStr = if (currentSnapshot != null && currentSnapshot.hrvRmssd > 0) "${currentSnapshot.hrvRmssd.toInt()}" else "--"
    val stateStr = currentContext?.state?.name?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Normal"

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top row: Auditor Badge & Quick Meal Advisor button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auditor Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(NavigationRequest.Custom("adjustment_details")) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            color = badgeFg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Meal Advisor Quick Action Button
                FilledTonalButton(
                    onClick = { onNavigate(NavigationRequest.Custom("meal_advisor")) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Meal Advisor",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Bottom row: Physiological Metrics (Steps, HR, HRV, Activity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigate(NavigationRequest.Custom("aimi_context")) }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhysioMetricItem(
                    icon = Icons.Default.DirectionsWalk,
                    label = "Steps",
                    value = stepsStr,
                    modifier = Modifier.weight(1f)
                )
                PhysioMetricItem(
                    icon = Icons.Default.Favorite,
                    label = "HR",
                    value = if (hrStr != "--") "$hrStr bpm" else "--",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.weight(1f)
                )
                PhysioMetricItem(
                    icon = Icons.Default.Timeline,
                    label = "HRV",
                    value = if (hrvStr != "--") "$hrvStr ms" else "--",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f)
                )
                PhysioMetricItem(
                    icon = Icons.Default.FitnessCenter,
                    label = "Context",
                    value = stateStr,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
private fun PhysioMetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
