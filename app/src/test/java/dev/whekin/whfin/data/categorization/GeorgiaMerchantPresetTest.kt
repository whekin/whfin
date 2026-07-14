package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeorgiaMerchantPresetTest {
    private val groceries = CategoryEntity(name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0)
    private val transport = CategoryEntity(name = "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0)
    private val salary = CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, icon = "Payments", color = 0)
    private val categories = listOf(groceries, transport, salary)

    @Test fun `Georgian supermarket variants are groceries`() {
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("nikora trade jsc", categories))
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("carrefour (dolidze)", categories))
        assertEquals(groceries, GeorgiaMerchantPreset.categoryFor("libre", categories))
    }

    @Test fun `Tbilisi transit and salary are recognized`() {
        assertEquals(transport, GeorgiaMerchantPreset.categoryFor("bus_tbilisi", categories))
        assertEquals(salary, GeorgiaMerchantPreset.categoryFor("შპს უნოტრონ", categories))
    }

    @Test fun `unknown merchant stays unclassified`() {
        assertNull(GeorgiaMerchantPreset.categoryFor("i m unknown person", categories))
    }
}
