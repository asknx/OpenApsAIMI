package app.aaps.activities

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.view.Gravity
import android.graphics.Color
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity

class AbbreviationsActivity : TranslatedDaggerAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Abbreviations Deciphering"

        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val abbreviations = listOf(
            "BG" to "Blood Glucose (Гликемия)",
            "IOB" to "Insulin On Board (Активный инсулин)",
            "CHO" to "Carbohydrates On Board (Активные углеводы)",
            "SMB" to "Super Micro Bolus (Супер микро болюс)",
            "TBR" to "Temporary Basal Rate (Временная базальная скорость)",
            "TT" to "Temporary Target (Временная цель)",
            "DIA" to "Duration of Insulin Action (Длительность действия инсулина)",
            "ISF" to "Insulin Sensitivity Factor (Фактор чувствительности к инсулину)",
            "IC" to "Insulin to Carb ratio (Углеводный коэффициент)",
            "UAM" to "Unannounced Meal (Непредвиденный прием пищи)",
            "TDD" to "Total Daily Dose (Общая суточная доза)",
            "TIR" to "Time In Range (Время в целевом диапазоне)",
            "GMI" to "Glucose Management Indicator (Прогноз HbA1c)"
        )

        for ((abbr, desc) in abbreviations) {
            val abbrView = TextView(this).apply {
                text = abbr
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 0)
            }
            val descView = TextView(this).apply {
                text = desc
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, 16)
            }
            mainLayout.addView(abbrView)
            mainLayout.addView(descView)
        }

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }
}
