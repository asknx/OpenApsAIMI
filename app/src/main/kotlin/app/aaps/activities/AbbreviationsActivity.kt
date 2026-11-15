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

class AbbreviationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AapsTheme {
                AbbreviationsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}

private data class AbbreviationEntry(val abbr: String, val full: String, val description: String, val category: String = "General")

private val ABBREVIATIONS = listOf(
    // Core Diabetes
    AbbreviationEntry("BG", "Blood Glucose", "Current glucose level in mg/dL or mmol/L", "Glucose"),
    AbbreviationEntry("IOB", "Insulin On Board", "Total active insulin in the body from previous doses", "Insulin"),
    AbbreviationEntry("COB", "Carbs On Board", "Active carbohydrates being absorbed", "Carbs"),
    AbbreviationEntry("SMB", "Super Micro Bolus", "Small automated insulin doses from the closed loop", "Insulin"),
    AbbreviationEntry("TBR", "Temporary Basal Rate", "Adjusted basal rate set by the loop algorithm", "Insulin"),
    AbbreviationEntry("TT", "Temporary Target", "Short-term BG target, e.g. for exercise or meals", "Settings"),
    AbbreviationEntry("DIA", "Duration of Insulin Action", "Time insulin remains active in the body", "Insulin"),
    AbbreviationEntry("ISF", "Insulin Sensitivity Factor", "How much BG drops per 1U of insulin", "Settings"),
    AbbreviationEntry("IC", "Insulin-to-Carb ratio", "Units of insulin per gram of carbohydrates", "Settings"),
    AbbreviationEntry("UAM", "Unannounced Meal", "Algorithm detection of unreported carb intake", "Algorithm"),
    AbbreviationEntry("TDD", "Total Daily Dose", "Total insulin delivered in 24 hours", "Insulin"),
    AbbreviationEntry("TIR", "Time In Range", "Percentage of time BG is within 70-180 mg/dL", "Statistics"),
    AbbreviationEntry("GMI", "Glucose Management Indicator", "Estimated HbA1c from CGM data", "Statistics"),
    AbbreviationEntry("CV", "Coefficient of Variation", "BG variability measure; <36% is target", "Statistics"),
    AbbreviationEntry("CGM", "Continuous Glucose Monitor", "Device that measures glucose every 5 minutes", "Hardware"),
    AbbreviationEntry("APS", "Artificial Pancreas System", "Automated insulin delivery system", "System"),
    AbbreviationEntry("GVI", "Glycemic Variability Index", "Composite measure of glucose variability", "Statistics"),
    AbbreviationEntry("AS", "Autosens", "Dynamic sensitivity adjustment from recent BG trend", "Algorithm"),
    AbbreviationEntry("NS", "Nightscout", "Open-source remote monitoring platform", "System"),
    // AIMI Specific
    AbbreviationEntry("AIMI", "AI-driven Micro Insulin", "Custom OpenAPS fork with neural net decision layer", "AIMI"),
    AbbreviationEntry("PKPD", "Pharmacokinetics/Pharmacodynamics", "Drug absorption and effect model used by AIMI", "AIMI"),
    AbbreviationEntry("MPC", "Model Predictive Control", "Control algorithm that predicts future BG to plan doses", "AIMI"),
    AbbreviationEntry("HRV", "Heart Rate Variability", "Autonomic nervous system proxy used for stress/sleep detection", "AIMI"),
    AbbreviationEntry("WCycle", "Women's Cycle Module", "Hormonal phase-based ISF adjustment in AIMI", "AIMI"),
    AbbreviationEntry("NightGrowth", "Night Growth Hormone Surge", "AIMI correction for GH-driven overnight BG rise", "AIMI"),
    AbbreviationEntry("DualBrain", "Dual-Brain Auditor", "AIMI safety layer that cross-checks APS output", "AIMI"),
    AbbreviationEntry("T3C", "Trajectory Convergence ETA", "Minutes until BG is projected to reach target", "AIMI"),
    AbbreviationEntry("RT", "Raw Trajectory data", "Internal AIMI data structure carrying trajectory metadata", "AIMI"),
    AbbreviationEntry("SMR", "Scoring Mode Reference", "Comparative scoring mode for AIMI vs SMB evaluation", "AIMI"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbbreviationsScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All") + ABBREVIATIONS.map { it.category }.distinct().sorted()
    val filtered = ABBREVIATIONS.filter { entry ->
        (selectedCategory == "All" || entry.category == selectedCategory) &&
            (searchQuery.isEmpty() ||
                entry.abbr.contains(searchQuery, ignoreCase = true) ||
                entry.full.contains(searchQuery, ignoreCase = true) ||
                entry.description.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abbreviations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search abbreviations…") },
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

            // Category chips
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // List
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.abbr }) { entry ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Abbreviation badge
                            Surface(
                                color = if (entry.category == "AIMI")
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = entry.abbr,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (entry.category == "AIMI")
                                        MaterialTheme.colorScheme.tertiary
                                    else
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            // Description
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.full, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No results for \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
