package com.sumpilot.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

enum class CalcOperator(val key: String, val symbol: String, val spokenName: String) {
    ADD("add", "+", "plus"),
    SUBTRACT("sub", "−", "minus"),
    MULTIPLY("mul", "×", "times"),
    DIVIDE("div", "÷", "divided by");

    companion object {
        fun fromKey(key: String?): CalcOperator? = entries.firstOrNull { it.key == key }
    }
}

sealed interface CalcResult {
    data class Success(val value: BigDecimal, val rounded: Boolean) : CalcResult
    data object DivisionByZero : CalcResult
    data object OutOfRange : CalcResult
}

/** Pure BigDecimal arithmetic with SumPilot limits. */
object CalcArithmetic {
    val MAX_ABS: BigDecimal = BigDecimal("1000000")
    const val MAX_FRACTION_DIGITS = 6

    fun compute(a: BigDecimal, op: CalcOperator, b: BigDecimal): CalcResult {
        if (a.abs() > MAX_ABS || b.abs() > MAX_ABS) return CalcResult.OutOfRange
        val (result, rounded) = when (op) {
            CalcOperator.ADD -> a.add(b) to false
            CalcOperator.SUBTRACT -> a.subtract(b) to false
            CalcOperator.MULTIPLY -> {
                val exact = a.multiply(b)
                val r = exact.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                r to (r.compareTo(exact) != 0)
            }
            CalcOperator.DIVIDE -> {
                if (b.signum() == 0) return CalcResult.DivisionByZero
                val r = a.divide(b, MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                // Exact when the 6-digit quotient multiplies back to the dividend.
                r to (r.multiply(b).compareTo(a) != 0)
            }
        }
        val normalized = normalize(result)
        if (normalized.abs() > MAX_ABS) return CalcResult.OutOfRange
        return CalcResult.Success(normalized, rounded)
    }

    /** Removes trailing zeros and avoids scientific notation / negative zero. */
    fun normalize(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    fun format(value: BigDecimal): String = normalize(value).toPlainString()
}

/**
 * Calculator state machine. One binary operation at a time.
 * Operands are edited as strings so the display matches what the child typed.
 */
data class CalculatorState(
    val first: String = "",
    val operator: CalcOperator? = null,
    val second: String = "",
    /** Last computed result shown on the display (canonical string). */
    val result: String? = null,
    val resultRounded: Boolean = false,
    /** Expression that produced [result], e.g. "7 ÷ 3". */
    val resultExpression: String? = null,
    val error: String? = null,
) {
    val isShowingResult: Boolean get() = result != null && operator == null && second.isEmpty()

    val expressionText: String
        get() = buildString {
            append(displayOperand(first))
            if (operator != null) {
                append(' ').append(operator.symbol)
                if (second.isNotEmpty()) append(' ').append(displayOperand(second))
            }
        }

    val spokenExpression: String
        get() = buildString {
            append(spokenOperand(first))
            if (operator != null) {
                append(' ').append(operator.spokenName)
                if (second.isNotEmpty()) append(' ').append(spokenOperand(second))
            }
        }

    companion object {
        fun displayOperand(raw: String): String = raw.replace("-", "−")
        fun spokenOperand(raw: String): String = raw.replace("-", "minus ").replace(".", " point ")
    }
}

/** A successful calculation to be stored in history. */
data class CompletedCalculation(
    val first: String,
    val operator: CalcOperator,
    val second: String,
    val result: String,
    val rounded: Boolean,
)

class CalculatorEngine {

    data class Outcome(val state: CalculatorState, val completed: CompletedCalculation? = null)

    fun digit(state: CalculatorState, d: Char): CalculatorState {
        require(d in '0'..'9')
        val base = if (state.error != null || state.isShowingResult) CalculatorState() else state
        return base.editActive { appendDigit(it, d) }
    }

    fun decimal(state: CalculatorState): CalculatorState {
        val base = if (state.error != null || state.isShowingResult) CalculatorState() else state
        return base.editActive { cur ->
            when {
                cur.contains('.') -> cur
                cur.isEmpty() -> "0."
                cur == "-" -> "-0."
                else -> "$cur."
            }
        }
    }

    fun toggleSign(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        if (state.isShowingResult) {
            val value = state.result ?: return state
            return CalculatorState(first = negate(value))
        }
        return state.editActive { cur -> negate(cur) }
    }

    fun operator(state: CalculatorState, op: CalcOperator): CalculatorState {
        if (state.error != null) return state
        if (state.isShowingResult) {
            // An operator continues from the result.
            return CalculatorState(first = state.result!!, operator = op)
        }
        if (state.operator != null && state.second.isEmpty()) {
            // Operator selected before the second operand replaces the previous operator.
            return state.copy(operator = op)
        }
        if (state.operator != null) return state // one binary operation at a time; press = first
        val first = parse(state.first) ?: return state
        return state.copy(first = CalcArithmetic.format(first), operator = op, result = null, resultExpression = null)
    }

    fun equals(state: CalculatorState): Outcome {
        val op = state.operator ?: return Outcome(state) // repeated Equals does nothing
        val a = parse(state.first) ?: return Outcome(state)
        val b = parse(state.second) ?: return Outcome(state)
        val expression = "${CalculatorState.displayOperand(CalcArithmetic.format(a))} ${op.symbol} " +
            CalculatorState.displayOperand(CalcArithmetic.format(b))
        return when (val r = CalcArithmetic.compute(a, op, b)) {
            is CalcResult.Success -> {
                val text = CalcArithmetic.format(r.value)
                Outcome(
                    CalculatorState(result = text, resultRounded = r.rounded, resultExpression = expression),
                    CompletedCalculation(CalcArithmetic.format(a), op, CalcArithmetic.format(b), text, r.rounded),
                )
            }
            CalcResult.DivisionByZero -> Outcome(state.copy(error = "You can't divide by zero. Try another number."))
            CalcResult.OutOfRange -> Outcome(state.copy(error = "That number is too big for SumPilot. Try smaller numbers."))
        }
    }

    fun clear(): CalculatorState = CalculatorState()

    fun backspace(state: CalculatorState): CalculatorState {
        if (state.error != null) return state.copy(error = null)
        if (state.isShowingResult) return state
        return when {
            state.second.isNotEmpty() -> state.copy(second = state.second.dropLast(1).let { if (it == "-") "" else it })
            state.operator != null -> state.copy(operator = null)
            else -> state.copy(first = state.first.dropLast(1).let { if (it == "-") "" else it })
        }
    }

    /** Starts a new calculation from a history result ("Use result"). */
    fun useResult(value: String): CalculatorState {
        val parsed = parse(value) ?: return CalculatorState()
        return CalculatorState(first = CalcArithmetic.format(parsed))
    }

    private inline fun CalculatorState.editActive(edit: (String) -> String): CalculatorState {
        val cleared = copy(error = null, result = null, resultExpression = null, resultRounded = false)
        return if (operator == null) cleared.copy(first = edit(first)) else cleared.copy(second = edit(second))
    }

    companion object {
        /** Parses an operand; returns null for incomplete input such as "" or "-". */
        fun parse(raw: String): BigDecimal? {
            if (raw.isEmpty() || raw == "-") return null
            return raw.removeSuffix(".").toBigDecimalOrNull()
        }

        fun appendDigit(cur: String, d: Char): String {
            val negative = cur.startsWith("-")
            val body = cur.removePrefix("-")
            val newBody = when {
                body == "0" -> d.toString() // normalise leading zeros
                else -> body + d
            }
            val intPart = newBody.substringBefore('.')
            val fracPart = if (newBody.contains('.')) newBody.substringAfter('.') else ""
            if (fracPart.length > CalcArithmetic.MAX_FRACTION_DIGITS) return cur
            val value = newBody.removeSuffix(".").toBigDecimalOrNull() ?: return cur
            if (value > CalcArithmetic.MAX_ABS || intPart.length > 7) return cur
            return if (negative) "-$newBody" else newBody
        }

        fun negate(cur: String): String = when {
            cur.isEmpty() -> "-"
            cur.startsWith("-") -> cur.removePrefix("-")
            else -> "-$cur"
        }
    }
}
