package dev.whekin.whfin.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun statisticsShowsTheShapeOfSpendingAndSendsTheItemisingToSpending() {
        var opened = false
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onOpenExpenses = { opened = true },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(5)
        // The itemised list lives on Spending alone; Statistics must not carry a second one.
        compose.onNodeWithTag("analytics-category-1").assertDoesNotExist()
        compose.onNodeWithTag("analytics-open-categories").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun trendBarSelectionUpdatesAmountAndOpensMatchingTransactions() {
        var opened: AnalyticsTransactionsRequest? = null
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData.copy(
                            period = AnalyticsPeriod.month(month),
                            trendFilter = AnalyticsTrendFilter.Category(1),
                            trendFilterName = "Food",
                        )),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-trend").performScrollTo()
        compose.onNodeWithTag("whfin-monthly-bar-5").performClick()
        compose.onNodeWithTag("analytics-selected-trend-amount").assertTextEquals("60.00 ₾")
        compose.waitForIdle()
        compose.onNodeWithTag("analytics-view-transactions").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 1_000) { opened != null }

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 6), opened?.period?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(1L, opened?.categoryId)
            assertEquals(6_000L, opened?.expectedExpenseMinor)
        }
    }

    @Test
    fun categoryDriverOpensCurrentMonthTransactions() {
        var opened: AnalyticsTransactionsRequest? = null
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(4)
        compose.onNodeWithTag("analytics-change-2").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 7), opened?.period?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(2L, opened?.categoryId)
            assertEquals("Transport", opened?.filterName)
            assertEquals(30_000L, opened?.expectedExpenseMinor)
        }
    }

    @Test
    fun expenseMetricOpensFocusedSpendingAnalysis() {
        var opened = false
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onOpenExpenses = { opened = true },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-open-expenses").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun monthHeaderOffersItsYearTotalWithoutASegmentedModeSwitch() {
        var scale: AnalyticsScale? = null
        compose.setContent {
            WhfinTheme {
                AnalyticsContent(
                    model = model(contentData),
                    onBack = {},
                    onPreviousPeriod = {},
                    onNextPeriod = {},
                    onScaleChange = { scale = it },
                    onSelectMonth = {},
                    onShowAllTrend = {},
                    onOpenExpenses = {},
                    onOpenTransactions = {},
                )
            }
        }

        compose.onNodeWithTag("analytics-view-year").performClick()
        compose.runOnIdle { assertEquals(AnalyticsScale.YEAR, scale) }
        compose.onNodeWithTag("analytics-scale-month").assertDoesNotExist()
        compose.onNodeWithTag("analytics-scale-year").assertDoesNotExist()
    }

    @Test
    fun focusedSpendingSelectsCategoryAndShowsItsComparison() {
        var filter by mutableStateOf<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        model = model(contentData.copy(
                            spendingAverageMinor = 60_000,
                            categoryValues = listOf(
                                AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000, 40_000),
                                AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 35_000),
                            ),
                            trendFilter = filter,
                            trendFilterName = (filter as? AnalyticsTrendFilter.Category)?.let { "Food" },
                        )),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = { filter = AnalyticsTrendFilter.All },
                        onShowCategoryTrend = { filter = AnalyticsTrendFilter.Category(it) },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(5)
        // A category row states its distance from the comparison base as a signed number; the
        // sentence naming that base is printed once, on the section heading.
        compose.onNodeWithText("+100.00 ₾", substring = true).assertExists()
        compose.onNodeWithTag("expense-category-1").performClick()
        compose.runOnIdle { assertEquals(AnalyticsTrendFilter.Category(1), filter) }
    }

    @Test
    fun lastTwelveMonthsSelectionMovesTheWholeStatisticsPeriod() {
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData.copy(period = AnalyticsPeriod.month(month))),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        // The month title is a control now — it zooms out to its year — so the text it prints
        // lives one level below the merged click target.
        compose.onNodeWithTag("analytics-period-title", useUnmergedTree = true).assertTextEquals("July 2026")
        compose.onNodeWithTag("analytics-trend").performScrollTo()
        compose.onNodeWithTag("whfin-monthly-bar-5").performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 6), month) }
        compose.onNodeWithTag("analytics-list").performScrollToIndex(1)
        compose.onNodeWithTag("analytics-period-title", useUnmergedTree = true).assertTextEquals("June 2026")
    }

    @Test
    fun focusedSpendingMonthBarRefreshesRingAndCategorySummary() {
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            val category = if (month == YearMonth.of(2026, 7)) {
                AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000, 40_000)
            } else {
                AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 35_000)
            }
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        model = model(contentData.copy(
                            period = AnalyticsPeriod.month(month),
                            expenseMinor = category.expenseMinor,
                            categoryValues = listOf(category),
                        )),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(5)
        compose.onNodeWithText("Food").assertExists()
        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(4)
        compose.onNodeWithTag("whfin-monthly-bar-5").performClick()
        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(5)
        compose.onNodeWithText("Transport").assertExists()
        compose.onNodeWithText("Food").assertDoesNotExist()
    }

    @Test
    fun statisticsKeepsLaterTrendMonthReachableAfterSelectingEarlierMonth() {
        var month by mutableStateOf(YearMonth.of(2026, 8))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = model(contentData.copy(
                            period = AnalyticsPeriod.month(month),
                            trendValues = trendForYear(2026),
                        )),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-trend").performScrollTo()
        compose.onNodeWithContentDescription("July 2026, 2,132.05 ₾").performClick()
        compose.onNodeWithContentDescription("August 2026, 321.54 ₾").assertExists().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 8), month) }
    }

    @Test
    fun spendingKeepsLaterTrendMonthReachableAfterSelectingEarlierMonth() {
        var month by mutableStateOf(YearMonth.of(2026, 8))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        model = model(contentData.copy(
                            period = AnalyticsPeriod.month(month),
                            trendValues = trendForYear(2026),
                        )),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(4)
        compose.onNodeWithContentDescription("July 2026, 2,132.05 ₾").performClick()
        compose.onNodeWithContentDescription("August 2026, 321.54 ₾").assertExists().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 8), month) }
    }

    @Test
    fun merchantsRankByAmountUntilAskedForRepetitionAndOpenThatCounterpartyAlone() {
        var opened: AnalyticsTransactionsRequest? = null
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        model = model(contentData.copy(merchantValues = merchantValues)),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = {},
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(5)
        // Six rows is the default depth; the seventh counterparty is behind the expander.
        compose.onNodeWithTag("expense-merchant-7").assertDoesNotExist()
        compose.onNodeWithTag("expense-merchants-expand").performScrollTo().performClick()
        compose.onNodeWithTag("expense-merchant-7").assertExists()

        // Sorting by count is not a reordering of the same answer: the shop paid thirty-one small
        // times outranks the one large purchase only under this reading.
        compose.onNodeWithTag("expense-merchants-sort-count").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("expense-merchant-3").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 1_000) { opened != null }

        compose.runOnIdle {
            assertEquals(true, opened?.merchantFilterEnabled)
            assertEquals(3L, opened?.merchantId)
            assertEquals(24_300L, opened?.expectedExpenseMinor)
            // Nothing was selected on the ring, so the list is the whole period's Bolt payments.
            assertEquals(false, opened?.categoryFilterEnabled)
            assertEquals("Bolt", opened?.filterName)
        }
    }

    @Test
    fun merchantRowsStayWholeAtLargeText() {
        // The two sort choices are the part that can run out of width: "By payment count" is a long
        // label in both languages. They ride the shared rail, which scrolls rather than wrapping,
        // so both stay reachable and the rows underneath keep their amounts.
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                WhfinTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ExpenseAnalysisContent(
                            model = model(contentData.copy(merchantValues = merchantValues)),
                            onBack = {},
                            onPreviousPeriod = {},
                            onNextPeriod = {},
                            onScaleChange = {},
                            onSelectMonth = {},
                            onShowAllTrend = {},
                            onShowCategoryTrend = {},
                            onOpenTransactions = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(5)
        compose.onNodeWithTag("expense-merchants-sort-amount").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("expense-merchants-sort-count").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("expense-merchant-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("614.00 ₾").assertIsDisplayed()
    }

    @Test
    fun draggingThePageMovesThroughTimeLikeTheArrowsDo() {
        var moved: String? = null
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        model = AnalyticsUiModel(
                            period = contentData.period,
                            canSelectPrevious = true,
                            canSelectNext = true,
                            state = AnalyticsUiState.Content(contentData),
                        ),
                        onBack = {},
                        onPreviousPeriod = { moved = "previous" },
                        onNextPeriod = { moved = "next" },
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performTouchInput { swipeLeft() }
        compose.waitUntil(timeoutMillis = 2_000) { moved != null }
        compose.runOnIdle { assertEquals("next", moved) }

        moved = null
        compose.onNodeWithTag("analytics-list").performTouchInput { swipeRight() }
        compose.waitUntil(timeoutMillis = 2_000) { moved != null }
        compose.runOnIdle { assertEquals("previous", moved) }
    }

    @Test
    fun aDragTowardsAPeriodThatDoesNotExistChangesNothing() {
        var moved = false
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        // canSelectNext is false: the current month has no month after it.
                        model = model(contentData),
                        onBack = {},
                        onPreviousPeriod = {},
                        onNextPeriod = { moved = true },
                        onScaleChange = {},
                        onSelectMonth = {},
                        onShowAllTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(false, moved) }
    }

    private val merchantValues = listOf(
        AnalyticsMerchantValue(1, "Agrohub", 61_400, 9),
        AnalyticsMerchantValue(2, "Carrefour", 39_700, 4),
        AnalyticsMerchantValue(3, "Bolt", 24_300, 31),
        AnalyticsMerchantValue(4, "Wolt", 18_900, 12),
        AnalyticsMerchantValue(5, "Silknet", 12_000, 1),
        AnalyticsMerchantValue(6, "Aversi", 9_450, 3),
        AnalyticsMerchantValue(7, "Nikora", 7_820, 6),
    )

    private fun model(data: AnalyticsData) = AnalyticsUiModel(
        period = data.period,
        canSelectPrevious = true,
        canSelectNext = false,
        state = AnalyticsUiState.Content(data),
    )

    private fun trendForYear(year: Int): List<AnalyticsMonthValue> =
        (1..12).map { monthNumber ->
            val month = YearMonth.of(year, monthNumber)
            AnalyticsMonthValue(
                month = month,
                expenseMinor = when (month) {
                    YearMonth.of(2026, 7) -> 213_205L
                    YearMonth.of(2026, 8) -> 32_154L
                    else -> 0L
                },
            )
        }

    private val contentData = AnalyticsData(
        period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
        incomeMinor = 400_000,
        expenseMinor = 80_000,
        categoryValues = listOf(
            AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000),
            AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000),
        ),
        trendFilter = AnalyticsTrendFilter.All,
        trendFilterName = null,
        trendValues = (1..12).map {
            AnalyticsMonthValue(YearMonth.of(2026, it), it * 1_000L)
        },
        previousTrendExpenseMinor = 6_000,
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
        pace = AnalyticsPace(
            daysElapsed = 20,
            daysTotal = 31,
            projectedExpenseMinor = 124_000,
            previousPeriodExpenseMinor = 70_000,
        ),
        categoryChanges = listOf(
            AnalyticsCategoryChange(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 20_000),
        ),
    )
}
