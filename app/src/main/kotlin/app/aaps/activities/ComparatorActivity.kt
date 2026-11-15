package app.aaps.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme

class ComparatorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AapsTheme {
                ComparatorScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}

data class ComparisonResult(
    val metric: String,
    val aimiValue: String,
    val smbValue: String,
    val winner: Winner
) {
    enum class Winner { AIMI, SMB, TIE }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparatorScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedWindow by remember { mutableStateOf("7d") }

    // Sample comparison data (would be loaded from CSV via ComparisonCsvParser in production)
    val results = remember {
        listOf(
            ComparisonResult("TIR (70-180)", "78.3%", "71.2%", ComparisonResult.Winner.AIMI),
            ComparisonResult("Avg BG (mg/dL)", "118.4", "124.7", ComparisonResult.Winner.AIMI),
            ComparisonResult("CV (%)", "22.1", "24.8", ComparisonResult.Winner.AIMI),
            ComparisonResult("Hypo events <54", "0.3/day", "0.7/day", ComparisonResult.Winner.AIMI),
            ComparisonResult("SMB deliveries/day", "14.2", "11.8", ComparisonResult.Winner.SMB),
            ComparisonResult("Total daily dose (U)", "32.4", "31.1", ComparisonResult.Winner.SMB),
            ComparisonResult("GMI (HbA1c est.)", "5.9%", "6.1%", ComparisonResult.Winner.AIMI),
            ComparisonResult("Time above 250", "1.2%", "2.8%", ComparisonResult.Winner.AIMI),
            ComparisonResult("AIMI Score", "84/100", "N/A", ComparisonResult.Winner.AIMI),
        )
    }

    val filteredResults = if (searchQuery.isEmpty()) results
    else results.filter { it.metric.contains(searchQuery, ignoreCase = true) }

    val aimiWins = results.count { it.winner == ComparisonResult.Winner.AIMI }
    val smbWins = results.count { it.winner == ComparisonResult.Winner.SMB }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AIMI vs SMB Comparator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* export CSV */ }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Score header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScoreCard(
                        label = "AIMI",
                        score = aimiWins,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ScoreCard(
                        label = "SMB",
                        score = smbWins,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Time window chips
            item {
                val windows = listOf("1d", "7d", "14d", "30d")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    windows.forEach { w ->
                        FilterChip(
                            selected = selectedWindow == w,
                            onClick = { selectedWindow = w },
                            label = { Text(w) }
                        )
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Filter metrics…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }

            // Header row
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Metric", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("AIMI", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("SMB", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Result rows
            items(filteredResults) { result ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(result.metric, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)

                        val aimiColor = when (result.winner) {
                            ComparisonResult.Winner.AIMI -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val smbColor = when (result.winner) {
                            ComparisonResult.Winner.SMB -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Text(
                            result.aimiValue,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (result.winner == ComparisonResult.Winner.AIMI) FontWeight.Bold else FontWeight.Normal,
                            color = aimiColor
                        )
                        Text(
                            result.smbValue,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (result.winner == ComparisonResult.Winner.SMB) FontWeight.Bold else FontWeight.Normal,
                            color = smbColor
                        )
                    }
                }
            }

            // Empty state
            if (filteredResults.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No data found for \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ScoreCard(label: String, score: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
            Text("$score wins", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}
