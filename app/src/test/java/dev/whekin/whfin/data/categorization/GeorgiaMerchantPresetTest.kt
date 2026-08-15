package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeorgiaMerchantPresetTest {
    private val groceries = CategoryEntity(name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0)
    private val transport = CategoryEntity(name = "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0)
    private val subscriptions = CategoryEntity(name = "Subscriptions", kind = CategoryKind.EXPENSE, icon = "Subscriptions", color = 0)
    private val utilities = CategoryEntity(name = "Utilities", kind = CategoryKind.EXPENSE, icon = "Bolt", color = 0)
    private val bike = CategoryEntity(name = "Bike", kind = CategoryKind.EXPENSE, icon = "PedalBike", color = 0)
    private val salary = CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, icon = "Payments", color = 0)
    private val categories = listOf(groceries, transport, subscriptions, utilities, bike, salary)

    @Test fun `Georgian supermarket variants are groceries`() {
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("nikora trade jsc", categories))
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("carrefour (dolidze)", categories))
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("libre", categories))
    }

    @Test fun `Tbilisi transit is recognized`() {
        assertEquals(transport, GeorgiaMerchantPreset.categoryFor("bus_tbilisi", categories))
        assertEquals(transport, GeorgiaMerchantPreset.categoryFor("yandex go", categories))
        assertEquals(transport, GeorgiaMerchantPreset.categoryFor("yandex*go taxi", categories))
    }

    /**
     * Who pays a given person cannot be read off a company name. One Georgian LLC paying out a
     * currency conversion every month was indistinguishable from an employer paying a salary until
     * its own ledger was read, so the preset stays out of income entirely.
     */
    @Test fun `no company is assumed to be an employer`() {
        assertNull(GeorgiaMerchantPreset.categoryFor("შპს უნოტრონ", categories))
        assertNull(GeorgiaMerchantPreset.categoryFor("shps unotron", categories))
        assertEquals(
            emptyList<CategoryEntity>(),
            categories.filter { it.kind == CategoryKind.INCOME }
                .filter { income -> GeorgiaMerchantPreset.categoryFor(income.name.lowercase(), categories) != null },
        )
    }

    @Test fun `unknown merchant stays unclassified`() {
        assertNull(GeorgiaMerchantPreset.categoryFor("i m unknown person", categories))
    }

    @Test fun `reviewed personal merchants use narrow local rules`() {
        assertEquals(transport, GeorgiaMerchantPreset.categoryFor("jetshr", categories))
        assertEquals(utilities, GeorgiaMerchantPreset.categoryFor("FLT*MySilknetAPP", categories))
        assertEquals(utilities, GeorgiaMerchantPreset.categoryFor("salerequest.silknet", categories))
        assertEquals(bike, GeorgiaMerchantPreset.categoryFor("Bike24 GmbH", categories))
        assertEquals(subscriptions, GeorgiaMerchantPreset.categoryFor("GOOGLE *Smart Launcher", categories))
        assertEquals(subscriptions, GeorgiaMerchantPreset.categoryFor("SONGSTERR", categories))
        assertEquals(subscriptions, GeorgiaMerchantPreset.categoryFor("ANTHROPIC* CLAUDE SUB", categories))
    }

    @Test fun `generic payment processors remain unclassified`() {
        assertNull(GeorgiaMerchantPreset.categoryFor("GOOGLE PLAY", categories))
        assertNull(GeorgiaMerchantPreset.categoryFor("PAYPAL", categories))
        assertNull(GeorgiaMerchantPreset.categoryFor("VIP PAY", categories))
    }
}
