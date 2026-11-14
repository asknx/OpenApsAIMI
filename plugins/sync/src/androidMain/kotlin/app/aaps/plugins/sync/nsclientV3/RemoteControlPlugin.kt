package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.sync.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Remote Control Plugin for NSClient Parent App.
 * Provides secure interface for parents to send AIMI commands remotely.
 * Only visible in AAPSClient (not in AAPS main app).
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(390)
@SingleIn(AppScope::class)
class RemoteControlPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    private val config: Config,
    notificationManager: NotificationManager
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(TextRef.AndroidRes(R.string.remote_control_title))
        .description(TextRef.AndroidRes(R.string.remote_control_description))
        .showInList { config.AAPSCLIENT },
    aapsLogger,
    rh,
    notificationManager
)
