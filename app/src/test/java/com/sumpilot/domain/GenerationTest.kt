package com.sumpilot.domain

import com.sumpilot.domain.generation.Explanations
import com.sumpilot.domain.generation.QuestionGenerator
import com.sumpilot.domain.generation.QuestionPools
import com.sumpilot.domain.generation.Strategy
import com.sumpilot.domain.generation.expressionKey
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationTest {

    private fun assertValid(a: Int, op: Operation, b: Int, d: Difficulty) {
        when (op) {
            Operation.ADD -> assertTrue(a in 1..d.addSubMax && b in 1..d.addSubMax, "$a + $b out of range for $d")
            Operation.SUBTRACT -> {
                assertTrue(a in 1..d.addSubMax && b in 1..d.addSubMax, "$a - $b out of range for $d")
                assertTrue(a - b >= 0, "negative subtraction $a - $b")
            }
            Operation.MULTIPLY -> assertTrue(a in 1..d.factorMax && b in 1..d.factorMax, "$a × $b out of range for $d")
            Operation.DIVIDE -> {
                assertTrue(b in 1..d.divisorMax, "divisor $b out of range for $d")
                assertTrue(a in 1..d.dividendMax, "dividend $a out of range for $d")
                assertEquals(0, a % b, "remainder in $a ÷ $b")
            }
        }
    }

    @Test fun poolsAreValidForEveryTopicAndDifficulty() {
        for (d in Difficulty.entries) for (op in Operation.entries) {
            val pool = QuestionPools.pool(op, d)
            assertTrue(pool.isNotEmpty())
            pool.forEach { (a, b) -> assertValid(a, op, b, d) }
        }
    }

    @Test fun divisorLimits() {
        assertEquals(10, QuestionPools.pool(Operation.DIVIDE, Difficulty.EASY).maxOf { it.second })
        assertEquals(10, QuestionPools.pool(Operation.DIVIDE, Difficulty.MEDIUM).maxOf { it.second })
        assertEquals(12, QuestionPools.pool(Operation.DIVIDE, Difficulty.HARD).maxOf { it.second })
    }

    @Test fun generatedQuestionsAreValidWithDistinctOptions() {
        for (seed in 0 until 40) {
            val gen = QuestionGenerator(Random(seed))
            for (d in Difficulty.entries) for (op in Operation.entries) {
                val qs = gen.generateFixedSet(10, d, setOf(op))
                qs.forEach { q ->
                    assertValid(q.first, q.operation, q.second, d)
                    assertEquals(q.operation.apply(q.first, q.second), q.correctAnswer)
                    assertEquals(4, q.options.size)
                    assertEquals(4, q.options.toSet().size, "options not distinct: ${q.options}")
                    assertEquals(1, q.options.count { it == q.correctAnswer })
                    val min = if (op == Operation.ADD || op == Operation.SUBTRACT) 0 else 1
                    assertTrue(q.options.all { it >= min }, "invalid option in ${q.options} for $op")
                }
            }
        }
    }

    @Test fun mixedTenQuestionSetsAreBalancedAndUnique() {
        for (seed in 0 until 30) {
            val gen = QuestionGenerator(Random(seed))
            val qs = gen.generateFixedSet(10, Difficulty.EASY, Operation.entries.toSet())
            assertEquals(10, qs.size)
            val counts = qs.groupingBy { it.operation }.eachCount()
            assertEquals(4, counts.size)
            assertTrue(counts.values.all { it == 2 || it == 3 }, "unbalanced: $counts")
            assertEquals(10, qs.map { it.key }.toSet().size, "duplicate questions")
        }
    }

    @Test fun customSelectionDistribution() {
        val gen = QuestionGenerator(Random(7))
        val qs = gen.generateFixedSet(10, Difficulty.MEDIUM, setOf(Operation.ADD, Operation.DIVIDE, Operation.MULTIPLY))
        val counts = qs.groupingBy { it.operation }.eachCount()
        assertEquals(setOf(Operation.ADD, Operation.DIVIDE, Operation.MULTIPLY), counts.keys)
        assertTrue(counts.values.all { it in 3..4 })
    }

    @Test fun reversedOperandsAreDuplicates() {
        assertEquals(expressionKey(3, Operation.ADD, 5), expressionKey(5, Operation.ADD, 3))
        assertEquals(expressionKey(4, Operation.MULTIPLY, 6), expressionKey(6, Operation.MULTIPLY, 4))
        assertFalse(expressionKey(8, Operation.SUBTRACT, 3) == expressionKey(3, Operation.SUBTRACT, 8))
    }

    @Test fun tenEasyMultiplicationQuestionsHaveNoReversedDuplicates() {
        for (seed in 0 until 30) {
            val qs = QuestionGenerator(Random(seed)).generateFixedSet(10, Difficulty.EASY, setOf(Operation.MULTIPLY))
            assertEquals(10, qs.map { it.key }.toSet().size)
        }
    }

    @Test fun timedSessionsUseShuffledCyclesAndAvoidRecentRepeats() {
        val gen = QuestionGenerator(Random(3))
        val ops = mutableListOf<Operation>()
        val keys = mutableListOf<String>()
        repeat(80) {
            val q = gen.generateNextTimed(Difficulty.HARD, Operation.entries.toSet(), ops, keys)
            assertFalse(q.key in keys.takeLast(20), "repeat within 20")
            ops += q.operation
            keys += q.key
        }
        ops.chunked(4).forEach { chunk -> assertEquals(4, chunk.toSet().size, "cycle not a permutation: $chunk") }
    }

    @Test fun timedGenerationFallsBackWhenPoolIsExhausted() {
        // Easy multiplication has only 15 distinct normalised questions, fewer than the 20-question window.
        val gen = QuestionGenerator(Random(11))
        val ops = mutableListOf<Operation>()
        val keys = mutableListOf<String>()
        repeat(60) {
            val q = gen.generateNextTimed(Difficulty.EASY, setOf(Operation.MULTIPLY), ops, keys)
            if (keys.isNotEmpty()) assertFalse(q.key == keys.last(), "immediate repeat")
            ops += q.operation
            keys += q.key
        }
        assertEquals(60, keys.size)
    }

    @Test fun deterministicWithSeed() {
        val a = QuestionGenerator(Random(42)).generateFixedSet(10, Difficulty.HARD, Operation.entries.toSet())
        val b = QuestionGenerator(Random(42)).generateFixedSet(10, Difficulty.HARD, Operation.entries.toSet())
        assertEquals(a, b)
    }

    @Test fun explanationsAreCorrectForEveryPoolQuestion() {
        for (d in Difficulty.entries) for (op in Operation.entries) {
            for ((a, b) in QuestionPools.pool(op, d)) {
                val answer = op.apply(a, b)
                val text = Explanations.explanation(a, op, b)
                assertTrue(text.contains(answer.toString()), "explanation for $a $op $b lacks answer: $text")
                val hint = Explanations.hint(a, op, b)
                assertTrue(hint.isNotBlank())
                when (val s = Explanations.strategy(a, op, b)) {
                    is Strategy.MakeTen -> {
                        assertEquals(s.other, s.toTen + s.rest)
                        assertEquals(s.ten, s.base + s.toTen)
                        assertEquals(answer, s.ten + s.rest)
                        assertTrue(s.toTen > 0 && s.rest > 0)
                    }
                    is Strategy.AddTensThenOnes -> assertEquals(answer, s.afterTens + s.ones)
                    is Strategy.SubtractToTen -> {
                        assertEquals(b, s.first + s.rest)
                        assertEquals(s.ten, s.from - s.first)
                        assertEquals(answer, s.ten - s.rest)
                        assertEquals(0, s.ten % 10)
                    }
                    is Strategy.SubtractTensThenOnes -> assertEquals(answer, s.afterTens - s.ones)
                    is Strategy.Count -> assertEquals(answer, if (s.up) s.from + s.by else s.from - s.by)
                    is Strategy.Groups -> assertEquals(answer, s.groups * s.size)
                    is Strategy.SplitFactor -> assertEquals(answer, 10 * s.other + s.extra * s.other)
                    is Strategy.RelatedFact -> assertEquals(s.dividend, s.divisor * s.quotient)
                    is Strategy.Plain -> Unit
                }
            }
        }
    }

    @Test fun hintsNeverStateTheAnswerForSpecExamples() {
        assertEquals(
            "Break 7 into 2 and 5. Add 2 to 8 to make 10, then add 5. 8 + 7 = 15.",
            Explanations.explanation(8, Operation.ADD, 7),
        )
        assertEquals(
            "Take away 5 first to reach 10, then take away the remaining 2. 15 − 7 = 8.",
            Explanations.explanation(15, Operation.SUBTRACT, 7),
        )
        assertEquals("Think of equal groups. 4 groups of 6 make 24.", Explanations.explanation(4, Operation.MULTIPLY, 6))
        assertEquals("Ask yourself: 6 times which number makes 24?", Explanations.hint(24, Operation.DIVIDE, 6))
    }
}
