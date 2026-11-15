package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.meal.ui.MealAdvisorScreen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round

class MealAdvisorActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var persistenceLayer: app.aaps.core.interfaces.db.PersistenceLayer
    @Inject lateinit var profileFunction: app.aaps.core.interfaces.profile.ProfileFunction
    @Inject lateinit var dateUtil: app.aaps.core.interfaces.utils.DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uiInteraction: app.aaps.core.interfaces.ui.UiInteraction
    @Inject lateinit var loop: app.aaps.core.interfaces.aps.Loop
    @Inject lateinit var commandQueue: app.aaps.core.interfaces.queue.CommandQueue
    @Inject lateinit var geminiOAuthManager: app.aaps.plugins.aps.openAPSAIMI.llm.gemini.GeminiOAuthManager
    @Inject lateinit var iobCobCalculator: app.aaps.core.interfaces.iob.IobCobCalculator
    @Inject lateinit var fatSecretService: FatSecretService
    @Inject lateinit var openFoodFactsService: OpenFoodFactsService

    private lateinit var recognitionService: FoodRecognitionService
    private val requestImageCapture = 1
    private val requestImagePick = 2
    private val requestBarcodeScan = 3

    private val selectedBitmap = mutableStateOf<Bitmap?>(null)
    private val description = mutableStateOf("")
    private val isAnalyzing = mutableStateOf(false)
    private val currentEstimate = mutableStateOf<EstimationResult?>(null)
    private val carbsText = mutableStateOf("")
    private val proposalButtonText = mutableStateOf("CONFIRM INJECTION")
    private val reasoningText = mutableStateOf("")
    private val showConfirmDialog = mutableStateOf(false)
    private val confirmDialogMessage = mutableStateOf("")
    private val showFatSecretBlockedDialog = mutableStateOf(false)
    private val showProviderDialog = mutableStateOf(false)
    private val activeProviderName = mutableStateOf("")

    private var lastCalculatedDose: MealDose? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeProviderName.value = preferences.get(app.aaps.core.keys.StringKey.AimiAdvisorProvider)
        recognitionService = FoodRecognitionService(this, preferences, persistenceLayer, geminiOAuthManager)

        setContent {
            AapsTheme {
                MealAdvisorScreen(
                    selectedBitmap = selectedBitmap.value,
                    description = description.value,
                    onDescriptionChange = { description.value = it },
                    isAnalyzing = isAnalyzing.value,
                    onAnalyzeClick = {
                        selectedBitmap.value?.let { simulateAnalysis(it) }
                    },
                    onCameraClick = { dispatchTakePictureIntent() },
                    onBarcodeClick = { dispatchBarcodeScanIntent() },
                    onGalleryClick = { dispatchPickPictureIntent() },
                    currentEstimate = currentEstimate.value,
                    carbsText = carbsText.value,
                    onCarbsChange = {
                        carbsText.value = it
                        recalculateProposal()
                    },
                    proposalButtonText = proposalButtonText.value,
                    reasoningText = reasoningText.value,
                    onConfirmClick = { confirmInjection() },
                    onBackClick = { finish() },
                    showConfirmDialog = showConfirmDialog.value,
                    confirmDialogMessage = confirmDialogMessage.value,
                    onDismissConfirmDialog = { showConfirmDialog.value = false },
                    onDirectBolusClick = {
                        showConfirmDialog.value = false
                        val valCarbs = carbsText.value.toDoubleOrNull() ?: return@MealAdvisorScreen
                        val dose = lastCalculatedDose
                        performInjection(valCarbs, dose?.totalInsulin ?: 0.0, isDirectBolus = true)
                    },
                    onAimiProposeClick = {
                        showConfirmDialog.value = false
                        val valCarbs = carbsText.value.toDoubleOrNull() ?: return@MealAdvisorScreen
                        val dose = lastCalculatedDose
                        performInjection(valCarbs, dose?.totalInsulin ?: 0.0, isDirectBolus = false, isAimiPropose = true)
                    },
                    onRecordOnlyClick = {
                        showConfirmDialog.value = false
                        val valCarbs = carbsText.value.toDoubleOrNull() ?: return@MealAdvisorScreen
                        val dose = lastCalculatedDose
                        performInjection(valCarbs, dose?.totalInsulin ?: 0.0, isDirectBolus = false, isAimiPropose = false)
                    },
                    showFatSecretBlockedDialog = showFatSecretBlockedDialog.value,
                    onDismissFatSecretDialog = { showFatSecretBlockedDialog.value = false },
                    activeProviderName = activeProviderName.value,
                    onSwitchProviderClick = { showProviderDialog.value = true }
                )

                if (showProviderDialog.value) {
                    val providers = listOf("GEMINI", "OPENAI", "DEEPSEEK", "CLAUDE", "GLM", "GROK", "OPENROUTER", "GROQ", "CUSTOM")
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showProviderDialog.value = false },
                        title = { androidx.compose.material3.Text("Select AI Provider") },
                        text = {
                            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                providers.forEach { prov ->
                                    androidx.compose.foundation.layout.Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                preferences.put(app.aaps.core.keys.StringKey.AimiAdvisorProvider, prov)
                                                activeProviderName.value = prov
                                                showProviderDialog.value = false
                                            }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = activeProviderName.value.equals(prov, ignoreCase = true),
                                            onClick = {
                                                preferences.put(app.aaps.core.keys.StringKey.AimiAdvisorProvider, prov)
                                                activeProviderName.value = prov
                                                showProviderDialog.value = false
                                            }
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                                        androidx.compose.material3.Text(prov)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showProviderDialog.value = false }) {
                                androidx.compose.material3.Text("Close")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != 0) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            val intent = Intent(this, MealAdvisorCameraActivity::class.java).apply {
                putExtra(MealAdvisorCameraActivity.EXTRA_MODE, MealAdvisorCameraActivity.MODE_CAMERA)
            }
            startActivityForResult(intent, requestImageCapture)
        }
    }

    private fun dispatchPickPictureIntent() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, requestImagePick)
    }

    private fun dispatchBarcodeScanIntent() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != 0) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            val intent = Intent(this, MealAdvisorCameraActivity::class.java).apply {
                putExtra(MealAdvisorCameraActivity.EXTRA_MODE, MealAdvisorCameraActivity.MODE_BARCODE)
            }
            startActivityForResult(intent, requestBarcodeScan)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                requestImageCapture -> {
                    data?.data?.let { uri ->
                        contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)?.let { bmp ->
                                selectedBitmap.value = bmp
                            }
                        }
                    } ?: (data?.extras?.get("data") as? Bitmap)?.let { bmp ->
                        selectedBitmap.value = bmp
                    }
                }
                requestImagePick -> {
                    data?.data?.let { uri ->
                        contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)?.let { bmp ->
                                selectedBitmap.value = bmp
                            }
                        }
                    }
                }
                requestBarcodeScan -> {
                    data?.getStringExtra("barcode")?.let { barcode ->
                        Toast.makeText(this@MealAdvisorActivity, "Barcode found: $barcode", Toast.LENGTH_SHORT).show()
                        fetchBarcodeData(barcode)
                    }
                }
            }
        }
    }

    private fun analyzeBarcode(bitmap: Bitmap) {
        Toast.makeText(this, "Scanning Barcode...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val barcode = BarcodeScanner.scanBarcode(bitmap)
            if (barcode != null) {
                Toast.makeText(this@MealAdvisorActivity, "Barcode found: $barcode", Toast.LENGTH_SHORT).show()
                fetchBarcodeData(barcode)
            } else {
                Toast.makeText(this@MealAdvisorActivity, "No barcode detected. Try again or use standard analysis.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchBarcodeData(barcode: String) {
        val dbMode = preferences.get(app.aaps.core.keys.StringKey.AimiFoodDatabaseProvider)
        val providerName = when (dbMode) {
            "FATSECRET" -> "FatSecret"
            "MIXED" -> "OpenFoodFacts + FatSecret"
            else -> "Open Food Facts"
        }

        Toast.makeText(this, "Поиск в базе $providerName...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            var product: FatSecretService.ProductInfo? = null
            var resolvedProvider = "Open Food Facts"

            when (dbMode) {
                "FATSECRET" -> {
                    product = fatSecretService.findProductByBarcode(barcode)
                    resolvedProvider = "FatSecret"
                }
                "MIXED" -> {
                    // Sequential lookup: try OpenFoodFacts first; if not found, fall back to FatSecret
                    val offProduct = openFoodFactsService.findProductByBarcode(barcode)
                    if (offProduct != null) {
                        product = FatSecretService.ProductInfo(
                            offProduct.name,
                            offProduct.carbs,
                            offProduct.protein,
                            offProduct.fat,
                            offProduct.calories,
                            offProduct.servingDescription
                        )
                        resolvedProvider = "Open Food Facts"
                    } else {
                        product = fatSecretService.findProductByBarcode(barcode)
                        resolvedProvider = "FatSecret"
                    }
                }
                else -> { // "OPENFOODFACTS"
                    val offProduct = openFoodFactsService.findProductByBarcode(barcode)
                    if (offProduct != null) {
                        product = FatSecretService.ProductInfo(
                            offProduct.name,
                            offProduct.carbs,
                            offProduct.protein,
                            offProduct.fat,
                            offProduct.calories,
                            offProduct.servingDescription
                        )
                    }
                    resolvedProvider = "Open Food Facts"
                }
            }

            if (product != null) {
                if (product.name == "IP_BLOCKED") {
                    showFatSecretBlockedDialog.value = true
                    return@launch
                }

                val barcodeInfo = "$resolvedProvider Data: ${product.name} " +
                        "(${product.carbs}g C, ${product.protein}g P, ${product.fat}g F, ${product.calories}kcal per ${product.servingDescription})."

                val currentText = description.value.trim()
                val newText = if (currentText.isEmpty()) "$resolvedProvider: ${product.name}" else "$currentText ($resolvedProvider: ${product.name})"
                description.value = newText

                Toast.makeText(this@MealAdvisorActivity, "Продукт найден: ${product.name}", Toast.LENGTH_LONG).show()

                if (selectedBitmap.value != null) {
                    simulateAnalysis(selectedBitmap.value!!, barcodeInfo)
                } else {
                    updateUIWithBarcodeData(product, resolvedProvider)
                    Toast.makeText(this@MealAdvisorActivity, "Данные о пищевой ценности получены.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this@MealAdvisorActivity, "Продукт не найден в базе данных ($providerName).", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUIWithBarcodeData(product: FatSecretService.ProductInfo, providerName: String) {
        val dummyMacro = MacroRange(product.carbs, product.carbs, product.carbs)
        val dummyProtein = MacroRange(product.protein, product.protein, product.protein)
        val dummyFat = MacroRange(product.fat, product.fat, product.fat)

        val result = EstimationResult(
            description = product.name,
            visibleItems = listOf(VisibleFoodItem(product.name, product.servingDescription)),
            uncertainItems = emptyList(),
            carbs = dummyMacro,
            protein = dummyProtein,
            fat = dummyFat,
            fpuEquivalent = round((product.fat * 9.0 + product.protein * 4.0) / 10.0 * 2.0) / 2.0,
            glycemicIndex = "MEDIUM",
            absorptionSpeed = "MIXED",
            confidence = "HIGH",
            portionConfidence = "HIGH",
            hiddenCarbRisk = "LOW",
            needsManualConfirmation = false,
            insulinRelevantNotes = emptyList(),
            reasoning = "Data directly from $providerName database (${product.servingDescription}).",
            recommendedCarbsForDose = product.carbs,
            recommendedCarbsReason = "Using exact $providerName values."
        )
        currentEstimate.value = result
        updateUIWithResult(result)
    }

    private fun simulateAnalysis(bitmap: Bitmap, customInfo: String = "") {
        val rawDesc = description.value.trim()
        val userDesc = if ((rawDesc.length >= 3) && rawDesc.any { it.isLetter() }) rawDesc else ""
        val finalDesc = if (customInfo.isNotEmpty()) "$customInfo. User Note: $userDesc" else userDesc

        Toast.makeText(this, "AI Analysis in progress...", Toast.LENGTH_SHORT).show()
        isAnalyzing.value = true

        lifecycleScope.launch {
            try {
                val result = recognitionService.estimateCarbsFromImage(bitmap, finalDesc)
                currentEstimate.value = result
                updateUIWithResult(result)
            } catch (e: Exception) {
                Toast.makeText(this@MealAdvisorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    private fun updateUIWithResult(result: EstimationResult) {
        carbsText.value = result.recommendedCarbsForDose.toInt().toString()
        reasoningText.value = "Rationale: ${result.reasoning}"
        recalculateProposal()
    }

    data class MealDose(
        val mealInsulin: Double,
        val correctionInsulin: Double,
        val totalIob: Double,
        val currentCob: Double,
        val netIob: Double,
        val totalInsulin: Double
    )

    private suspend fun calculateMealDose(carbs: Double): MealDose? {
        val profile = profileFunction.getProfile() ?: return null
        val ic = if (profile.getIc() > 0.1) profile.getIc() else 10.0
        val isf = profile.getIsfMgdl("MealAdvisor")

        val mealInsulin = carbs / ic
        val lastBg = persistenceLayer.getLastGlucoseValue()?.value ?: 100.0
        val target = profile.getTargetMgdl()

        val bolusIob = iobCobCalculator.calculateIobFromBolus().iob
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().basaliob
        val totalIob = bolusIob + basalIob

        val currentCob = iobCobCalculator.getMealDataWithWaitingForCalculationFinish().mealCOB

        val insulinReservedForCob = currentCob / ic
        val netIob = (totalIob - insulinReservedForCob).coerceAtLeast(0.0)

        val correctionInsulin = if (isf > 1.0) (lastBg - target) / isf else 0.0
        val totalInsulin = (mealInsulin + correctionInsulin - netIob).coerceAtLeast(0.0)

        return MealDose(mealInsulin, correctionInsulin, totalIob, currentCob, netIob, totalInsulin)
    }

    private fun recalculateProposal() {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val carbs = carbsText.value.toDoubleOrNull() ?: 0.0
                val dose = calculateMealDose(carbs)
                lastCalculatedDose = dose

                withContext(Dispatchers.Main) {
                    if (dose == null) {
                        proposalButtonText.value = "CONFIRM INJECTION"
                        return@withContext
                    }

                    if (Math.abs(dose.correctionInsulin) > 0.1 || Math.abs(dose.totalIob) > 0.1 || dose.currentCob > 0.1) {
                        val cobText = if (dose.currentCob > 1.0) " (COB: ${dose.currentCob.toInt()}г)" else ""
                        proposalButtonText.value = "ВВЕСТИ ${carbs.toInt()}г УГЛЕВОДОВ$cobText\nЕда: %.1fед + Корр: %.1fед - ЧистыйIOB: %.1fед = %.1f ед"
                            .format(dose.mealInsulin, dose.correctionInsulin, dose.netIob, dose.totalInsulin)

                        if (dose.currentCob > 1.0 && dose.totalIob > 0.1) {
                            reasoningText.value = "Обоснование: ${currentEstimate.value?.reasoning ?: ""}\n" +
                                    "IOB (%.1fед) частично сбалансирован COB (%dг), Чистый IOB: %.1fед"
                                        .format(dose.totalIob, dose.currentCob.toInt(), dose.netIob)
                        }
                    } else {
                        proposalButtonText.value = "ВВЕСТИ ${carbs.toInt()}г УГЛЕВОДОВ\nОбщая доза: %.1f ед".format(dose.totalInsulin)
                    }

                    aapsLogger.debug(
                        LTag.APS,
                        "MealAdvisor calculation: carbs=$carbs, mealInsulin=${dose.mealInsulin}, correction=${dose.correctionInsulin}, iob=${dose.totalIob}, cob=${dose.currentCob}, netIob=${dose.netIob}"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    proposalButtonText.value = "CONFIRM INJECTION"
                }
            }
        }
    }

    private fun confirmInjection() {
        val valCarbs = carbsText.value.toDoubleOrNull() ?: return
        if (valCarbs <= 0.0) return

        lifecycleScope.launch(Dispatchers.Main) {
            val dose = lastCalculatedDose ?: withContext(Dispatchers.Default) { calculateMealDose(valCarbs) } ?: return@launch
            val lastBg = withContext(Dispatchers.IO) { persistenceLayer.getLastGlucoseValue()?.value } ?: 100.0

            val cobInfo = if (dose.currentCob > 1.0) "\nТекущие COB: ${dose.currentCob.toInt()}г" else ""
            val iobInfo = if (dose.totalIob > 0.1 && dose.currentCob > 1.0)
                "\nЧистый IOB: %.1fед (Всего IOB: %.1fед)".format(dose.netIob, dose.totalIob)
            else if (dose.totalIob > 0.1) "\nIOB: %.1fед".format(dose.totalIob)
            else ""

            confirmDialogMessage.value = "Что вы хотите сделать с этими ${valCarbs.toInt()}г углеводов?$cobInfo$iobInfo\n\n(Тек. ГК: %.1f, Общая доза: %.1f ед)".format(
                if (profileFunction.getUnits() == app.aaps.core.data.model.GlucoseUnit.MMOL) lastBg / 18.0182 else lastBg,
                dose.totalInsulin
            )
            showConfirmDialog.value = true
        }
    }

    private fun performInjection(valCarbs: Double, insulin: Double, isDirectBolus: Boolean, isAimiPropose: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            proposalButtonText.value = "PROCESSING..."
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ca = app.aaps.core.data.model.CA(
                    timestamp = System.currentTimeMillis(),
                    isValid = true,
                    duration = 0,
                    amount = valCarbs,
                    notes = "AIMI V2: ${currentEstimate.value?.description ?: ""}",
                    ids = app.aaps.core.data.model.IDs()
                )
                persistenceLayer.insertOrUpdateCarbs(ca, app.aaps.core.data.ue.Action.TREATMENT, app.aaps.core.data.ue.Sources.CarbDialog, ca.notes)

                if (isDirectBolus) {
                    if (insulin > 0.05) {
                        val detailedBolusInfo = app.aaps.core.interfaces.pump.DetailedBolusInfo().apply {
                            eventType = app.aaps.core.data.model.TE.Type.CORRECTION_BOLUS
                            this.insulin = insulin
                            notes = ca.notes
                            timestamp = ca.timestamp
                        }

                        val bolusResult = commandQueue.bolus(detailedBolusInfo)
                        loop.invoke("MealAdvisor_Direct", true)
                        withContext(Dispatchers.Main) {
                            if (bolusResult.success) {
                                Toast.makeText(this@MealAdvisorActivity, "Bolus delivered and carbs recorded.", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                Toast.makeText(this@MealAdvisorActivity, "Bolus delivery error: ${bolusResult.comment}", Toast.LENGTH_LONG).show()
                                recalculateProposal()
                            }
                        }
                    } else {
                        loop.invoke("MealAdvisor_Direct", true)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MealAdvisorActivity, "Carbs recorded. No bolus needed.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } else if (isAimiPropose) {
                    loop.invoke("MealAdvisor_AIMI", true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MealAdvisorActivity, "Carbs recorded. AIMI will dose insulin.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MealAdvisorActivity, "Carbs recorded without loop trigger.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MealAdvisorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    recalculateProposal()
                }
            }
        }
    }
}
