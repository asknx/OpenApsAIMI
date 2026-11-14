package app.aaps.core.interfaces.stats

interface TIR {

    val date: Long
    val lowThreshold: Double
    val highThreshold: Double
    var below: Int
    var inRange: Int
    var above: Int
    var error: Int
    var count: Int
    fun error()
    fun below()
    fun inRange()
    fun above()

    fun belowPct(): Double = if (count > 0) below.toDouble() / count * 100.0 else 0.0
    fun abovePct(): Double = if (count > 0) above.toDouble() / count * 100.0 else 0.0
    fun inRangePct(): Double = if (count > 0) 100.0 - belowPct() - abovePct() else 0.0
}
