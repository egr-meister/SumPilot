package com.sumpilot.domain

import com.sumpilot.domain.calculator.CalcArithmetic
import com.sumpilot.domain.calculator.CalcOperator
import com.sumpilot.domain.calculator.CalcResult
import com.sumpilot.domain.calculator.CalculatorEngine
import com.sumpilot.domain.calculator.CalculatorState
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculatorTest {
    private val engine = CalculatorEngine()

    private fun type(keys: String, start: CalculatorState = CalculatorState()): CalculatorEngine.Outcome {
        var state = start
        var completed: CalculatorEngine.Outcome? = null
        for (k in keys) {
            state = when (k) {
                in '0'..'9' -> engine.digit(state, k)
                '.' -> engine.decimal(state)
                '+' -> engine.operator(state, CalcOperator.ADD)
                '-' -> engine.operator(state, CalcOperator.SUBTRACT)
                '*' -> engine.operator(state, CalcOperator.MULTIPLY)
                '/' -> engine.operator(state, CalcOperator.DIVIDE)
                '~' -> engine.toggleSign(state)
                '<' -> engine.backspace(state)
                'C' -> engine.clear()
                '=' -> engine.equals(state).also { completed = it }.state
                else -> error("bad key $k")
            }
        }
        return CalculatorEngine.Outcome(state, completed?.completed)
    }

    private fun bd(s: String) = BigDecimal(s)

    @Test fun basicOperations() {
        assertEquals("5", type("2+3=").state.result)
        assertEquals("-1", type("2-3=").state.result)
        assertEquals("7.5", type("2.5*3=").state.result)
        assertEquals("4", type("8/2=").state.result)
    }

    @Test fun decimalAdditionIsExact() {
        assertEquals("0.3", type("0.1+0.2=").state.result)
    }

    @Test fun divisionRoundsHalfUpToSixDigits() {
        val r = CalcArithmetic.compute(bd("2"), CalcOperator.DIVIDE, bd("3"))
        assertIs<CalcResult.Success>(r)
        assertEquals("0.666667", CalcArithmetic.format(r.value))
        assertTrue(r.rounded)

        val exact = CalcArithmetic.compute(bd("1"), CalcOperator.DIVIDE, bd("8"))
        assertIs<CalcResult.Success>(exact)
        assertEquals("0.125", CalcArithmetic.format(exact.value))
        assertFalse(exact.rounded)
    }

    @Test fun trailingZerosRemoved() {
        assertEquals("3", type("1.5*2=").state.result)
        assertEquals("100", CalcArithmetic.format(bd("100.000")))
    }

    @Test fun divisionByZeroShowsFriendlyErrorWithoutHistory() {
        val out = type("5/0=")
        assertNotNull(out.state.error)
        assertNull(out.completed)
    }

    @Test fun outOfRangeResultIsRejected() {
        val out = type("1000000+1=")
        assertNotNull(out.state.error)
        assertNull(out.completed)
        assertIs<CalcResult.OutOfRange>(CalcArithmetic.compute(bd("1000"), CalcOperator.MULTIPLY, bd("1001")))
        assertIs<CalcResult.Success>(CalcArithmetic.compute(bd("1000"), CalcOperator.MULTIPLY, bd("1000")))
    }

    @Test fun operandLimitsEnforcedWhileTyping() {
        assertEquals("1000000", type("10000000").state.first)
        assertEquals("1000000", type("10000001").state.first)
        assertEquals("100000", type("1000001").state.first)
        assertEquals("0.123456", type("0.1234567").state.first)
    }

    @Test fun multipleDecimalPointsPrevented() {
        assertEquals("1.25", type("1..2.5").state.first)
        assertEquals("0.5", type(".5").state.first)
    }

    @Test fun leadingZerosNormalised() {
        assertEquals("7", type("0007").state.first)
        assertEquals("0.07", type("00.07").state.first)
    }

    @Test fun operatorBeforeSecondOperandReplacesOperator() {
        val s = type("6+*").state
        assertEquals(CalcOperator.MULTIPLY, s.operator)
        assertEquals("12", type("6+*2=").state.result)
    }

    @Test fun repeatedEqualsDoesNotRepeat() {
        val once = type("2+3=")
        val twice = engine.equals(once.state)
        assertEquals("5", twice.state.result)
        assertNull(twice.completed)
    }

    @Test fun digitAfterResultStartsNewCalculation() {
        val s = type("2+3=4").state
        assertEquals("4", s.first)
        assertNull(s.operator)
        assertNull(s.result)
    }

    @Test fun operatorAfterResultContinues() {
        assertEquals("10", type("2+3=*2=").state.result)
    }

    @Test fun negativeOperandsAndResults() {
        assertEquals("-6", type("~3*2=").state.result)
        assertEquals("-6", type("3*2~=").state.result)
        assertEquals("6", type("~3*2~=").state.result)
        assertEquals("1", type("3+~2=").state.result)
    }

    @Test fun backspaceEditsActiveOperand() {
        assertEquals("12", type("123<").state.first)
        val s = type("12+34<").state
        assertEquals("3", s.second)
        val s2 = type("12+<").state
        assertNull(s2.operator)
        assertEquals("12", s2.first)
    }

    @Test fun clearResets() {
        assertEquals(CalculatorState(), type("12+3C").state)
    }

    @Test fun completedCalculationCarriesRoundedFlag() {
        val out = type("10/3=")
        val c = assertNotNull(out.completed)
        assertEquals("3.333333", c.result)
        assertTrue(c.rounded)
        assertTrue(out.state.resultRounded)
    }

    @Test fun useResultStartsFromHistoryValue() {
        val s = engine.useResult("-2.5")
        assertEquals("-2.5", s.first)
        assertEquals("-5", type("*2=", s).state.result)
    }
}
