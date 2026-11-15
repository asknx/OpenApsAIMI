package app.aaps.ui.compose.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.LocalAppIcon
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import app.aaps.core.ui.compose.icons.IcPluginFood
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.description
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResourceOrNull
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.preference.rememberPreferenceBooleanState

@Composable
fun MainDrawer(
    /**
     * The drawer's state. With it the sheet closes the drawer on Back. Without it nothing took Back while
     * the drawer was open, and Back left the app.
     */
    drawerState: DrawerState,
    versionName: String,
    onNavigate: (NavigationRequest) -> Unit,
    isTreatmentsEnabled: Boolean,
    /** False on iOS, where the app may not close itself. See `MainViewModel.showExit`. */
    showExit: Boolean,
    modifier: Modifier = Modifier
) {
    val devUnlockedState = rememberPreferenceBooleanState(BooleanKey.AimiDeveloperOptionsUnlocked)
    val engineeringState = rememberPreferenceBooleanState(BooleanKey.AimiEngineeringBypassEnabled)

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = modifier.width(320.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LocalAppIcon.current(Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AAPS $versionName",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (devUnlockedState.value) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { engineeringState.value = !engineeringState.value }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Инженерный режим",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (engineeringState.value) "Включен (обход целей)" else "Выключен",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = engineeringState.value,
                            onCheckedChange = { engineeringState.value = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            DrawerMenuItem(ElementType.LOOP) { onNavigate(NavigationRequest.Element(ElementType.LOOP)) }
            DrawerMenuItem(ElementType.TREATMENTS, enabled = isTreatmentsEnabled) { onNavigate(NavigationRequest.Element(ElementType.TREATMENTS)) }
            DrawerMenuItem(ElementType.HISTORY_BROWSER) { onNavigate(NavigationRequest.Element(ElementType.HISTORY_BROWSER)) }
            DrawerMenuItem(ElementType.STATISTICS) { onNavigate(NavigationRequest.Element(ElementType.STATISTICS)) }
            DrawerMenuItem(ElementType.PROFILE_HELPER) { onNavigate(NavigationRequest.Element(ElementType.PROFILE_HELPER)) }
            DrawerMenuItem(ElementType.MAINTENANCE) { onNavigate(NavigationRequest.Element(ElementType.MAINTENANCE)) }
            DrawerMenuItem(ElementType.SETUP_WIZARD) { onNavigate(NavigationRequest.Element(ElementType.SETUP_WIZARD)) }
            DrawerMenuItem(ElementType.CONFIGURATION) { onNavigate(NavigationRequest.Element(ElementType.CONFIGURATION)) }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "OpenAPS AIMI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            DrawerMenuItem(
                icon = IcPluginOpenAPS,
                label = "AIMI 3.3",
                description = "Статус и расчеты контура",
                onClick = { onNavigate(NavigationRequest.Plugin("OpenAPSAIMIPlugin")) }
            )
            DrawerMenuItem(
                icon = Icons.Default.Settings,
                label = "Настройки AIMI",
                description = "Конфигурация AI, SOS, Physio и параметров",
                onClick = { onNavigate(NavigationRequest.PluginPreferences("openapsaimi_settings")) }
            )
            DrawerMenuItem(
                icon = IcPluginFood,
                label = "AIMI Meal Advisor",
                description = "AI анализ приёма пищи",
                onClick = { onNavigate(NavigationRequest.Custom("meal_advisor")) }
            )
            DrawerMenuItem(
                icon = IcProfile,
                label = "AIMI Advisor",
                description = "Анализ профиля и рекомендации",
                onClick = { onNavigate(NavigationRequest.Custom("profile_advisor")) }
            )
            DrawerMenuItem(
                icon = IcActivity,
                label = "Контекст AIMI",
                description = "Физиологический и внешний контекст",
                onClick = { onNavigate(NavigationRequest.Custom("aimi_context")) }
            )
            DrawerMenuItem(
                icon = IcStats,
                label = "Плагин сравнения AIMI-SMB",
                description = "Сравнение метрик и решений",
                onClick = { onNavigate(NavigationRequest.Custom("comparator")) }
            )
            DrawerMenuItem(
                icon = IcCalculator,
                label = "Коррекции (Adjustments)",
                description = "Детали решений и аудит",
                onClick = { onNavigate(NavigationRequest.Custom("adjustment_details")) }
            )
            DrawerMenuItem(
                icon = IcAutomation,
                label = "Режимы AIMI",
                description = "Управление активными режимами",
                onClick = { onNavigate(NavigationRequest.Custom("aimi_modes")) }
            )
            DrawerMenuItem(
                icon = IcClinicalNotes,
                label = "Руководство пользователя",
                description = "Инструкция OpenAPS AIMI",
                onClick = { onNavigate(NavigationRequest.Custom("user_manual")) }
            )
            DrawerMenuItem(
                icon = IcQuestion,
                label = "Расшифровка аббревиатур",
                description = "Словарь терминов и сокращений",
                onClick = { onNavigate(NavigationRequest.Custom("abbreviations")) }
            )
        }

        // Bottom section with About and Exit
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(ElementType.ABOUT) { onNavigate(NavigationRequest.Element(ElementType.ABOUT)) }
        if (showExit) DrawerMenuItem(ElementType.EXIT) { onNavigate(NavigationRequest.Element(ElementType.EXIT)) }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** DrawerMenuItem that derives icon, label, and description from [ElementType]. */
@Composable
private fun DrawerMenuItem(
    elementType: ElementType,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val desc = elementType.description()
    DrawerMenuItem(
        icon = elementType.icon(),
        label = (stringResourceOrNull(elementType.label()) ?: ""),
        description = stringResourceOrNull(desc),
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
) {
    val iconPainter = icon?.let { rememberVectorPainter(it) }
        ?: error("DrawerMenuItem requires an icon")
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = if (description != null) 8.dp else 12.dp)
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint
                )
            }
        }
    }
}
