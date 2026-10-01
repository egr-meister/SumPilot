package com.sumpilot.domain.sessions

import com.sumpilot.domain.generation.expressionKey
import com.sumpilot.domain.generation.formatExpression
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.MissionConfig
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionStatus
import com.sumpilot.domain.model.SessionType

data class SessionRecord(
    val id: Long = 0,
    val type: SessionType,
    val difficulty: Difficulty,
    val operations: Set<Operation>,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    /** Remaining active time for timed sessions; null for untimed sessions. */
    val remainingMillis: Long? = null,
    val activeAnsweringMillis: Long = 0L,
) {
    val config: MissionConfig get() = MissionConfig(type, difficulty, operations)
    val isFinished: Boolean get() = status != SessionStatus.IN_PROGRESS
    val limitMillis: Long? get() = if (type == SessionType.FIVE_MINUTES) SessionType.FIVE_MINUTE_MILLIS else null
}

data class QuestionRecord(
    val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val first: Int,
    val operation: Operation,
    val second: Int,
    val correctAnswer: Int,
    val options: List<Int>,
    val selectedAnswer: Int? = null,
    val answeredAt: Long? = null,
    val isCorrect: Boolean? = null,
    val hintUsed: Boolean = false,
) {
    val isAnswered: Boolean get() = selectedAnswer != null
    val key: String get() = expressionKey(first, operation, second)
    val expression: String get() = formatExpression(first, operation, second)
}

data class MistakeRecord(
    val id: Long = 0,
    val key: String,
    val first: Int,
    val operation: Operation,
    val second: Int,
    val correctAnswer: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val resolvedAt: Long? = null,
) {
    val isResolved: Boolean get() = resolvedAt != null
    val expression: String get() = formatExpression(first, operation, second)
}

data class ProgressTotals(
    val originalAnswered: Int = 0,
    val originalCorrect: Int = 0,
    val completedSessions: Int = 0,
    val completedTenQuestionSessions: Int = 0,
    val resolvedMistakes: Int = 0,
    val perOperationAnswered: Map<Operation, Int> = emptyMap(),
) {
    fun answeredFor(op: Operation): Int = perOperationAnswered[op] ?: 0
}

/** Summary used by results, history and the panel. */
data class SessionSummary(
    val session: SessionRecord,
    val answered: Int,
    val correct: Int,
) {
    val incorrect: Int get() = answered - correct

    /** correct / answered × 100, or null when nothing was answered (never fabricated). */
    val accuracyPercent: Int? get() = Scoring.accuracyPercent(correct, answered)
}

object Scoring {
    fun accuracyPercent(correct: Int, answered: Int): Int? {
        if (answered <= 0) return null
        return Math.round(correct * 100.0 / answered).toInt()
    }

    fun summarize(session: SessionRecord, questions: List<QuestionRecord>): SessionSummary {
        val answered = questions.filter { it.isAnswered }
        return SessionSummary(session, answered.size, answered.count { it.isCorrect == true })
    }
}
