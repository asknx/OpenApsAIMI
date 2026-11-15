package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModeSettingsActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity
import app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object ApsMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MealAdvisorActivity::class)
    fun bindMealAdvisorActivity(injector: MembersInjector<MealAdvisorActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiProfileAdvisorActivity::class)
    fun bindAimiProfileAdvisorActivity(injector: MembersInjector<AimiProfileAdvisorActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AimiModeSettingsActivity::class)
    fun bindAimiModeSettingsActivity(injector: MembersInjector<AimiModeSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ContextActivity::class)
    fun bindContextActivity(injector: MembersInjector<ContextActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModelPickerActivity::class)
    fun bindAimiModelPickerActivity(injector: MembersInjector<app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModelPickerActivity>): MembersInjector<*> = injector
}
