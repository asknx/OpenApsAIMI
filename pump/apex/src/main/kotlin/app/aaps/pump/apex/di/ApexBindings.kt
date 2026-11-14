package app.aaps.pump.apex.di

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.misc.ApexDeviceInfoImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI bindings for the Apex pump module.
 *
 * Replaces the Dagger-era ApexModule / ApexServicesModule / ApexUiModule triad.
 *
 * Notes:
 * - [ApexDriverStatus], [ApexBluetooth], [ApexService] and [ApexFragment] all carry
 *   `@Inject` constructors / field-injection and are either scoped via `@SingleIn`
 *   on the class or wired through the FeatureMemberInjectors map (see ApexMemberInjectors).
 * - [ApexDeviceInfo] cannot be contributed from [ApexDeviceInfoImpl] directly
 *   because that class lives in a separate package — the binding is declared here.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApexBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideApexDeviceInfo(impl: ApexDeviceInfoImpl): ApexDeviceInfo = impl
}
