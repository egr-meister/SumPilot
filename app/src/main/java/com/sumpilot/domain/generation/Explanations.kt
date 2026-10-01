package com.sumpilot.domain.generation

import com.sumpilot.domain.model.Operation

/**
 * Locally generated hints (which never reveal the answer) and explanations (which do),
 * adapted to the actual operands. Each strategy is also exposed as data so tests can
 * check that every step is arithmetically correct.
 */
sealed interface Strategy {
    /** a + b where a + b crosses ten: split b into [toTen] and [rest]. */
    data class MakeTen(val base: Int, val other: Int, val toTen: Int, val rest: Int, val ten: Int) : Strategy

    /** a + b with b ≥ 10: add tens, then ones. */
    data class AddTensThenOnes(val base: Int, val tens: Int, val ones: Int, val afterTens: Int) : Strategy

    /** a − b crossing a ten: take away [first] to reach [ten], then [rest]. */
    data class SubtractToTen(val from: Int, val first: Int, val rest: Int, val ten: Int) : Strategy

    /** a − b with b ≥ 10: take away tens, then ones. */
    data class SubtractTensThenOnes(val from: Int, val tens: Int, val ones: Int, val afterTens: Int) : Strategy

    /** Small add or subtract by counting on/back. */
    data class Count(val from: Int, val by: Int, val up: Boolean) : Strategy

    /** a × b as equal groups. */
    data class Groups(val groups: Int, val size: Int) : Strategy

    /** a × b with a factor above 10: (10 × other) + (extra × other). */
    data class SplitFactor(val big: Int, val other: Int, val extra: Int) : Strategy

    /** a ÷ b via the related multiplication fact. */
    data class RelatedFact(val dividend: Int, val divisor: Int, val quotient: Int) : Strategy

    /** Fallback: plain statement. */
    data class Plain(val first: Int, val op: Operation, val second: Int) : Strategy
}

object Explanations {

    fun strategy(a: Int, op: Operation, b: Int): Strategy = when (op) {
        Operation.ADD -> addStrategy(a, b)
        Operation.SUBTRACT -> subStrategy(a, b)
        Operation.MULTIPLY -> mulStrategy(a, b)
        Operation.DIVIDE -> if (b != 0 && a % b == 0) Strategy.RelatedFact(a, b, a / b) else Strategy.Plain(a, op, b)
    }

    private fun addStrategy(a: Int, b: Int): Strategy {
        val base = maxOf(a, b)
        val other = minOf(a, b)
        val sum = a + b
        if (other in 1..9 && base % 10 != 0) {
            val ten = (base / 10 + 1) * 10
            val toTen = ten - base
            if (sum > ten && toTen in 1 until other) {
                return Strategy.MakeTen(base, other, toTen, other - toTen, ten)
            }
            if (other <= 3) return Strategy.Count(base, other, up = true)
        }
        if (other >= 10) {
            val tens = (other / 10) * 10
            val ones = other - tens
            if (ones > 0) return Strategy.AddTensThenOnes(base, tens, ones, base + tens)
        }
        if (other in 1..3) return Strategy.Count(base, other, up = true)
        return Strategy.Plain(a, Operation.ADD, b)
    }

    private fun subStrategy(a: Int, b: Int): Strategy {
        if (b in 1..9 && a > 10) {
            val first = a % 10
            if (first in 1 until b) {
                return Strategy.SubtractToTen(a, first, b - first, a - first)
            }
        }
        if (b >= 10) {
            val tens = (b / 10) * 10
            val ones = b - tens
            if (ones > 0 && a - tens >= ones) return Strategy.SubtractTensThenOnes(a, tens, ones, a - tens)
        }
        if (b in 1..3 && a - b >= 0) return Strategy.Count(a, b, up = false)
        return Strategy.Plain(a, Operation.SUBTRACT, b)
    }

    private fun mulStrategy(a: Int, b: Int): Strategy {
        val big = maxOf(a, b)
        val other = minOf(a, b)
        if (big > 10 && other > 1) return Strategy.SplitFactor(big, other, big - 10)
        return Strategy.Groups(groups = a, size = b)
    }

