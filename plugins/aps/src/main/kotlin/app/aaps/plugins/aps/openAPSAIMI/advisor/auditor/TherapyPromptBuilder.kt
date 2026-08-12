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
        return """
# TU ES UN EXPERT CLINIQUE EN BOUCLE FERMÉE HYBRIDE (AID)

## TON RÔLE
Tu analyses les données de thérapie d'un patient sous AIMI (Artificial Intelligence Metabolic Interface). 
Ton objectif est de fournir une analyse **prospective et stratégique** pour optimiser le contrôle glycémique.

## CONTEXTE DU PATIENT (Derniers 7 jours)
- **TIR (70-180)**: ${(context.metrics.tir70_180 * 100).toInt()}%
- **Hypoglycémies (<70)**: ${(context.metrics.timeBelow70 * 100).toInt()}%
- **Moyenne Glycémique**: ${context.metrics.meanBg.toInt()} mg/dL
- **TDD (Insuline Totale/Jour)**: ${context.metrics.tdd} U
- **Part Basale**: ${(context.metrics.basalPercent * 100).toInt()}% du TDD

## RÉGLAGES ACTUELS DU PROFIL
- **Basal Nuit**: ${context.profile.nightBasal} U/h
- **Ratio Glucides (IC)**: ${context.profile.icRatio} g/U
- **Facteur Sensibilité (ISF)**: ${context.profile.isf} mg/dL/U
- **Cible**: ${context.profile.targetBg} mg/dL

## ANALYSE DES REPAS (Therapy Stats)
${formatTherapyStats(context)}

## INSTRUCTIONS D'ANALYSE
1. **Efficacité IC/Prebolus**: Analyse si les pics post-prandiaux sont dus à un IC trop faible ou un prebolus insuffisant.
2. **Équilibre Basal/SMB**: Détermine si le système sur-compense une basale trop faible par des SMB agressifs.
3. **Stabilité Nocturne**: Vérifie si la basale de nuit maintient la cible sans provoquer d'hypos.
4. **Utilisation des Modes**: Recommande l'usage plus systématique des modes AIMI (Meal, Sport, etc.) si nécessaire.

## TON OUTPUT
Fournis une analyse en **russe** (Russian), concise (max 200 mots), structurée en 3 points :
- **Главный вывод**: Основная выявленная проблема.
- **Рекомендуемое действие**: Точная настройка, которую необходимо изменить.
- **Совет коуча**: Поведенческий совет для пациента.

Будь клинически точным, используй цифры и поддерживающий тон.
        """.trimIndent()
    }

    private fun formatTherapyStats(context: AdvisorContext): String {
        val stats = context.metrics.therapyStats ?: return "Aucune donnée de repas disponible."
        val mealCount = stats.meals.size
        if (mealCount == 0) return "Aucun repas enregistré."
        
        val successCount = stats.meals.count { it.success }
        val successRate = (successCount.toDouble() / mealCount * 100).toInt()
        
        return """
- **Nombre de repas**: $mealCount
- **Taux de succès (70-140 à 4h)**: $successRate%
- **Pics moyens post-repas (2h)**: ${stats.meals.map { it.bgMax2h }.average().toInt()} mg/dL
- **Consommation Glucides Totale**: ${stats.totalCarbs.toInt()}g
        """.trimIndent()
    }
}
