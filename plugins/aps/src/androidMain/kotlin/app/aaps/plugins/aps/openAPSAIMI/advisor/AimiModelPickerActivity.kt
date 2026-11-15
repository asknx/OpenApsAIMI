package app.aaps.plugins.aps.openAPSAIMI.advisor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorAIService
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AimiModelPickerActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var auditorAIService: AuditorAIService

    private val isLoading = mutableStateOf(true)
    private val modelsList = mutableStateOf<List<String>>(emptyList())
    private val selectedModel = mutableStateOf("")
    private val manualModelText = mutableStateOf("")
    private val searchQuery = mutableStateOf("")
    private val statusMessage = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentManual = preferences.get(StringKey.AimiAdvisorModelCustomManual).trim()
        val currentList = preferences.get(StringKey.AimiAdvisorModelCustom).trim()
        val initial = currentManual.ifBlank { currentList.ifBlank { "custom-model" } }
        selectedModel.value = initial
        manualModelText.value = currentManual

        loadModels()

        setContent {
            AapsTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Каталог моделей", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Кастомный Endpoint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                }
                            },
                            actions = {
                                IconButton(onClick = { loadModels() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Manual input section
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Ручной ввод модели",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = manualModelText.value,
                                    onValueChange = {
                                        manualModelText.value = it
                                        selectedModel.value = it.trim()
                                    },
                                    label = { Text("Имя модели") },
                                    placeholder = { Text("например: llama-3.3-70b-instruct") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { applyModel(manualModelText.value.trim()) },
                                    enabled = manualModelText.value.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Установить ручную модель")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search and list section
                        OutlinedTextField(
                            value = searchQuery.value,
                            onValueChange = { searchQuery.value = it },
                            label = { Text("Поиск модели в каталоге") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (statusMessage.value.isNotBlank()) {
                            Text(
                                text = statusMessage.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (isLoading.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Загрузка доступных моделей с сервера...")
                                }
                            }
                        } else {
                            val filtered = modelsList.value.filter {
                                it.contains(searchQuery.value, ignoreCase = true)
                            }

                            if (filtered.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (modelsList.value.isEmpty()) "Список моделей пуст или сервер недоступен" else "Ничего не найдено",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(filtered) { model ->
                                        val isSelected = (model == selectedModel.value)
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable {
                                                    selectedModel.value = model
                                                    manualModelText.value = model
                                                    applyModel(model)
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedModel.value = model
                                                        manualModelText.value = model
                                                        applyModel(model)
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = model,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadModels() {
        isLoading.value = true
        statusMessage.value = "Запрос к серверу..."
        lifecycleScope.launch {
            try {
                val customUrl = preferences.get(StringKey.AimiAdvisorCustomUrl).trim()
                if (customUrl.isBlank() || (!customUrl.startsWith("http://") && !customUrl.startsWith("https://"))) {
                    withContext(Dispatchers.Main) {
                        isLoading.value = false
                        statusMessage.value = "Укажите корректный URL кастомного API в настройках"
                        Toast.makeText(this@AimiModelPickerActivity, "Укажите URL кастомного API в настройках", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val fetched = auditorAIService.fetchAvailableModels(AuditorAIService.Provider.CUSTOM)
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    if (fetched.isNotEmpty()) {
                        modelsList.value = fetched
                        statusMessage.value = "Получено моделей: ${fetched.size}"
                    } else {
                        // Fallback to default custom models list
                        modelsList.value = listOf("custom-model", "gpt-4o", "llama-3.3-70b", "qwen-2.5-72b", "deepseek-r1")
                        statusMessage.value = "Сервер не вернул /models. Показан базовый список."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    modelsList.value = listOf("custom-model", "gpt-4o", "llama-3.3-70b", "qwen-2.5-72b", "deepseek-r1")
                    statusMessage.value = "Ошибка подключения: ${e.message}"
                }
            }
        }
    }

    private fun applyModel(model: String) {
        if (model.isBlank()) return
        preferences.put(StringKey.AimiAdvisorModelCustomManual, model)
        preferences.put(StringKey.AimiAdvisorModelCustom, model)
        preferences.put(StringKey.AimiAdvisorModel, model)
        Toast.makeText(this, "Модель установлена: $model", Toast.LENGTH_SHORT).show()
        finish()
    }
}
