package dev.whekin.whfin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every screen's view model must still be constructible by the default factory.
 *
 * The factory finds the constructor by reflection, so an extra parameter — even one with a Kotlin
 * default, which reads as optional at the call site — removes the `(Application)` constructor it
 * looks for and the screen dies the moment it is opened. Nothing else catches this: a Compose test
 * renders the stateless screen, and a repository test never goes through a factory, so the failure
 * only appears on a device.
 */
class ViewModelConstructorTest {

    private val viewModels = listOf(
        dev.whekin.whfin.ui.accounts.AccountsViewModel::class.java,
        dev.whekin.whfin.ui.accounts.AccountTransactionsViewModel::class.java,
        dev.whekin.whfin.ui.analytics.AnalyticsViewModel::class.java,
        dev.whekin.whfin.ui.analytics.AnalyticsTransactionsViewModel::class.java,
        dev.whekin.whfin.ui.feed.FeedViewModel::class.java,
        dev.whekin.whfin.ui.settings.BankStatementsViewModel::class.java,
        dev.whekin.whfin.ui.settings.CategoriesViewModel::class.java,
        dev.whekin.whfin.ui.settings.CategoryIntelligenceViewModel::class.java,
        dev.whekin.whfin.ui.settings.DataHealthViewModel::class.java,
        dev.whekin.whfin.ui.settings.IncomeSourcesViewModel::class.java,
        dev.whekin.whfin.ui.settings.PeopleViewModel::class.java,
        dev.whekin.whfin.ui.settings.SmsDiagnosticsViewModel::class.java,
    )

    @Test
    fun `every view model can be built the way the framework builds it`() {
        val unbuildable = viewModels.filter { type ->
            val constructable = runCatching {
                if (AndroidViewModel::class.java.isAssignableFrom(type)) {
                    type.getConstructor(Application::class.java)
                } else {
                    type.getConstructor()
                }
            }.isSuccess
            !constructable
        }

        assertEquals(emptyList<Class<out ViewModel>>(), unbuildable)
    }
}
