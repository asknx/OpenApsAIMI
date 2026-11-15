package app.aaps.core.keys

object ExportableKeys {
    fun isCustomExportableKeyPrefix(key: String): Boolean {
        return key.startsWith("aimi_") ||
            key.startsWith("key_oaps_aimi_") ||
            key.startsWith("oa_aimi_") ||
            key.startsWith("key_aimi_") ||
            key.startsWith("apex_") ||
            key.startsWith("garmin_") ||
            key.startsWith("LocalProfile_") ||
            key.startsWith("Objectives_")
    }
}
