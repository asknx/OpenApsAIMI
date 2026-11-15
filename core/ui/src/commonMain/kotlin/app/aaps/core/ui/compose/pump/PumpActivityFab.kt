package app.aaps.core.ui.compose.pump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSmallFab
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

/**
 * FAB indicating active pump communication or disconnected status.
 *
 * Visible when pump is communicating, bolus is in progress, or pump is disconnected.
 * When disconnected, shown in red without a spinning ring.
 * When communicating, a turning ring around the content shows that the exchange is running.
 * Tap opens the PumpActivityDialog.
 *
 * @see PreviewPumpFabIcon
 * @see PreviewPumpFabSmbPercent
 */
@Composable
fun PumpActivityFab(
    visible: Boolean,
    bolusState: BolusProgressState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDisconnected: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        val fabDescription = stringResource(CoreUiStrings.pump)
        val containerColor = if (isDisconnected) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (isDisconnected) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        AapsSmallFab(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = fabDescription },
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!isDisconnected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AapsSpacing.pumpFabRingSize),
                        strokeWidth = AapsSpacing.pumpFabRingStrokeWidth,
                        color = contentColor
                    )
                }
                if (isDisconnected) {
                    Icon(
                        imageVector = Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = contentColor
                    )
                } else if (bolusState != null && bolusState.isSMB && bolusState.percent in 1..99) {
                    Text(
                        text = stringResource(CoreUiStrings.units_format_percent, bolusState.percent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = contentColor
                    )
                }
            }
        }
    }
}