    fun hint(a: Int, op: Operation, b: Int): String = when (val s = strategy(a, op, b)) {
        is Strategy.MakeTen -> "Try making ${s.ten} first. What do you add to ${s.base} to make ${s.ten}?"
        is Strategy.AddTensThenOnes -> "Add the tens first: ${s.base} + ${s.tens}. Then add ${s.ones} more."
        is Strategy.SubtractToTen -> "Take away ${s.first} first to reach ${s.ten}. Then take away the rest."
        is Strategy.SubtractTensThenOnes -> "Take away ${s.tens} first, then take away ${s.ones} more."
        is Strategy.Count -> if (s.up) "Start at ${s.from} and count on ${s.by}." else "Start at ${s.from} and count back ${s.by}."
        is Strategy.Groups -> when {
            s.groups == 1 || s.size == 1 -> "When you multiply by 1, the number stays the same."
            else -> "Think of ${s.groups} equal groups of ${s.size}. Try counting by ${s.size}s."
        }
        is Strategy.SplitFactor -> "Split ${s.big} into 10 and ${s.extra}. Work out 10 × ${s.other} and ${s.extra} × ${s.other}, then add them."
        is Strategy.RelatedFact -> "Ask yourself: ${s.divisor} times which number makes ${s.dividend}?"
        is Strategy.Plain -> plainHint(s)
    }

    fun explanation(a: Int, op: Operation, b: Int): String {
        val answer = op.apply(a, b)
        val expr = formatExpression(a, op, b)
        return when (val s = strategy(a, op, b)) {
            is Strategy.MakeTen ->
                "Break ${s.other} into ${s.toTen} and ${s.rest}. Add ${s.toTen} to ${s.base} to make ${s.ten}, then add ${s.rest}. $expr = $answer."
            is Strategy.AddTensThenOnes ->
                "Add ${s.tens} to ${s.base} to get ${s.afterTens}, then add ${s.ones}. $expr = $answer."
            is Strategy.SubtractToTen ->
                "Take away ${s.first} first to reach ${s.ten}, then take away the remaining ${s.rest}. $expr = $answer."
            is Strategy.SubtractTensThenOnes ->
                "Take away ${s.tens} to get ${s.afterTens}, then take away ${s.ones}. $expr = $answer."
            is Strategy.Count -> {
                val steps = (1..s.by).map { if (s.up) s.from + it else s.from - it }
                val verb = if (s.up) "Count on" else "Count back"
                "$verb ${s.by} from ${s.from}: ${steps.joinToString(", ")}. $expr = $answer."
            }
            is Strategy.Groups -> when {
                s.groups == 1 || s.size == 1 -> "Multiplying by 1 keeps the number the same. $expr = $answer."
                else -> "Think of equal groups. ${s.groups} groups of ${s.size} make $answer."
            }
            is Strategy.SplitFactor -> {
                val tenPart = 10 * s.other
                val extraPart = s.extra * s.other
                "Split ${s.big} into 10 and ${s.extra}. 10 × ${s.other} = $tenPart and ${s.extra} × ${s.other} = $extraPart. $tenPart + $extraPart = $answer."
            }
            is Strategy.RelatedFact ->
                "Ask yourself: ${s.divisor} times which number makes ${s.dividend}? ${s.divisor} × ${s.quotient} = ${s.dividend}, so $expr = ${s.quotient}."
            is Strategy.Plain -> "$expr = $answer."
        }
    }

    private fun plainHint(s: Strategy.Plain): String = when (s.op) {
        Operation.ADD -> "Start with the bigger number and add the smaller one."
        Operation.SUBTRACT -> if (s.first == s.second) "What is left when you take away everything?" else "Think: what number plus ${s.second} makes ${s.first}?"
        Operation.MULTIPLY -> "Think of ${s.first} equal groups of ${s.second}."
        Operation.DIVIDE -> "Ask yourself: ${s.second} times which number makes ${s.first}?"
    }
}
