package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class AdaptiveIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    SessionId("adaptive_session_id", 0),
}
