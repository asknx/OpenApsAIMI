package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class GarminLongNonKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    LastTimestamp("garmin_http_last_steps_ts", 0L),
}
