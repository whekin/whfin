package dev.whekin.whfin.widget

import dev.whekin.whfin.core.ui.WhfinAmountKey
import java.math.BigDecimal
import java.math.RoundingMode

internal enum class AmountOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
}

internal data class AmountCalculator(
    val accumulator: BigDecimal? = null,
    val operator: AmountOperator? = null,
    val input: String = "",
    val error: Boolean = false,
) {
    val display: String
        get() = when {
            error -> "—"
            input.isNotEmpty() -> input
            accumulator != null -> accumulator.toInputText()
            else -> ""
        }

    val expression: String?
        get() = if (accumulator != null && operator != null) {
            "${accumulator.toInputText()} ${operator.symbol}"
        } else null

    fun press(key: WhfinAmountKey): AmountCalculator = when (key) {
        WhfinAmountKey.DIGIT_0 -> append("0")
        WhfinAmountKey.DIGIT_1 -> append("1")
        WhfinAmountKey.DIGIT_2 -> append("2")
        WhfinAmountKey.DIGIT_3 -> append("3")
        WhfinAmountKey.DIGIT_4 -> append("4")
        WhfinAmountKey.DIGIT_5 -> append("5")
        WhfinAmountKey.DIGIT_6 -> append("6")
        WhfinAmountKey.DIGIT_7 -> append("7")
        WhfinAmountKey.DIGIT_8 -> append("8")
        WhfinAmountKey.DIGIT_9 -> append("9")
        WhfinAmountKey.DECIMAL -> decimal()
        WhfinAmountKey.ADD -> withOperator(AmountOperator.ADD)
        WhfinAmountKey.SUBTRACT -> withOperator(AmountOperator.SUBTRACT)
        WhfinAmountKey.MULTIPLY -> withOperator(AmountOperator.MULTIPLY)
        WhfinAmountKey.DIVIDE -> withOperator(AmountOperator.DIVIDE)
        WhfinAmountKey.PERCENT -> percent()
        WhfinAmountKey.EQUALS -> equals()
        WhfinAmountKey.BACKSPACE -> backspace()
    }

    fun resolvedText(): String = equals().display

    private fun append(digits: String): AmountCalculator {
        val base = if (error) copy(error = false) else this
        if (base.input.count(Char::isDigit) >= MAX_DIGITS) return base
        val available = MAX_DIGITS - base.input.count(Char::isDigit)
        val accepted = digits.take(available)
        val next = when {
            base.input.isEmpty() && accepted.all { it == '0' } -> "0"
            base.input == "0" && !base.input.contains('.') -> accepted.trimStart('0').ifEmpty { "0" }
            else -> base.input + accepted
        }
        return base.copy(input = next)
    }

    private fun decimal(): AmountCalculator {
        val base = if (error) copy(error = false) else this
        return if ('.' in base.input) base else base.copy(input = base.input.ifEmpty { "0" } + ".")
    }

    private fun withOperator(next: AmountOperator): AmountCalculator {
        if (error) return AmountCalculator()
        val operand = input.toBigDecimalOrNull()
        if (operand == null) return if (accumulator != null) copy(operator = next) else this
        if (accumulator == null || operator == null) {
            return copy(accumulator = operand, operator = next, input = "")
        }
        val result = calculate(accumulator, operator, operand) ?: return copy(error = true, input = "")
        return AmountCalculator(accumulator = result, operator = next)
    }

    private fun percent(): AmountCalculator {
        if (error) return AmountCalculator()
        val operand = input.toBigDecimalOrNull() ?: accumulator ?: return this
        val percentValue = if (accumulator != null && operator in setOf(AmountOperator.ADD, AmountOperator.SUBTRACT)) {
            accumulator.multiply(operand).divide(HUNDRED, RESULT_SCALE, RoundingMode.HALF_UP)
        } else {
            operand.divide(HUNDRED, RESULT_SCALE, RoundingMode.HALF_UP)
        }
        return if (operator != null && accumulator != null) {
            copy(input = percentValue.toInputText())
        } else {
            AmountCalculator(input = percentValue.toInputText())
        }
    }

    private fun equals(): AmountCalculator {
        if (error || accumulator == null || operator == null) return this
        val operand = input.toBigDecimalOrNull() ?: return this
        val result = calculate(accumulator, operator, operand) ?: return copy(error = true, input = "")
        return AmountCalculator(input = result.toInputText())
    }

    private fun backspace(): AmountCalculator = when {
        error -> AmountCalculator()
        input.isNotEmpty() -> copy(input = input.dropLast(1))
        accumulator != null && operator != null -> AmountCalculator(input = accumulator.toInputText())
        else -> this
    }

    private fun calculate(left: BigDecimal, operation: AmountOperator, right: BigDecimal): BigDecimal? =
        when (operation) {
            AmountOperator.ADD -> left.add(right)
            AmountOperator.SUBTRACT -> left.subtract(right)
            AmountOperator.MULTIPLY -> left.multiply(right)
            AmountOperator.DIVIDE -> if (right.compareTo(BigDecimal.ZERO) == 0) null
                else left.divide(right, RESULT_SCALE, RoundingMode.HALF_UP)
        }?.setScale(RESULT_SCALE, RoundingMode.HALF_UP)?.stripTrailingZeros()

    private fun BigDecimal.toInputText(): String = toPlainString()

    private companion object {
        const val MAX_DIGITS = 12
        const val RESULT_SCALE = 2
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}
