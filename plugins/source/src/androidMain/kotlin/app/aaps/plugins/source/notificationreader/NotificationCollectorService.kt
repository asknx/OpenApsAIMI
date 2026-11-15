package app.aaps.plugins.source.notificationreader

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.source.NotificationReaderPlugin
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCollectorService : NotificationListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var notificationReaderPlugin: NotificationReaderPlugin
    @Inject lateinit var xdripSourcePlugin: app.aaps.plugins.source.XdripSourcePlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var preferences: Preferences

    private var parser: NotificationParser? = null
    private var deduplicator: GlucoseDeduplicator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // NotificationListenerService is its own framework base class, so this cannot use MetroService.
        // Calling the injector directly is what MetroService does anyway.
        (applicationContext as MetroMemberInjector).injectMembers(this)
        parser = NotificationParser(notificationReaderPlugin.packageConfig)
        deduplicator = GlucoseDeduplicator(
            packageConfig = notificationReaderPlugin.packageConfig,
            store = object : GlucoseDeduplicator.StateStore {
                override fun load(): String? =
                    preferences.get(StringNonKey.NotificationReaderDedupState).takeIf { it.isNotBlank() }

                override fun save(json: String) {
                    preferences.put(StringNonKey.NotificationReaderDedupState, json)
                }
            }
        )
        aapsLogger.debug(LTag.BGSOURCE, "NotificationCollectorService created")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        aapsLogger.debug(LTag.BGSOURCE, "NotificationCollectorService destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!notificationReaderPlugin.packageConfig.isSupportedPackage(packageName)) return

        val isXdripPkg = packageName == "com.eveningoutpost.dexdrip" ||
            packageName == "com.eveningoutpost.dexdrip.debug" ||
            packageName == "tk.glucodata"

        val canHandleNotificationReader = notificationReaderPlugin.isEnabled()
        val canHandleXdrip = isXdripPkg && xdripSourcePlugin.isEnabled()

        if (!canHandleNotificationReader && !canHandleXdrip) return

        aapsLogger.debug(LTag.BGSOURCE, "Notification from: $packageName (xdripMode=$canHandleXdrip)")
        processNotification(sbn.notification, packageName, isXdrip = canHandleXdrip)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    private fun processNotification(notification: Notification?, packageName: String, isXdrip: Boolean = false) {
        if (notification == null) return

        val texts = extractTexts(notification)
        if (texts.isEmpty()) {
            aapsLogger.debug(LTag.BGSOURCE, "No text in notification from $packageName")
            return
        }

        aapsLogger.debug(LTag.BGSOURCE, "Extracted texts: $texts")

        val useMgdl = profileFunction.getUnits() == GlucoseUnit.MGDL
        val result = parser?.extractGlucose(texts, packageName, useMgdl) ?: return

        aapsLogger.debug(LTag.BGSOURCE, "Glucose: ${result.glucoseMgdl} mg/dL from $packageName (${result.sourceSensor}) trend=${result.trendArrow}")

        val now = System.currentTimeMillis()
        if (deduplicator?.process(packageName, now) != true) {
            aapsLogger.debug(LTag.BGSOURCE, "Skipping duplicate notification from $packageName")
            return
        }

        val readingTimestamp = if (notification.`when` > 0L && kotlin.math.abs(now - notification.`when`) < 300_000L) {
            notification.`when`
        } else {
            now
        }

        val gv = GV(
            timestamp = readingTimestamp,
            value = result.glucoseMgdl.toDouble(),
            raw = null,
            noise = null,
            trendArrow = result.trendArrow,
            sourceSensor = if (isXdrip) SourceSensor.UNKNOWN else result.sourceSensor
        )

        scope.launch {
            try {
                val source = if (isXdrip) Sources.Xdrip else Sources.NotificationReader
                persistenceLayer.insertCgmSourceData(source, listOf(gv), emptyList(), null)
                if (isXdrip) {
                    app.aaps.plugins.source.XdripSourcePlugin.XdripSourceWorker.lastDataTimestamp =
                        maxOf(app.aaps.plugins.source.XdripSourcePlugin.XdripSourceWorker.lastDataTimestamp, readingTimestamp)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "Error inserting glucose data", e)
            }
        }
    }

    /**
     * Extract visible text from notification.
     * Tries standard extras first (modern apps), falls back to RemoteViews inflation (legacy).
     */
    private fun extractTexts(notification: Notification): List<String> {
        val extras = notification.extras ?: return emptyList()

        // Modern notifications: extract from standard extras
        val texts = buildList {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (texts.isNotEmpty()) return texts

        // Legacy: inflate custom RemoteViews
        @Suppress("DEPRECATION")
        notification.contentView?.let { remoteViews ->
            try {
                val applied = remoteViews.apply(this, null)
                val root = applied.rootView as? ViewGroup ?: return@let
                val inflated = root.collectVisibleText()
                if (inflated.isNotEmpty()) return inflated
            } catch (e: Exception) {
                aapsLogger.debug(LTag.BGSOURCE, "RemoteViews inflation failed: ${e.message}")
            }
        }

        return emptyList()
    }
}

/**
 * Recursively collect text from all visible TextViews in a ViewGroup hierarchy.
 */
private fun ViewGroup.collectVisibleText(): List<String> = buildList {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child.visibility != View.VISIBLE) continue
        when (child) {
            is TextView -> child.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            is ViewGroup -> addAll(child.collectVisibleText())
        }
    }
}
