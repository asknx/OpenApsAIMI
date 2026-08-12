package app.aaps.plugins.aps.openAPSAIMI.plugins.impl

import app.aaps.core.keys.DoubleKey
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPluginContext
import app.aaps.plugins.aps.openAPSAIMI.plugins.AimiDecisionPlugin
import kotlin.math.roundToInt

/**
 * 📈 StableControlPlugin
 * 
 * Suggests increasing basal/lunch aggression when control is stable but suboptimal.
 */
class StableControlPlugin : AimiDecisionPlugin {
    override val id: String = "stable_control"
    override val name: String = "Stable Control Optimizer"
    override val priority: Int = 50 // Medium priority

    override fun analyze(context: AimiPluginContext): List<AimiAction> {
        val actions = mutableListOf<AimiAction>()
        
        // Use Mean BG from context to decide if we need more aggression
        val meanBg = context.glucose.glucose
        val lunchFactor = context.preferences.get(DoubleKey.OApsAIMILunchFactor)
        
        // If mean BG is suboptimal (> 140 mg/dL) and we have headroom
        if (meanBg > 140.0 && lunchFactor < 2.0) {
            val increment = if (meanBg > 180.0) 0.2 else 0.1
            val newValue = ((lunchFactor + increment) * 10.0).roundToInt() / 10.0
            actions.add(
                AimiAction.PreferenceUpdate(
                    key = DoubleKey.OApsAIMILunchFactor,
                    newValue = newValue,
                    reason = "Mean BG is high (${meanBg.toInt()} mg/dL). Increasing Lunch Factor to improve post-prandial control.",
                    domain = AimiDomain.Basal,
                    priority = AimiPriority.Medium
                )
            )
        }
        
        return actions
    }
}
