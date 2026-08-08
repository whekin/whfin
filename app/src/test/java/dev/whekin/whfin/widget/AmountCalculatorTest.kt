package dev.whekin.whfin.widget

import dev.whekin.whfin.core.ui.WhfinAmountKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountCalculatorTest {
    @Test
    fun `basic operators evaluate left to right`() {
        val result = keys(
            WhfinAmountKey.DIGIT_1,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.ADD,
            WhfinAmountKey.DIGIT_5,
            WhfinAmountKey.MULTIPLY,
            WhfinAmountKey.DIGIT_2,
            WhfinAmountKey.EQUALS,
        )

        assertEquals("30", result.display)
    }

    @Test
    fun `percent follows calculator convention for addition`() {
        val result = keys(
            WhfinAmountKey.DIGIT_2,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.ADD,
            WhfinAmountKey.DIGIT_1,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.PERCENT,
            WhfinAmountKey.EQUALS,
        )

        assertEquals("220", result.display)
    }

    @Test
    fun `division rounds to currency precision`() {
        val result = keys(
            WhfinAmountKey.DIGIT_1,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.DIVIDE,
            WhfinAmountKey.DIGIT_3,
            WhfinAmountKey.EQUALS,
        )

        assertEquals("3.33", result.display)
    }

    @Test
    fun `division by zero is recoverable with next digit`() {
        val error = keys(
            WhfinAmountKey.DIGIT_1,
            WhfinAmountKey.DIVIDE,
            WhfinAmountKey.DIGIT_0,
            WhfinAmountKey.EQUALS,
        )
        assertTrue(error.error)

        assertEquals("7", error.press(WhfinAmountKey.DIGIT_7).display)
    }

    @Test
    fun `save resolution evaluates pending expression`() {
        val calculator = keys(
            WhfinAmountKey.DIGIT_1,
            WhfinAmountKey.DECIMAL,
            WhfinAmountKey.DIGIT_5,
            WhfinAmountKey.ADD,
            WhfinAmountKey.DIGIT_2,
        )

        assertEquals("3.5", calculator.resolvedText())
    }

    private fun keys(vararg keys: WhfinAmountKey): AmountCalculator =
        keys.fold(AmountCalculator()) { calculator, key -> calculator.press(key) }
}
