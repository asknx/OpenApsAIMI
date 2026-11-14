package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import dev.zacsweers.metro.Inject
import kotlin.math.max

/**
 * Fetches glucose values (and the calibration entries that ride the same cursor) from Nightscout.
 *
 * The body of `LoadBgWorker`, split out so it is plain Kotlin. Nothing here knows about WorkManager;
 * the worker is a shim that maps [WorkOutcome] onto a `ListenableWorker.Result`.
 */
@Inject
class LoadBgRunner(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientSource: NSClientSource,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val storeDataForDb: StoreDataForDb,
    private val nsClientRepository: NSClientRepository,
    private val persistenceLayer: PersistenceLayer
) {

    suspend fun run(): WorkOutcome {
        if (!nsClientSource.isEnabled() && !preferences.get(BooleanKey.NsClientAcceptCgmData) && !nsClientV3Plugin.doingFullSync)
            return WorkOutcome.Skipped("Load not enabled")

        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")
        var continueLoading = true
        var advanceTimestamp = 0L
        try {
            while (continueLoading) {
                val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.ENTRIES)
                val lastLoaded =
                    if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
                    else max(nsClientV3Plugin.lastLoadedSrvModified.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
                val newestOnServer = nsClientV3Plugin.newestDataOnServer?.collections?.entries ?: Long.MAX_VALUE
                val lastGlucose = persistenceLayer.getLastGlucoseValue()
                val dataIsOld = (lastGlucose?.timestamp ?: 0L) < dateUtil.now() - T.mins(3).plus(T.secs(10)).msecs()

                if (newestOnServer > lastLoaded || (nsClientSource.isEnabled() && dataIsOld)) {
                    if (newestOnServer <= lastLoaded) {
                        aapsLogger.info(LTag.NSCLIENT, "Forcing BG load because data is old (${dateUtil.dateAndTimeAndSecondsString(lastGlucose?.timestamp ?: 0L)}) and NS is source. newestOnServer: $newestOnServer lastLoaded: $lastLoaded")
                    }
                    val sgvs: List<NSSgvV3>
                    val response: NSAndroidClient.ReadResponse<List<NSSgvV3>>
                    var cursorStalled = false
                    if (isFirstLoad) {
                        response = nsAndroidClient.getSgvsNewerThan(max(lastLoaded, advanceTimestamp), NSClientV3Plugin.RECORDS_TO_LOAD)
                    } else if (nsClientSource.isEnabled()) {
                        response = nsAndroidClient.getSgvsNewerThan(max(lastGlucose?.timestamp ?: 0L, advanceTimestamp), NSClientV3Plugin.RECORDS_TO_LOAD)
                    } else {
                        response = nsAndroidClient.getSgvsModifiedSince(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                        cursorStalled = response.lastServerModified?.let { it <= lastLoaded } == true
                    }
                    aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                    val nextSrvModified = response.lastServerModified ?: response.values.mapNotNull { it.srvModified }.maxOrNull()
                    nextSrvModified?.let {
                        if (it > nsClientV3Plugin.lastLoadedSrvModified.collections.entries) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.entries = it
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                    }
                    if (!isFirstLoad) {
                        nsClientV3Plugin.scheduleIrregularExecution() // Idea is to run after 3 min after last BG
                    }
                    sgvs = response.values
                    // Calibration mbg entries ride the same entries fetch + cursor; ingest them
                    // regardless of whether there were any sgvs in this page.
                    if (response.calibrations.isNotEmpty()) {
                        nsClientRepository.addLog("◄ RCV", "${response.calibrations.size} calibrations from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        nsIncomingDataProcessor.processCalibrations(response.calibrations, nsClientV3Plugin.doingFullSync)
                    }
                    aapsLogger.debug(LTag.NSCLIENT, "SGVS: $sgvs")
                    if (sgvs.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-F" else "RCV"
                        nsClientRepository.addLog("◄ $action", "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(max(lastGlucose?.timestamp ?: 0L, advanceTimestamp))}")
                        // Schedule processing of fetched data and continue of loading
                        val processed = nsIncomingDataProcessor.processSgvs(sgvs, nsClientV3Plugin.doingFullSync, advanceInitialLoadCursor = true)
                        val maxSgvTime = sgvs.mapNotNull { it.date }.maxOrNull() ?: 0L
                        if (maxSgvTime > advanceTimestamp) advanceTimestamp = maxSgvTime
                        continueLoading = !cursorStalled && processed
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        nsClientRepository.addLog("◄ RCV BG END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        continueLoading = false
                    }
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    nsClientRepository.addLog("◄ RCV BG END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                    continueLoading = false
                }
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        storeDataForDb.storeGlucoseValuesToDb()
        storeDataForDb.storeCalibrationEntriesToDb()
        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }
}
