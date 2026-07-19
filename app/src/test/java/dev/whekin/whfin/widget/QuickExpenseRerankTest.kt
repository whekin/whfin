package dev.whekin.whfin.widget

import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickExpenseRerankTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun xOfOrNull(description: String): Float? =
        compose.onAllNodes(hasContentDescription(description))
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.positionInRoot?.x

    private fun xOf(description: String): Float =
        checkNotNull(xOfOrNull(description)) { "node $description not found" }

    private fun bigBeforeSmall(): Boolean {
        val big = xOfOrNull("RerankBig") ?: return false
        val small = xOfOrNull("RerankSmall")
        // После snap-to-start прежний лидер может закономерно уйти вправо за
        // lazy viewport; видимый новый лидер уже доказывает пере-ранжировку.
        return small == null || big < small
    }

    /**
     * waitUntil в Robolectric крутит виртуальные часы и обгоняет реальный IO-поток,
     * на котором грузятся категории, — ждём условие по настенному времени.
     */
    private fun awaitReal(
        timeoutMillis: Long = 5_000,
        diagnostic: () -> String = { "condition remained false" },
        condition: () -> Boolean,
    ) {
        val start = System.currentTimeMillis()
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() - start < timeoutMillis) {
                "Timed out waiting for condition: ${diagnostic()}"
            }
            Thread.sleep(25)
        }
    }

    @Test
    fun typedAmountLiftsAmountCompatibleCategory() {
        val app = ApplicationProvider.getApplicationContext<Application>() as WhfinApp
        val db = app.userDb
        val now = System.currentTimeMillis()
        runBlocking {
            val accountId = db.accountDao().insert(
                AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"),
            )
            val frequentSmall = db.categoryDao().insert(
                CategoryEntity(name = "RerankSmall", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 1),
            )
            val rareBig = db.categoryDao().insert(
                CategoryEntity(name = "RerankBig", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 2),
            )
            // Частая «мелкая» категория: 20 операций по ~2 GEL.
            repeat(20) { index ->
                db.transactionDao().insert(
                    TransactionEntity(
                        accountId = accountId, amountMinor = -(180L + (index % 3) * 20), currency = "GEL",
                        occurredAt = now - index * 86_400_000L, categoryId = frequentSmall,
                        status = TxStatus.MANUAL, source = TxSource.MANUAL, createdAt = 1,
                    ),
                )
            }
            // Редкая «крупная»: 6 операций по ~120 GEL.
            repeat(6) { index ->
                db.transactionDao().insert(
                    TransactionEntity(
                        accountId = accountId, amountMinor = -(11_000L + (index % 3) * 1_000), currency = "GEL",
                        occurredAt = now - index * 4 * 86_400_000L, categoryId = rareBig,
                        status = TxStatus.MANUAL, source = TxSource.MANUAL, createdAt = 1,
                    ),
                )
            }
        }

        ActivityScenario.launch(QuickExpenseActivity::class.java).use {
            awaitReal { xOfOrNull("RerankBig") != null }
            // Без суммы частая категория стоит левее.
            assertTrue("frequency order broken", xOf("RerankSmall") < xOf("RerankBig"))

            compose.onAllNodes(hasSetTextAction())[0].performTextInput("120")
            awaitReal(
                diagnostic = {
                    "big=${xOfOrNull("RerankBig")}, small=${xOfOrNull("RerankSmall")}"
                },
            ) { bigBeforeSmall() }
            assertTrue("amount rerank did not happen", bigBeforeSmall())
        }
    }
}
