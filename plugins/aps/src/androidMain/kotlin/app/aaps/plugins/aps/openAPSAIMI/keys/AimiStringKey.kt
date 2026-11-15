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
    override val exportable: Boolean = true,
    override val preferenceType: app.aaps.core.keys.PreferenceType = app.aaps.core.keys.PreferenceType.TEXT_FIELD,
    override val visibility: app.aaps.core.keys.interfaces.ElementVisibility = app.aaps.core.keys.interfaces.ElementVisibility.ALWAYS,
    val entriesLiterals: Map<String, String>? = null
) : StringPreferenceKey {
    PregnancyDueDateString("aimi_pregnancy_due_date_string", "", TextRef.Literal("Ожидаемая дата родов (ПДР)")),
    RemoteControlPin("aimi_remote_control_pin", "", TextRef.Literal("PIN-код удаленного управления"), isPin = true, isPassword = true),
    FatSecretClientId(
        key = "aimi_fatsecret_client_id",
        defaultValue = "",
        title = TextRef.Literal("FatSecret Client ID"),
        visibility = app.aaps.core.keys.interfaces.ElementVisibility {
            it.preferences.get(app.aaps.core.keys.StringKey.AimiFoodDatabaseProvider) in listOf("FATSECRET", "MIXED")
        }
    ),
    FatSecretClientSecret(
        key = "aimi_fatsecret_client_secret",
        defaultValue = "",
        title = TextRef.Literal("FatSecret Client Secret"),
        visibility = app.aaps.core.keys.interfaces.ElementVisibility {
            it.preferences.get(app.aaps.core.keys.StringKey.AimiFoodDatabaseProvider) in listOf("FATSECRET", "MIXED")
        }
    ),
    FatSecretRegion(
        key = "aimi_fatsecret_region",
        defaultValue = "RU",
        title = TextRef.Literal("Регион базы FatSecret"),
        visibility = app.aaps.core.keys.interfaces.ElementVisibility {
            it.preferences.get(app.aaps.core.keys.StringKey.AimiFoodDatabaseProvider) in listOf("FATSECRET", "MIXED")
        },
        preferenceType = app.aaps.core.keys.PreferenceType.LIST,
        entriesLiterals = mapOf(
            "RU" to "Россия (RU)",
            "US" to "США (US)",
            "FR" to "Франция (FR)",
            "DE" to "Германия (DE)",
            "GB" to "Великобритания (GB)",
            "IT" to "Италия (IT)",
            "ES" to "Испания (ES)"
        )
    ),
    StepsSourceMode(
        key = "aimi_steps_source_mode",
        defaultValue = "wear",
        title = TextRef.Literal("Режим источника шагов и пульса"),
        preferenceType = app.aaps.core.keys.PreferenceType.LIST,
        entriesLiterals = mapOf(
            "wear" to "Wear OS (приоритет)",
            "auto" to "Авто-переключение (Wear -> Health Connect)",
            "health_connect" to "Только Health Connect",
            "disabled" to "Отключено"
        )
    );

    override val entries: Map<String, TextRef> =
        entriesLiterals?.mapValues { TextRef.Literal(it.value) } ?: emptyMap()
}
