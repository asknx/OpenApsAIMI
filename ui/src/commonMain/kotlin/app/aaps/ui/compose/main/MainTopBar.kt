package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcSettingsOff
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.search.M3SearchBar
import app.aaps.ui.search.SearchUiState

import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import app.aaps.core.ui.compose.navigation.NavigationRequest

/**
 * Main top bar with M3-style search bar.
 * Layout: [Menu] [----Search Bar----] [SimpleMode?] [Settings] [MoreAIMI]
 *
 * @param searchUiState Current search UI state
 * @param onMenuClick Called when menu button is clicked
 * @param onPreferencesClick Called when preferences button is clicked
 * @param onSearchQueryChange Called when search query changes
 * @param onSearchClear Called when search query is cleared
 * @param onSearchActiveChange Called when search active state changes
 * @param isSimpleMode When true, shows a non-interactive simple-mode indicator next to Settings
 * @param onNavigate Optional navigation callback for AIMI menu actions
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    searchUiState: SearchUiState,
    onMenuClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    isSimpleMode: Boolean = false,
    onNavigate: ((NavigationRequest) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                M3SearchBar(
                    query = searchUiState.query,
                    isActive = searchUiState.isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onClearClick = onSearchClear,
                    onActiveChange = onSearchActiveChange,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(CoreUiStrings.open_navigation)
                )
            }
        },
        actions = {
            IconButton(onClick = onPreferencesClick) {
                // In simple mode the gear shows "crossed" (IcSettingsOff) to signal the mode;
                // the button action (open settings) is unchanged.
                Icon(
                    imageVector = if (isSimpleMode) IcSettingsOff else Icons.Default.Settings,
                    contentDescription = stringResource(CoreUiStrings.settings)
                )
            }
            if (onNavigate != null) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "OpenAPS AIMI"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("AIMI Meal Advisor") },
                            leadingIcon = { Icon(Icons.Default.Restaurant, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("meal_advisor"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("AIMI Advisor") },
                            leadingIcon = { Icon(Icons.Default.Psychology, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("profile_advisor"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Контекст AIMI") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("aimi_context"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Плагин сравнения AIMI-SMB") },
                            leadingIcon = { Icon(Icons.Default.CompareArrows, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("comparator"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Коррекции (Adjustments)") },
                            leadingIcon = { Icon(Icons.Default.Analytics, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("adjustment_details"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Режимы AIMI") },
                            leadingIcon = { Icon(Icons.Default.Tune, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("aimi_modes"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Руководство пользователя") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("user_manual"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Расшифровка аббревиатур") },
                            leadingIcon = { Icon(Icons.Default.HelpOutline, null) },
                            onClick = {
                                menuExpanded = false
                                onNavigate(NavigationRequest.Custom("abbreviations"))
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0),
        modifier = modifier
    )
}
