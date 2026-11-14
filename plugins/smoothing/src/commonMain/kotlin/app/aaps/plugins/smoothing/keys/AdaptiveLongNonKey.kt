package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class AdaptiveLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastSavedTimestamp("adaptive_last_saved_timestamp", 0L),
    LastSensorChangeTimestamp("adaptive_sensor_change_timestamp", 0L),
    LastProcessedTimestamp("adaptive_last_processed_timestamp", 0L, exportable = false),
}
