package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.max

class LoadBgWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var nsClientSource: NSClientSource
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var persistenceLayer: PersistenceLayer

    override suspend fun doWorkAndLog(): Result {
        if (!nsClientSource.isEnabled() && !preferences.get(BooleanKey.NsClientAcceptCgmData) && !nsClientV3Plugin.doingFullSync)
            return Result.success(workDataOf("Result" to "Load not enabled"))

        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
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
                    if (isFirstLoad) {
                        response = nsAndroidClient.getSgvsNewerThan(max(lastLoaded, advanceTimestamp), NSClientV3Plugin.RECORDS_TO_LOAD)
                    } else if (nsClientSource.isEnabled()) {
                        // xDrip-style: If NS is source, always poll by measurement date for reliability
                        response = nsAndroidClient.getSgvsNewerThan(max(lastGlucose?.timestamp ?: 0L, advanceTimestamp), NSClientV3Plugin.RECORDS_TO_LOAD)
                    } else {
                        response = nsAndroidClient.getSgvsModifiedSince(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
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
                    aapsLogger.debug(LTag.NSCLIENT, "SGVS: $sgvs")
                    if (sgvs.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-F" else "RCV"
                        rxBus.send(EventNSClientNewLog("◄ $action", "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(max(lastGlucose?.timestamp ?: 0L, advanceTimestamp))}"))
                        // Schedule processing of fetched data and continue of loading
                        val newestTimestamp = nsIncomingDataProcessor.processSgvs(sgvs, nsClientV3Plugin.doingFullSync)
                        continueLoading = response.code != 304 && newestTimestamp > advanceTimestamp
                        advanceTimestamp = newestTimestamp
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        rxBus.send(EventNSClientNewLog("◄ RCV BG END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        continueLoading = false
                    }
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    rxBus.send(EventNSClientNewLog("◄ RCV BG END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    continueLoading = false
                }
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        storeDataForDb.storeGlucoseValuesToDb()
        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}