package app.aaps.plugins.aps.openAPSAIMI.plugins.impl

import app.aaps.core.keys.DoubleKey
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPluginContext
import app.aaps.plugins.aps.openAPSAIMI.plugins.AimiDecisionPlugin
import kotlin.math.roundToInt

/**
 * 🛡️ SafetyAggressionPlugin
 * 
 * Monitors hypo risk and automatically proposes reductions in SMB aggression.
 * Migrated from legacy AimiAdvisorService.
 */
class SafetyAggressionPlugin : AimiDecisionPlugin {
    override val id: String = "safety_aggression"
    override val name: String = "Safety & SMB Aggression Monitor"
    override val priority: Int = 90 // High priority for safety

    override fun analyze(context: AimiPluginContext): List<AimiAction> {
        val actions = mutableListOf<AimiAction>()
        
        // Use Mean BG from context to assess overall safety
        val meanBg = context.glucose.glucose
        
        // Critical Rule: If Mean BG is too low or we have high hypo risk
        if (meanBg < 105.0) {
            val maxSmb = context.preferences.get(DoubleKey.OApsAIMIMaxSMB)
            
            if (maxSmb > 0.5) {
                val reduction = if (meanBg < 90.0) 0.7 else 0.85
                val newValue = (maxSmb * reduction * 10.0).roundToInt() / 10.0
                
                actions.add(
                    AimiAction.PreferenceUpdate(
                        key = DoubleKey.OApsAIMIMaxSMB,
                        newValue = newValue,
                        reason = "Mean BG is low (${meanBg.toInt()} mg/dL). Reducing Max SMB to prevent hypos.",
                        domain = AimiDomain.Safety,
                        priority = AimiPriority.Critical
                    )
                )
            }
        }
        
        return actions
    }
}
