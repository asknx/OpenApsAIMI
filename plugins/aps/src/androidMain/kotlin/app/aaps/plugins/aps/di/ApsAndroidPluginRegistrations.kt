package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.openAPSAIMI.OpenAPSAIMIPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object ApsAndroidPluginRegistrations {

    @Provides
    @IntoMap
    @IntKey(224)
    fun openApsAimiEntry(plugin: OpenAPSAIMIPlugin): PluginBase = plugin
}
