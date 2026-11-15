package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor

import app.aaps.plugins.aps.openAPSAIMI.advisor.AdvisorContext
import app.aaps.plugins.aps.openAPSAIMI.advisor.AdvisorReport
import org.json.JSONObject

/**
 * 🍱 TherapyPromptBuilder
 * 
 * Specialized builder for clinical therapy analysis prompts.
 * Designed for deep-dive retrospective analysis of meal outcomes and profile settings.
 */
object TherapyPromptBuilder {

    fun buildAnalysisPrompt(context: AdvisorContext, report: AdvisorReport): String {
        val m = context.metrics
        val isMmol = m.isMmol
        val unitStr = if (isMmol) "ммоль/л (mmol/L)" else "мг/дл (mg/dL)"
        val meanBgStr = if (isMmol) "%.1f ммоль/л".format(m.meanBg / 18.01559) else "%.0f mg/dL".format(m.meanBg)
        val isfStr = if (isMmol) "%.1f ммоль/л/U".format(context.profile.isf / 18.01559) else "%.0f mg/dL/U".format(context.profile.isf)
        val targetStr = if (isMmol) "%.1f ммоль/л".format(context.profile.targetBg / 18.01559) else "%.0f mg/dL".format(context.profile.targetBg)

        val unitInstruction = if (isMmol) {
            "ВАЖНО: Все значения глюкозы и чувствительности (ISF) в ответе пиши СТРОГО в ммоль/л (mmol/L)! Никаких mg/dL!"
        } else {
            "ВАЖНО: Все значения глюкозы и чувствительности (ISF) в ответе пиши СТРОГО в мг/дл (mg/dL)!"
        }

        return """
# ТЫ — ВЕДУЩИЙ КЛИНИЧЕСКИЙ ЭКСПЕРТ В ОБЛАСТИ ГИБРИДНЫХ ЗАМКНУТЫХ СИСТЕМ (AID / ИСКУССТВЕННАЯ ПОДЖЕЛУДОЧНАЯ ЖЕЛЕЗА)

## ТВОЯ РОЛЬ
Ты анализируешь детальные клинические данные и Juggluco NG-статистику пациента на алгоритме AIMI (AndroidAPS 4.0).
Твоя цель — предоставить глубокий клинический разбор и стратегические рекомендации по оптимизации базала, углеводных коэффициентов (IC), фактора чувствительности (ISF) и преболюсов.

## КЛИНИЧЕСКИЙ СТАТУС ПАЦИЕНТА (${m.periodLabel})
- **Единицы измерения**: $unitStr
- **5-зонный TIR**:
  - Очень высокий (>13.9 / >250): ${(m.tirVeryHigh * 100).toInt()}%
  - Высокий (10.0-13.9 / 180-250): ${(m.tirHigh * 100).toInt()}%
  - В целевом диапазоне (3.9-10.0 / 70-180): ${(m.tirInRange * 100).toInt()}%
  - Низкий (3.0-3.9 / 54-70): ${(m.tirLow * 100).toInt()}%
  - Очень низкий (<3.0 / <54): ${(m.tirVeryLow * 100).toInt()}%
- **Средний сахар**: $meanBgStr (GMI / HbA1c: ${"%.1f%%".format(m.gmi)})
- **Вариабельность (CV)**: ${"%.1f%%".format(m.cvPct)} (Цель <36%)
- **Индекс риска гликемии (GRI)**: ${"%.0f".format(m.gri)} (${m.griZone})
- **GVI (Glycemic Variability Index)**: ${"%.2f".format(m.gvi)}, **PSG**: ${"%.2f".format(m.psg)}
- **Эпизоды гипогликемий**: ${m.hypoEvents} (тяжелых: ${m.severeHypoEvents}, ср. длит: ${m.avgHypoDurationMin} мин, пик: ${m.hypoPeakIntervalStr})
- **Эпизоды гипергликемий**: ${m.hyperEvents} (тяжелых: ${m.severeHyperEvents}, ср. длит: ${m.avgHyperDurationMin} мин, пик: ${m.hyperPeakIntervalStr})
- **TDD (Всего инсулина/сутки)**: ${"%.1f".format(m.tdd)} U (Базал: ${(m.basalPercent * 100).toInt()}%)

## ТЕКУЩИЕ НАСТРОЙКИ ПРОФИЛЯ
- **Ночной базал**: ${context.profile.nightBasal} U/h
- **Углеводный коэффициент (IC)**: ${context.profile.icRatio} г/U
- **Фактор чувствительности (ISF)**: $isfStr
- **Целевая гликемия**: $targetStr

## АНАЛИЗ ПРИЕМОВ ПИЩИ И ТЕРАПИИ
${formatTherapyStats(context)}

## ПРАВИЛА И ИНСТРУКЦИИ ДЛЯ ОТВЕТА
$unitInstruction
Сформируй лаконичный, авторитетный клинический ответ на русском языке (максимум 200-250 слов), структурированный строго по 4 пунктам:
1. **Клинический вердикт**: Оценка текущего контроля, риска гипогликемий и вариабельности.
2. **Оценка питания и преболюсов**: Анализ постпрандиальных пиков, достаточности паузы до еды (преболюса) и точности IC.
3. **Рекомендации по профилю**: Конкретные шаги по корректировке базала (ночь/день), IC и ISF в единицах $unitStr.
4. **Совет пациенту**: Практический совет по поведению или режимам AIMI.

Пиши профессионально, поддерживающе и с конкретными цифрами.
        """.trimIndent()
    }

    private fun formatTherapyStats(context: AdvisorContext): String {
        val stats = context.metrics.therapyStats ?: return "Нет данных о приемах пищи."
        val mealCount = stats.meals.size
        if (mealCount == 0) return "Приемы пищи не зафиксированы."
        val isMmol = context.metrics.isMmol

        val sb = StringBuilder()
        sb.appendLine("- **Всего приемов пищи**: $mealCount")
        sb.appendLine("- **Успешность компенсации**: ${stats.mealSuccessRate.toInt()}%")
        sb.appendLine("- **Среднее время преболюса**: ${"%.0f".format(stats.avgPrebolusMin)} мин")
        if (stats.bFastAvgPeak != null) {
            val v = if (isMmol) "%.1f ммоль/л".format(stats.bFastAvgPeak / 18.01559) else "%.0f mg/dL".format(stats.bFastAvgPeak)
            sb.appendLine("- **Средний пик (Завтрак)**: $v")
        }
        if (stats.lunchAvgPeak != null) {
            val v = if (isMmol) "%.1f ммоль/л".format(stats.lunchAvgPeak / 18.01559) else "%.0f mg/dL".format(stats.lunchAvgPeak)
            sb.appendLine("- **Средний пик (Обед)**: $v")
        }
        if (stats.dinnerAvgPeak != null) {
            val v = if (isMmol) "%.1f ммоль/л".format(stats.dinnerAvgPeak / 18.01559) else "%.0f mg/dL".format(stats.dinnerAvgPeak)
            sb.appendLine("- **Средний пик (Ужин)**: $v")
        }
        if (!stats.therapyEventsSummary.isNullOrBlank()) {
            sb.appendLine("- **События терапии**: ${stats.therapyEventsSummary}")
        }
        return sb.toString().trimEnd()
    }
}
