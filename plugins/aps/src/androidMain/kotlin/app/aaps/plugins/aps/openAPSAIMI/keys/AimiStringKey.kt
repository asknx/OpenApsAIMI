package app.aaps.plugins.aps.openAPSAIMI.keys

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class AimiStringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef = TextRef.Literal(""),
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
    override val dependency: app.aaps.core.keys.interfaces.BooleanPreferenceKey? = null,
    override val negativeDependency: app.aaps.core.keys.interfaces.BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true
) : StringPreferenceKey {
    PregnancyDueDateString("aimi_pregnancy_due_date_string", "", TextRef.Literal("Pregnancy Due Date")),
    RemoteControlPin("aimi_remote_control_pin", "", TextRef.Literal("Remote Control PIN"), isPin = true, isPassword = true),
    FatSecretClientId("aimi_fatsecret_client_id", "", TextRef.Literal("FatSecret Client ID"), negativeDependency = BooleanKey.OApsAIMIUseOpenFoodFacts),
    FatSecretClientSecret("aimi_fatsecret_client_secret", "", TextRef.Literal("FatSecret Client Secret"), negativeDependency = BooleanKey.OApsAIMIUseOpenFoodFacts),
    FatSecretRegion("aimi_fatsecret_region", "RU", TextRef.Literal("FatSecret Region"), negativeDependency = BooleanKey.OApsAIMIUseOpenFoodFacts),
}
