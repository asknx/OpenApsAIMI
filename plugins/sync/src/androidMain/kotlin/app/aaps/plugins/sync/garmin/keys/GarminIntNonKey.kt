package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class GarminIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    LastSteps("garmin_http_last_steps", -1),
}
