package com.sumpilot.domain.generation

import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import kotlin.random.Random

/** A generated practice question with its persisted answer options. */
data class GeneratedQuestion(
    val first: Int,
    val operation: Operation,
    val second: Int,
    val correctAnswer: Int,
    val options: List<Int>,
) {
    val key: String get() = expressionKey(first, operation, second)
}

/**
 * Normalised expression key. Addition and multiplication are commutative,
 * so "3 + 5" and "5 + 3" share the key "add:3:5".
 */
fun expressionKey(first: Int, operation: Operation, second: Int): String {
    val (a, b) = when (operation) {
        Operation.ADD, Operation.MULTIPLY -> minOf(first, second) to maxOf(first, second)
        else -> first to second
    }
    return "${operation.key}:$a:$b"
}

fun formatExpression(first: Int, operation: Operation, second: Int): String =
    "$first ${operation.symbol} $second"

fun spokenExpression(first: Int, operation: Operation, second: Int): String =
    "$first ${operation.spokenName} $second"

/** Enumerates every valid operand pair for an operation and difficulty. */
object QuestionPools {
    private val cache = HashMap<Pair<Operation, Difficulty>, List<Pair<Int, Int>>>()

    fun pool(operation: Operation, difficulty: Difficulty): List<Pair<Int, Int>> =
        synchronized(cache) { cache.getOrPut(operation to difficulty) { build(operation, difficulty) } }

    private fun build(operation: Operation, d: Difficulty): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        when (operation) {
            Operation.ADD -> for (a in 1..d.addSubMax) for (b in 1..d.addSubMax) out += a to b
            // Subtraction results are never negative.
            Operation.SUBTRACT -> for (a in 1..d.addSubMax) for (b in 1..a) out += a to b
            Operation.MULTIPLY -> for (a in 1..d.factorMax) for (b in 1..d.factorMax) out += a to b
            // Positive divisor (never zero), whole-number quotient, dividend within range.
            Operation.DIVIDE -> for (divisor in 1..d.divisorMax) {
                var q = 1
                while (divisor * q <= d.dividendMax) {
                    out += (divisor * q) to divisor
                    q++
                }
            }
        }
        return out
    }
}

/**
 * Generates mentally solvable questions. The random source is injected so tests are deterministic.
 */
class QuestionGenerator(private val random: Random) {

    private val distractors = DistractorGenerator(random)

    /** Builds all questions for a ten-question session: balanced operations, shuffled, no duplicates. */
    fun generateFixedSet(count: Int, difficulty: Difficulty, operations: Set<Operation>): List<GeneratedQuestion> {
        require(operations.isNotEmpty())
        val plan = balancedOperationPlan(count, operations)
        val used = HashSet<String>()
        return plan.map { op ->
            val q = generateOne(op, difficulty, used)
            used += q.key
            q
        }
    }

    /**
     * Distributes [count] questions across the enabled operations as evenly as possible.
     * Remainders go to randomly chosen, distinct operations. The order is shuffled.
     */
    fun balancedOperationPlan(count: Int, operations: Set<Operation>): List<Operation> {
        val ops = Operation.entries.filter { it in operations }
        val base = count / ops.size
        val extra = count % ops.size
        val bonus = ops.shuffled(random).take(extra).toSet()
        val plan = ops.flatMap { op -> List(base + if (op in bonus) 1 else 0) { op } }
        return plan.shuffled(random)
    }

    /**
     * Next question for a timed session. [previousOperations] is the full list of operations
     * already generated in the session (in order); [recentKeys] are the keys of the most recent
     * questions (up to 20) which should not repeat unless the pool is exhausted.
     */
    fun generateNextTimed(
        difficulty: Difficulty,
        operations: Set<Operation>,
        previousOperations: List<Operation>,
        recentKeys: List<String>,
    ): GeneratedQuestion {
        val op = nextCycleOperation(operations, previousOperations)
        return generateOne(op, difficulty, recentKeys.takeLast(RECENT_WINDOW).toHashSet(), recentKeys)
    }

    /**
     * Operations are drawn in shuffled cycles: each block of N questions (N = enabled operations)
     * contains every operation once, which prevents long runs of a single topic.
     */
    fun nextCycleOperation(operations: Set<Operation>, previousOperations: List<Operation>): Operation {
        val ops = Operation.entries.filter { it in operations }
        val n = ops.size
        val inCycle = previousOperations.size % n
        val currentCycle = previousOperations.takeLast(inCycle).toSet()
        val remaining = ops.filter { it !in currentCycle }.ifEmpty { ops }
        return remaining[random.nextInt(remaining.size)]
    }

    /**
     * Picks from the valid pool, excluding [excluded] keys. If everything is excluded, falls back to
     * excluding only the most recent keys, then to the whole pool, so generation always succeeds.
     */
    fun generateOne(
        operation: Operation,
        difficulty: Difficulty,
        excluded: Set<String>,
        recentOrder: List<String> = emptyList(),
    ): GeneratedQuestion {
        val pool = QuestionPools.pool(operation, difficulty)
        var candidates = pool.filter { (a, b) -> expressionKey(a, operation, b) !in excluded }
        if (candidates.isEmpty() && recentOrder.isNotEmpty()) {
            // Pool exhausted: allow reuse, but still avoid the very latest question when possible.
            val last = recentOrder.last()
            candidates = pool.filter { (a, b) -> expressionKey(a, operation, b) != last }
        }
        if (candidates.isEmpty()) candidates = pool
        val (a, b) = candidates[random.nextInt(candidates.size)]
        val answer = operation.apply(a, b)
        return GeneratedQuestion(a, operation, b, answer, distractors.options(a, operation, b, answer))
    }

    /** New option order and distractors for an existing expression (used for mistake retries). */
    fun optionsFor(first: Int, operation: Operation, second: Int): List<Int> =
        distractors.options(first, operation, second, operation.apply(first, second))

    companion object {
        const val RECENT_WINDOW = 20
    }
}

/** Builds four distinct, plausible integer options with exactly one correct answer. */
class DistractorGenerator(private val random: Random) {

    fun options(a: Int, op: Operation, b: Int, answer: Int): List<Int> {
        val minValue = when (op) {
            Operation.ADD, Operation.SUBTRACT -> 0
            Operation.MULTIPLY, Operation.DIVIDE -> 1
        }
        val primary: List<Int> = when (op) {
            Operation.ADD -> listOf(answer + 1, answer - 1, answer + 10, answer - 10, answer + 2, answer - 2, kotlin.math.abs(a - b))
            Operation.SUBTRACT -> listOf(answer + 1, answer - 1, answer + 10, answer - 10, answer + 2, a + b, answer - 2)
            Operation.MULTIPLY -> listOf(answer + a, answer - a, answer + b, answer - b, a + b, answer + 1, answer - 1)
            Operation.DIVIDE -> listOf(answer + 1, answer - 1, answer + 2, answer - 2, b, answer * 2, a - b)
        }
        val chosen = LinkedHashSet<Int>()
        val valid = primary.filter { it >= minValue && it != answer }.distinct().shuffled(random)
        for (v in valid) {
            if (chosen.size == 3) break
            chosen += v
        }
        // Bounded fallback: nearby values above the answer always exist and are valid.
        var step = 1
        while (chosen.size < 3 && step <= 50) {
            val up = answer + step
            if (up != answer) chosen += up
            if (chosen.size < 3) {
                val down = answer - step
                if (down >= minValue) chosen += down
            }
            step++
        }
        return (chosen.take(3) + answer).shuffled(random)
    }
}
