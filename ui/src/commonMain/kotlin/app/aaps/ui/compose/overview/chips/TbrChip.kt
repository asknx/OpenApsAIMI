package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcNoTbr
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * @see TbrChipHighPreview
 * @see TbrChipLowPreview
 * @see TbrChipNonePreview
 */
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
fun TbrChip(
    state: TbrState,
    rateText: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = AapsTheme.elementColors.tempBasal
    val containerColor = iconColor.copy(alpha = 0.2f)
    val haptic = LocalHapticFeedback.current

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        val tbrState = stringResource(state.toDescription())
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier
                .height(AapsSpacing.chipHeight * LocalAapsScale.current)
                .semantics { stateDescription = tbrState }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = state.toIcon(),
                    contentDescription = stringResourceOrNull(ElementType.TEMP_BASAL.label()),
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                if (rateText.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * What the chip's icon means in words. Kept beside [toIcon] so the two cannot drift, and exhaustive
 * with no `else` so a new [TbrState] fails to compile here rather than silently going unspoken.
 */
private fun TbrState.toDescription(): TextRef = when (this) {
    TbrState.HIGH -> CoreUiStrings.tbr_state_above_profile
    TbrState.LOW  -> CoreUiStrings.tbr_state_below_profile
    TbrState.NONE -> CoreUiStrings.tbr_state_none
}

internal fun TbrState.toIcon(): ImageVector = when (this) {
    TbrState.HIGH -> IcTbrHigh
    TbrState.LOW  -> IcTbrLow
    TbrState.NONE -> IcNoTbr
}
