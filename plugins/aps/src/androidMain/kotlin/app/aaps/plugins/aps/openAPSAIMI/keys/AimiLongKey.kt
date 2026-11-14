package app.aaps.plugins.aps.openAPSAIMI.keys

import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class AimiLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val title: TextRef = TextRef.Literal(key),
    override val min: Long = 0L,
    override val max: Long = Long.MAX_VALUE,
    override val exportable: Boolean = true
) : LongPreferenceKey {

    // 🤰 Pregnancy Due Date (Timestamp MS)
    PregnancyDueDate("oa_aimi_pregnancy_due_date_ms", 0L, TextRef.Literal("Pregnancy Due Date")),

    // 💉 Last Manual Prebolus Timestamp (Timestamp MS)
    LastPrebolusTime("oa_aimi_last_prebolus_time_ms", 0L, TextRef.Literal("Last Prebolus Time"))
}
