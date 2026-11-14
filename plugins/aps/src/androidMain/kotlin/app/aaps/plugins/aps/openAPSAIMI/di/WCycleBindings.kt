package app.aaps.plugins.aps.openAPSAIMI.di

import android.content.Context
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleAdjuster
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleCsvLogger
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleEstimator
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleFacade
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleLearner
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCyclePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object WCycleBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCyclePreferences(preferences: Preferences): WCyclePreferences =
        WCyclePreferences(preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCycleEstimator(preferences: WCyclePreferences): WCycleEstimator =
        WCycleEstimator(preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCycleLearner(context: Context): WCycleLearner =
        WCycleLearner(ctx = context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCycleAdjuster(
        preferences: WCyclePreferences,
        estimator: WCycleEstimator,
        learner: WCycleLearner
    ): WCycleAdjuster =
        WCycleAdjuster(preferences, estimator, learner)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCycleCsvLogger(context: Context): WCycleCsvLogger =
        WCycleCsvLogger(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWCycleFacade(
        adjuster: WCycleAdjuster,
        logger: WCycleCsvLogger
    ): WCycleFacade =
        WCycleFacade(adjuster, logger)

    @Provides
    @SingleIn(AppScope::class)
    fun provideEndometriosisAdjuster(
        preferences: Preferences,
        aapsLogger: app.aaps.core.interfaces.logging.AAPSLogger
    ): app.aaps.plugins.aps.openAPSAIMI.wcycle.EndometriosisAdjuster =
        app.aaps.plugins.aps.openAPSAIMI.wcycle.EndometriosisAdjuster(preferences, aapsLogger)
}
