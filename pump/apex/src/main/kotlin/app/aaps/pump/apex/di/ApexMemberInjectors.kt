package app.aaps.pump.apex.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.apex.ApexDriverStatus
import app.aaps.pump.apex.ApexService
import app.aaps.pump.apex.connectivity.ApexBluetooth
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Metro member-injectors for Apex Android entry-points.
 *
 * [ApexDriverStatus] and [ApexBluetooth] are `@Inject`-constructor classes; they don't need
 * member injection. Only Android framework types that Metro cannot construct via constructor
 * injection need to be wired here (Activity, Service with their own lifecycle).
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApexMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ApexService::class)
    fun bindApexService(injector: MembersInjector<ApexService>): MembersInjector<*> = injector
}
