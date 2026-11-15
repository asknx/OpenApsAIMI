package app.aaps.plugins.main.general.manual

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.main.R
import dev.zacsweers.metro.Inject

class UserManualActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AapsTheme {
                UserManualScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    preferences = preferences
                )
            }
        }
    }
}

private data class ManualSection(val id: Int, val title: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualScreen(
    onBack: () -> Unit,
    preferences: Preferences? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }

    val sections = remember {
        val list = mutableListOf<ManualSection>()
        val sectionPairs = listOf(
            R.string.manual_section_1_title to R.string.manual_section_1_content,
            R.string.manual_section_2_title to R.string.manual_section_2_content,
            R.string.manual_section_3_title to R.string.manual_section_3_content,
            R.string.manual_section_4_title to R.string.manual_section_4_content,
            R.string.manual_section_5_title to R.string.manual_section_5_content,
            R.string.manual_section_6_title to R.string.manual_section_6_content,
            R.string.manual_section_7_title to R.string.manual_section_7_content,
            R.string.manual_section_8_title to R.string.manual_section_8_content,
            R.string.manual_section_9_title to R.string.manual_section_9_content,
            R.string.manual_section_10_title to R.string.manual_section_10_content,
            R.string.manual_section_11_title to R.string.manual_section_11_content,
            R.string.manual_section_12_title to R.string.manual_section_12_content,
            R.string.manual_section_13_title to R.string.manual_section_13_content,
            R.string.manual_section_14_title to R.string.manual_section_14_content,
            R.string.manual_section_15_title to R.string.manual_section_15_content,
            R.string.manual_section_16_title to R.string.manual_section_16_content,
            R.string.manual_section_17_title to R.string.manual_section_17_content,
            R.string.manual_section_18_title to R.string.manual_section_18_content,
            R.string.manual_section_19_title to R.string.manual_section_19_content,
            R.string.manual_section_20_title to R.string.manual_section_20_content,
            R.string.manual_section_21_title to R.string.manual_section_21_content,
            R.string.manual_section_22_title to R.string.manual_section_22_content,
        )

        sectionPairs.forEachIndexed { index, (tRes, cRes) ->
            try {
                val t = context.getString(tRes)
                val c = context.getString(cRes)
                list.add(ManualSection(index + 1, t, c))
            } catch (_: Exception) {}
        }
        list
    }

    val introText = remember {
        try { context.getString(R.string.manual_intro) } catch (_: Exception) { "" }
    }

    val filteredSections = if (searchQuery.isBlank()) sections else {
        sections.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Руководство OpenAPS AIMI",
                        modifier = Modifier.clickable {
                            tapCount++
                            if (tapCount == 5) {
                                Toast.makeText(context, "Осталось 2 нажатия до разблокировки инженерного режима AIMI", Toast.LENGTH_SHORT).show()
                            } else if (tapCount == 6) {
                                Toast.makeText(context, "Осталось 1 нажатие до разблокировки инженерного режима AIMI", Toast.LENGTH_SHORT).show()
                            } else if (tapCount >= 7) {
                                preferences?.put(BooleanKey.AimiDeveloperOptionsUnlocked, true)
                                Toast.makeText(context, "Переключатель инженерного режима разблокирован в меню", Toast.LENGTH_LONG).show()
                                tapCount = 0
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по руководству…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchQuery.isBlank() && introText.isNotBlank()) {
                    item {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = parseMarkdownToAnnotatedString(introText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                items(filteredSections, key = { it.id }) { section ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = parseMarkdownToAnnotatedString(section.content),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (filteredSections.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ничего не найдено по запросу \"$searchQuery\"")
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * Parses simple Markdown **bold** into AnnotatedString with FontWeight.Bold.
 * Strips raw ** characters so text renders cleanly in Compose.
 */
fun parseMarkdownToAnnotatedString(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("\\*\\*(.*?)\\*\\*")
        val matches = regex.findAll(raw)

        for (match in matches) {
            // Append text before the match
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }
            // Append bold text
            val boldContent = match.groupValues[1]
            val start = length
            append(boldContent)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)

            cursor = match.range.last + 1
        }

        // Append remainder
        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }
}
