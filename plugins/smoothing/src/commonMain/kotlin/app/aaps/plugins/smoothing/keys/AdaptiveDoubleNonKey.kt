package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class AdaptiveDoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
) : DoubleNonPreferenceKey {

    LearnedR("adaptive_learned_r", 25.0),
}
