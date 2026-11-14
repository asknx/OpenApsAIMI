package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject
import app.aaps.core.keys.DoubleKey
import androidx.core.graphics.toColorInt
import java.util.Locale
import kotlin.math.round

/**
 * Meal Advisor UI: "Snap & Go"
 * Allows user to take a photo, estimates carbs, and injects into AIMI.
 */
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

    private lateinit var resultText: TextView
    private lateinit var reasoningText: TextView
    private lateinit var confirmButton: Button
    private lateinit var analyzeButton: Button // New analyze button
    private lateinit var imageView: ImageView
    private lateinit var carbsInput: android.widget.EditText
    private lateinit var descriptionInput: android.widget.EditText
    private lateinit var detailsLayout: LinearLayout
    
    private var selectedBitmap: Bitmap? = null // Store selected bitmap
    
    // V2 Enhanced UI Views
    private lateinit var confidenceBadge: TextView
    private lateinit var macroSummaryText: TextView
    private lateinit var visibleItemsList: TextView
    private lateinit var recReasonText: TextView
    private lateinit var riskWarningText: TextView

    private var currentEstimate: EstimationResult? = null
    private var lastCalculatedDose: MealDose? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        recognitionService = FoodRecognitionService(this, preferences, persistenceLayer, geminiOAuthManager)
        title = "AIMI Meal Advisor"

        val bgColor = "#0F172A".toColorInt() // Deep Slate
        val cardColor = "#1E293B".toColorInt()
        val accentColor = "#3B82F6".toColorInt() // Blue

        val scrollView = android.widget.ScrollView(this).apply {
             layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
             isFillViewport = true
             setBackgroundColor(bgColor)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- Image Area ---
        imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(700, 700).apply { topMargin = 48 }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 32f
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_camera)
            setPadding(20, 20, 20, 20)
        }
        mainLayout.addView(imageView)

        descriptionInput = android.widget.EditText(this).apply {
            hint = "Optional context (e.g. 'Half portion')"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 16f
            }
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 }
            filters = arrayOf(android.text.InputFilter.LengthFilter(200)) // Character limit
        }
        mainLayout.addView(descriptionInput)

        // --- Analyze Button ---
        analyzeButton = Button(this).apply {
            text = "✨ ANALYZE MEAL"
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#6366F1".toColorInt()) // Indigo
                cornerRadius = 24f
            }
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, 140).apply { topMargin = 32 }
            visibility = android.view.View.GONE // Only show when photo is present
            setOnClickListener { 
                selectedBitmap?.let { simulateAnalysis(it) } 
            }
        }
        mainLayout.addView(analyzeButton)

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 }
        }

        val snapButton = Button(this).apply {
            text = "📷 CAMERA"
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 24f
            }
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1f).apply { rightMargin = 8 }
            setOnClickListener { dispatchTakePictureIntent() }
        }
        buttonsRow.addView(snapButton)

        val barcodeButton = Button(this).apply {
            text = "📋 BARCODE"
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#8B5CF6".toColorInt()) // Violet
                cornerRadius = 24f
            }
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1f).apply { leftMargin = 8; rightMargin = 8 }
            setOnClickListener { dispatchBarcodeScanIntent() }
        }
        buttonsRow.addView(barcodeButton)

        val pickButton = Button(this).apply {
            text = "📁 GALLERY"
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#475569".toColorInt())
                cornerRadius = 24f
            }
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1f).apply { leftMargin = 8 }
            setOnClickListener { dispatchPickPictureIntent() }
        }
        buttonsRow.addView(pickButton)
        mainLayout.addView(buttonsRow)

        // --- Results Section ---
        detailsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 48 }
        }

        confidenceBadge = TextView(this).apply {
            setPadding(24, 8, 24, 8)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER }
        }
        detailsLayout.addView(confidenceBadge)

        resultText = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        detailsLayout.addView(resultText)

        macroSummaryText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        detailsLayout.addView(macroSummaryText)

        // Divider
        detailsLayout.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply { setMargins(0, 16, 0, 32) }
            setBackgroundColor("#334155".toColorInt())
        })

        // Inputs
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        inputRow.addView(TextView(this).apply { text = "Carbs: "; setTextColor(Color.GRAY) })
        carbsInput = android.widget.EditText(this).apply {
            inputType = 8194
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = null
            setEms(3)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { recalculateProposal() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        inputRow.addView(carbsInput)
        inputRow.addView(TextView(this).apply { text = " g"; setTextColor(Color.GRAY) })
        detailsLayout.addView(inputRow)

        recReasonText = TextView(this).apply {
            textSize = 12f
            setTextColor("#94A3B8".toColorInt())
            gravity = Gravity.CENTER
            setPadding(32, 8, 32, 24)
        }
        detailsLayout.addView(recReasonText)

        riskWarningText = TextView(this).apply {
            setTextColor("#F87171".toColorInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            visibility = android.view.View.GONE
            setPadding(32, 16, 32, 16)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#451A1A".toColorInt())
                cornerRadius = 12f
            }
        }
        detailsLayout.addView(riskWarningText)

        visibleItemsList = TextView(this).apply {
            textSize = 13f
            setTextColor("#CBD5E1".toColorInt())
            setPadding(32, 24, 32, 24)
        }
        detailsLayout.addView(visibleItemsList)

        reasoningText = TextView(this).apply {
            textSize = 12f
            setTextColor("#64748B".toColorInt())
            setPadding(32, 0, 32, 32)
        }
        detailsLayout.addView(reasoningText)

        confirmButton = Button(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = 24f
            }
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, 160).apply { topMargin = 32 }
            setOnClickListener { confirmInjection() }
        }

        detailsLayout.addView(confirmButton)

        mainLayout.addView(detailsLayout)
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun dispatchTakePictureIntent() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != 0) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
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
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != 0) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            val intent = Intent(this, MealAdvisorCameraActivity::class.java).apply {
                putExtra(MealAdvisorCameraActivity.EXTRA_MODE, MealAdvisorCameraActivity.MODE_BARCODE)
            }
            startActivityForResult(intent, requestBarcodeScan)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                requestImageCapture -> {
                    data?.data?.let { uri ->
                        contentResolver.openInputStream(uri)?.use { 
                            android.graphics.BitmapFactory.decodeStream(it)?.let { bmp ->
                                imageView.setImageBitmap(bmp)
                                selectedBitmap = bmp
                                analyzeButton.visibility = android.view.View.VISIBLE
                            }
                        }
                    } ?: (data?.extras?.get("data") as? Bitmap)?.let { bmp ->
                        imageView.setImageBitmap(bmp)
                        selectedBitmap = bmp
                        analyzeButton.visibility = android.view.View.VISIBLE
                    }
                }
                requestImagePick -> {
                    data?.data?.let { uri ->
                        contentResolver.openInputStream(uri)?.use { 
                            android.graphics.BitmapFactory.decodeStream(it)?.let { bmp ->
                                imageView.setImageBitmap(bmp)
                                selectedBitmap = bmp
                                analyzeButton.visibility = android.view.View.VISIBLE
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
                analyzeButton.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun fetchBarcodeData(barcode: String) {
        val useOFF = preferences.get(app.aaps.core.keys.BooleanKey.OApsAIMIUseOpenFoodFacts)
        val providerName = if (useOFF) "Open Food Facts" else "FatSecret"
        
        Toast.makeText(this, "Fetching $providerName info...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            val product = if (useOFF) {
                val offProduct = openFoodFactsService.findProductByBarcode(barcode)
                offProduct?.let { 
                    FatSecretService.ProductInfo(it.name, it.carbs, it.protein, it.fat, it.calories, it.servingDescription)
                }
            } else {
                fatSecretService.findProductByBarcode(barcode)
            }

            if (product != null) {
                if (product.name == "IP_BLOCKED") {
                    AlertDialog.Builder(this@MealAdvisorActivity)
                        .setTitle("FatSecret Restriction")
                        .setMessage("FatSecret is blocking access from your current IP (Russian IP detected).\n\nPlease use a VPN (USA/UK/France) to enable barcode nutritional lookups or switch to Open Food Facts in settings.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    analyzeButton.visibility = android.view.View.VISIBLE
                    return@launch
                }

                val barcodeInfo = "$providerName Data: ${product.name} " +
                        "(${product.carbs}g C, ${product.protein}g P, ${product.fat}g F, ${product.calories}kcal per ${product.servingDescription})."
                
                val currentText = descriptionInput.text.toString().trim()
                val newText = if (currentText.isEmpty()) "$providerName: ${product.name}" else "$currentText ($providerName: ${product.name})"
                descriptionInput.setText(newText)
                
                Toast.makeText(this@MealAdvisorActivity, "Product found: ${product.name}", Toast.LENGTH_LONG).show()

                // If we have a photo, trigger AI analysis with this context
                if (selectedBitmap != null) {
                    simulateAnalysis(selectedBitmap!!, barcodeInfo)
                } else {
                    // If no photo, populate UI directly from data
                    updateUIWithBarcodeData(product, providerName)
                    Toast.makeText(this@MealAdvisorActivity, "Nutritional data retrieved. You can now confirm or take a photo for AI sizing.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this@MealAdvisorActivity, "Product not found in $providerName database.", Toast.LENGTH_LONG).show()
                analyzeButton.visibility = android.view.View.VISIBLE
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
        currentEstimate = result
        updateUIWithResult(result)
    }

    private fun simulateAnalysis(bitmap: Bitmap, customInfo: String = "") {
        val rawDesc = descriptionInput.text.toString().trim()
        
        // Basic sanitization
        val userDesc = if ((rawDesc.length >= 3) && rawDesc.any { it.isLetter() }) rawDesc else ""
        val finalDesc = if (customInfo.isNotEmpty()) "$customInfo. User Note: $userDesc" else userDesc
        
        Toast.makeText(this, "AI Analysis in progress...", Toast.LENGTH_SHORT).show()
        analyzeButton.isEnabled = false
        analyzeButton.text = "PROCESSING..."

        lifecycleScope.launch {
            try {
                val result = recognitionService.estimateCarbsFromImage(bitmap, finalDesc)
                currentEstimate = result
                updateUIWithResult(result)
            } catch (e: Exception) {
                Toast.makeText(this@MealAdvisorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                analyzeButton.isEnabled = true
                analyzeButton.text = "✨ ANALYZE MEAL"
            }
        }
    }

    private fun updateUIWithResult(result: EstimationResult) {
        resultText.text = result.description
        carbsInput.setText(result.recommendedCarbsForDose.toInt().toString())
        
        val macroStr = "P: ${result.protein.estimate.toInt()}g | F: ${result.fat.estimate.toInt()}g | FPU: ${result.fpuEquivalent}"
        macroSummaryText.text = macroStr
        
        recReasonText.text = result.recommendedCarbsReason
        reasoningText.text = "Rationale: ${result.reasoning}"
        
        val items = result.visibleItems.joinToString("\n") { "• ${it.name} (${it.amountInfo})" }
        visibleItemsList.text = "Identified:\n$items"

        // Confidence Badge
        val (color, text) = when(result.confidence.uppercase(Locale.ROOT)) {
            "HIGH" -> "#065F46".toColorInt() to "HIGH CONFIDENCE"
            "MEDIUM" -> "#92400E".toColorInt() to "MEDIUM CONFIDENCE"
            else -> "#991B1B".toColorInt() to "LOW CONFIDENCE"
        }
        confidenceBadge.text = text
        confidenceBadge.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = 12f
        }

        // Risks
        if ((result.hiddenCarbRisk == "HIGH") || result.needsManualConfirmation) {
            riskWarningText.visibility = android.view.View.VISIBLE
            riskWarningText.text = "⚠️ HIGH RISK: ${result.insulinRelevantNotes.joinToString(", ")}"
        } else {
            riskWarningText.visibility = android.view.View.GONE
        }

        detailsLayout.visibility = android.view.View.VISIBLE
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
        
        // 1. Insulin for NEW carbs
        val mealInsulin = carbs / ic

        // 2. Fetch current status
        val lastBg = persistenceLayer.getLastGlucoseValue()?.value ?: 100.0
        val target = profile.getTargetMgdl()
        
        // 3. IOB & COB Balance
        val bolusIob = iobCobCalculator.calculateIobFromBolus().iob
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().basaliob
        val totalIob = bolusIob + basalIob
        
        val currentCob = iobCobCalculator.getMealDataWithWaitingForCalculationFinish().mealCOB
        
        // 🚨 CRITICAL FIX: Net IOB is the IOB that is NOT currently covering existing carbs.
        // If we have 38g COB and IC is 5.2, then 38/5.2 = 7.3U is "reserved" for that COB.
        // If total IOB is 6.4U, then Net IOB is 6.4 - 7.3 = -0.9U (clamped to 0.0), 
        // meaning we have NO excess insulin to subtract from the new meal dose.
        val insulinReservedForCob = currentCob / ic
        val netIob = (totalIob - insulinReservedForCob).coerceAtLeast(0.0)

        // 4. Correction for BG
        val correctionInsulin = if (isf > 1.0) (lastBg - target) / isf else 0.0
        
        // 5. Final Dose: (New Meal + Correction) - (Unreserved IOB)
        val totalInsulin = (mealInsulin + correctionInsulin - netIob).coerceAtLeast(0.0)

        return MealDose(mealInsulin, correctionInsulin, totalIob, currentCob, netIob, totalInsulin)
    }

    private fun recalculateProposal() {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val carbs = carbsInput.text.toString().toDoubleOrNull() ?: 0.0
                val dose = calculateMealDose(carbs)
                lastCalculatedDose = dose

                withContext(Dispatchers.Main) {
                    if (dose == null) {
                        confirmButton.text = "CONFIRM INJECTION"
                        return@withContext
                    }

                    if (Math.abs(dose.correctionInsulin) > 0.1 || Math.abs(dose.totalIob) > 0.1 || dose.currentCob > 0.1) {
                        val cobText = if (dose.currentCob > 1.0) " (COB: ${dose.currentCob.toInt()}г)" else ""
                        confirmButton.text = "ВВЕСТИ ${carbs.toInt()}г УГЛЕВОДОВ$cobText\nЕда: %.1fед + Корр: %.1fед - ЧистыйIOB: %.1fед = %.1f ед"
                            .format(dose.mealInsulin, dose.correctionInsulin, dose.netIob, dose.totalInsulin)

                        if (dose.currentCob > 1.0 && dose.totalIob > 0.1) {
                            reasoningText.text = "Обоснование: ${currentEstimate?.reasoning ?: ""}\n" +
                                    "IOB (%.1fед) частично сбалансирован COB (%dг), Чистый IOB: %.1fед"
                                        .format(dose.totalIob, dose.currentCob.toInt(), dose.netIob)
                        }
                    } else {
                        confirmButton.text = "ВВЕСТИ ${carbs.toInt()}г УГЛЕВОДОВ\nОбщая доза: %.1f ед".format(dose.totalInsulin)
                    }

                    aapsLogger.debug(
                        LTag.APS,
                        "MealAdvisor calculation: carbs=$carbs, mealInsulin=${dose.mealInsulin}, correction=${dose.correctionInsulin}, iob=${dose.totalIob}, cob=${dose.currentCob}, netIob=${dose.netIob}"
                    )
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    confirmButton.text = "CONFIRM INJECTION"
                }
            }
        }
    }

    private fun confirmInjection() {
        val valCarbs = carbsInput.text.toString().toDoubleOrNull() ?: return
        if (valCarbs <= 0.0) return

        lifecycleScope.launch(Dispatchers.Main) {
            val dose = lastCalculatedDose ?: withContext(Dispatchers.Default) { calculateMealDose(valCarbs) } ?: return@launch
            val lastBg = withContext(Dispatchers.IO) { persistenceLayer.getLastGlucoseValue()?.value } ?: 100.0

            val cobInfo = if (dose.currentCob > 1.0) "\nТекущие COB: ${dose.currentCob.toInt()}г" else ""
            val iobInfo = if (dose.totalIob > 0.1 && dose.currentCob > 1.0) 
                "\nЧистый IOB: %.1fед (Всего IOB: %.1fед)".format(dose.netIob, dose.totalIob) 
                else if (dose.totalIob > 0.1) "\nIOB: %.1fед".format(dose.totalIob)
                else ""

            val msg = "Что вы хотите сделать с этими ${valCarbs.toInt()}г углеводов?$cobInfo$iobInfo\n\n(Тек. ГК: %.1f, Общая доза: %.1f ед)".format(
                if (profileFunction.getUnits() == app.aaps.core.data.model.GlucoseUnit.MMOL) lastBg / 18.0182 else lastBg,
                dose.totalInsulin
            )

            AlertDialog.Builder(this@MealAdvisorActivity)
                .setTitle("Подтверждение еды")
                .setMessage(msg)
                .setPositiveButton("ВВЕСТИ БОЛЮС") { _, _ -> performInjection(valCarbs, dose.totalInsulin, isDirectBolus = true) }
                .setNeutralButton("ОТПРАВИТЬ В AIMI") { _, _ -> performInjection(valCarbs, dose.totalInsulin, isDirectBolus = false, isAimiPropose = true) }
                .setNegativeButton("ОТМЕНА", null)
                .show()
        }
    }

    private fun performInjection(valCarbs: Double, insulin: Double, isDirectBolus: Boolean, isAimiPropose: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            confirmButton.isEnabled = false
            confirmButton.text = "PROCESSING..."
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ca = app.aaps.core.data.model.CA(
                    timestamp = System.currentTimeMillis(),
                    isValid = true,
                    duration = 0,
                    amount = valCarbs,
                    notes = "AIMI V2: ${currentEstimate?.description ?: ""}",
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
                                confirmButton.isEnabled = true
                                recalculateProposal()
                            }
                        }
                    } else {
                        // Direct but 0 units - skip bolus command to avoid PumpPluginAbstract crash/hang
                        loop.invoke("MealAdvisor_Direct", true)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MealAdvisorActivity, "Carbs recorded. No bolus needed.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } else if (isAimiPropose) {
                    // Scenario: Open Loop with "Propose for AIMI"
                    preferences.put(app.aaps.core.keys.BooleanKey.OApsAIMIMealAdvisorTrigger, true)
                    preferences.put(DoubleKey.OApsAIMILastEstimatedCarbs, valCarbs)
                    val nowMs = System.currentTimeMillis()
                    preferences.put(DoubleKey.OApsAIMILastEstimatedCarbTime, nowMs.toDouble())
                    aapsLogger.debug(
                        LTag.APS,
                        "MEAL_ADVISOR_TRACE confirmInjection carbs=${"%.1f".format(Locale.US, valCarbs)}g trigger=true estimateTimeMs=$nowMs mode=${loop.runningMode()}"
                    )

                    // 🔄 Refresh Loop to process carbs and show TBR/SMB proposal immediately
                    loop.invoke("MealAdvisor", true)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MealAdvisorActivity, "Carbs recorded. Check Adjustments.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    // Scenario: Record Only
                    loop.invoke("MealAdvisor_Record", true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MealAdvisorActivity, "Carbs recorded.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MealAdvisorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    confirmButton.isEnabled = true
                    recalculateProposal()
                }
            }
        }
    }
}
