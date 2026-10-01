package com.sumpilot.domain.model

/** The four practice operations. [symbol] is shown on screen, [spokenName] is used for screen readers. */
enum class Operation(val key: String, val symbol: String, val spokenName: String, val label: String) {
    ADD("add", "+", "plus", "Addition"),
    SUBTRACT("sub", "−", "minus", "Subtraction"),
    MULTIPLY("mul", "×", "times", "Multiplication"),
    DIVIDE("div", "÷", "divided by", "Division");

    fun apply(a: Int, b: Int): Int = when (this) {
        ADD -> a + b
        SUBTRACT -> a - b
        MULTIPLY -> a * b
        DIVIDE -> a / b
    }

    companion object {
        fun fromKey(key: String): Operation? = entries.firstOrNull { it.key == key }

        /** Serialises a set of operations in a stable order, e.g. "add,sub,mul,div". */
        fun encode(ops: Set<Operation>): String = entries.filter { it in ops }.joinToString(",") { it.key }

        fun decode(value: String?): Set<Operation> =
            value.orEmpty().split(',').mapNotNull { fromKey(it.trim()) }.toSet()
    }
}

/** Difficulty ranges from the specification. All ranges are inclusive and start at 1. */
enum class Difficulty(
    val key: String,
    val label: String,
    val addSubMax: Int,
    val factorMax: Int,
    val dividendMax: Int,
    val divisorMax: Int,
) {
    EASY("easy", "Easy", addSubMax = 10, factorMax = 5, dividendMax = 20, divisorMax = 10),
    MEDIUM("medium", "Medium", addSubMax = 50, factorMax = 10, dividendMax = 100, divisorMax = 10),
    HARD("hard", "Hard", addSubMax = 100, factorMax = 12, dividendMax = 144, divisorMax = 12);

    companion object {
        fun fromKey(key: String?): Difficulty = entries.firstOrNull { it.key == key } ?: EASY
    }
}

enum class SessionType(val key: String, val label: String) {
    TEN_QUESTIONS("ten", "10-question mission"),
    FIVE_MINUTES("five_min", "5-minute mission");

    companion object {
        const val TEN_QUESTION_COUNT = 10
        const val FIVE_MINUTE_MILLIS = 5L * 60L * 1000L

        fun fromKey(key: String?): SessionType = entries.firstOrNull { it.key == key } ?: TEN_QUESTIONS
    }
}

enum class SessionStatus(val key: String, val label: String) {
    IN_PROGRESS("in_progress", "In progress"),
    COMPLETED("completed", "Completed"),
    ENDED_EARLY("ended_early", "Ended early");

    companion object {
        fun fromKey(key: String?): SessionStatus = entries.firstOrNull { it.key == key } ?: IN_PROGRESS
    }
}

data class MissionConfig(
    val type: SessionType = SessionType.TEN_QUESTIONS,
    val difficulty: Difficulty = Difficulty.EASY,
    val operations: Set<Operation> = Operation.entries.toSet(),
) {
    init {
        require(operations.isNotEmpty()) { "At least one operation must be enabled" }
    }
}

/** Abstraction over time so timer and history rules are deterministic in tests. */
interface Clock {
    /** Monotonic time, e.g. SystemClock.elapsedRealtime(). Used only for active timing. */
    fun elapsedRealtime(): Long

    /** Wall-clock time in epoch millis, used only for display dates. */
    fun now(): Long
}
