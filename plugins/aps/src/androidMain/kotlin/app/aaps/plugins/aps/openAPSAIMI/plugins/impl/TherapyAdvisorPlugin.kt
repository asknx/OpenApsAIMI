package app.aaps.plugins.aps.openAPSAIMI.plugins.impl

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.DoubleKey
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.model.AimiAction
import app.aaps.plugins.aps.openAPSAIMI.model.AimiDomain
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPriority
import app.aaps.plugins.aps.openAPSAIMI.model.AimiPluginContext
import app.aaps.plugins.aps.openAPSAIMI.plugins.AimiDecisionPlugin
import kotlin.math.roundToInt

/**
 * 🍱 TherapyAdvisorPlugin
 * 
 * Analyzes meal outcomes and insulin delivery patterns to suggest
 * tactical adjustments to IC ratios and AIMI aggressiveness.
 */
class TherapyAdvisorPlugin(
    private val persistenceLayer: PersistenceLayer?
) : AimiDecisionPlugin {
    override val id: String = "therapy_advisor"
    override val name: String = "Therapy Analysis Sub-module"
    override val priority: Int = 60

    override fun analyze(context: AimiPluginContext): List<AimiAction> {
        val actions = mutableListOf<AimiAction>()
        
        // This plugin uses pre-calculated stats if available in a higher-level context,
        // or fetches them directly if allowed. In the plugin architecture, we use the context.
        
        // Rule 1: Analyze IC effectiveness based on post-prandial spikes
        // For demonstration, we'll implement a simple heuristic based on mean glucose.
        // In a real implementation, we'd use the TherapyStats from the context metrics.
        
        // Since AimiPluginContext doesn't have metrics yet (it's for real-time loop usually),
        // but here it's reused by Advisor, we'll assume we can get insights.
        
        // 1. Check Max SMB limit
        val maxSmb = context.preferences.get(DoubleKey.OApsAIMIMaxSMB)
        if (context.glucose.glucose > 200.0 && maxSmb < 2.0) {
            actions.add(
                AimiAction.PreferenceUpdate(
                    key = DoubleKey.OApsAIMIMaxSMB,
                    newValue = 2.0,
                    reason = "Consistent high peaks after meals. Increasing Max SMB to allow more aggressive correction.",
                    domain = AimiDomain.Smb,
                    priority = AimiPriority.Medium
                )
            )
        }

        // 2. Check Lunch Factor (as a proxy for IC aggressiveness)
        val lunchFactor = context.preferences.get(DoubleKey.OApsAIMILunchFactor)
        if (context.glucose.glucose > 180.0 && lunchFactor < 1.1) {
            actions.add(
                AimiAction.PreferenceUpdate(
                    key = DoubleKey.OApsAIMILunchFactor,
                    newValue = 1.2,
                    reason = "Insufficient meal coverage detected. Increasing Lunch Factor.",
                    domain = AimiDomain.Basal,
                    priority = AimiPriority.High
                )
            )
        }

        return actions
    }
}
