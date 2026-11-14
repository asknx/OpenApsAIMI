package app.aaps.plugins.aps.openAPSAIMI

import android.annotation.SuppressLint
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Therapy(private val persistenceLayer: PersistenceLayer) {

    var sleepTime = false
    var sportTime = false
    var snackTime = false
    var lowCarbTime = false
    var highCarbTime = false
    var mealTime = false
    var bfastTime = false
    var lunchTime = false
    var dinnerTime = false
    var fastingTime = false
    var stopTime = false
    var calibrationTime = false
    var deleteEventDate: String? = null
    var deleteTime = false

    private var cachedEvents: List<TE> = emptyList()

    @SuppressLint("CheckResult")
    fun updateStatesBasedOnTherapyEvents() {
        val now = System.currentTimeMillis()
        val fromTime = now - TimeUnit.DAYS.toMillis(1) // 24 hours lookback for most events

        // 🚀 PERFORMANCE FIX: Fetch all therapy events in a SINGLE database query
        cachedEvents = try {
            runBlocking { persistenceLayer.getTherapyEventDataFromTime(fromTime, true) }
        } catch (ignored: Exception) {
            emptyList()
        }

        resetAllStates()
        calibrationTime = false
        stopTime = false
        deleteEventDate = null

        // Process all events from memory to set flags
        cachedEvents.forEach { event ->
            val isActive = now <= (event.timestamp + event.duration)

            when (event.type) {
                TE.Type.NOTE -> {
                    val note = event.note ?: ""
                    val noteLower = note.lowercase()

                    if (noteLower.contains("stop") && isActive) stopTime = true
                    if (noteLower.contains("sleep") && isActive) sleepTime = true

                    val containsSport = noteLower.contains("sport")
                    val isWalking = noteLower.contains("marche") || noteLower.contains("walk")
                    if (containsSport && !isWalking && isActive) sportTime = true

                    if (noteLower.contains("snack") && isActive) snackTime = true
                    if (noteLower.contains("lowcarb") && isActive) lowCarbTime = true
                    if ((noteLower.contains("highcarb") || noteLower.contains("high carb")) && isActive) highCarbTime = true
                    if (noteLower.contains("meal") && isActive) mealTime = true
                    if ((noteLower.contains("bfast") || noteLower.contains("breakfast")) && isActive) bfastTime = true
                    if (noteLower.contains("lunch") && isActive) lunchTime = true
                    if (noteLower.contains("dinner") && isActive) dinnerTime = true
                    if (noteLower.contains("fasting") && isActive) fastingTime = true

                    if (noteLower.contains("delete")) {
                        if (isActive) deleteTime = true
                        val extractedDate = extractDateFromDeleteEvent(note)
                        if (extractedDate != null) deleteEventDate = extractedDate
                    }
                }
                TE.Type.FINGER_STICK_BG_VALUE -> {
                    // Finger stick calibration is considered active if it occurred in the last 15 mins
                    if (now - event.timestamp <= TimeUnit.MINUTES.toMillis(15) && isActive) {
                        calibrationTime = true
                    }
                }
                else -> {
                    // Other event types not relevant for the flags set here
                }
            }
        }

        if (stopTime) {
            resetAllStates()
            clearActiveEvent("sleep")
            clearActiveEvent("sport")
            clearActiveEvent("snack")
            clearActiveEvent("lowcarb")
            clearActiveEvent("highcarb")
            clearActiveEvent("meal")
            clearActiveEvent("bfast")
            clearActiveEvent("lunch")
            clearActiveEvent("dinner")
            clearActiveEvent("fasting")
            clearActiveEvent("delete")
        }
    }

    private fun clearActiveEvent(noteKeyword: String) {
        runBlocking {
            persistenceLayer.invalidateTherapyEventsWithNote(noteKeyword, app.aaps.core.data.ue.Action.TREATMENT, app.aaps.core.data.ue.Sources.Aaps)
        }
    }

    private fun resetAllStates() {
        sleepTime = false
        sportTime = false
        snackTime = false
        lowCarbTime = false
        highCarbTime = false
        mealTime = false
        bfastTime = false
        lunchTime = false
        dinnerTime = false
        fastingTime = false
        deleteTime = false
    }

    private fun extractDateFromDeleteEvent(note: String?): String? {
        val deletePattern = Pattern.compile("delete (\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE)
        val matcher = deletePattern.matcher(note ?: "")
        return if (matcher.find()) {
            matcher.group(1) // DD/MM/YYYY
        } else {
            null
        }
    }

    /**
     * Returns the time elapsed since the last event matching the keyword in minutes.
     * Reuses cached events to avoid DB queries.
     */
    fun getTimeElapsedSinceLastEvent(keyword: String): Long {
        val now = System.currentTimeMillis()
        val lastEvent = cachedEvents.asSequence()
            .filter { it.type == TE.Type.NOTE }
            .filter { it.note?.contains(keyword, ignoreCase = true) == true }
            .maxByOrNull { it.timestamp }

        lastEvent?.let {
            val elapsedMinutes = (now - it.timestamp) / 60000
            // Original code only looked back 60 minutes in the database query
            if (elapsedMinutes <= 60) return elapsedMinutes
        }
        return -1  // Return -1 if no event found within 60 minutes
    }
}
