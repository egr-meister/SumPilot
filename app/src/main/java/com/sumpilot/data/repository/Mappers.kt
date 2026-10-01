package com.sumpilot.data.repository

import com.sumpilot.data.local.MistakeEntity
import com.sumpilot.data.local.ProgressTotalsEntity
import com.sumpilot.data.local.QuestionEntity
import com.sumpilot.data.local.SessionEntity
import com.sumpilot.data.local.SessionSummaryRow
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionStatus
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.sessions.MistakeRecord
import com.sumpilot.domain.sessions.ProgressTotals
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.SessionRecord
import com.sumpilot.domain.sessions.SessionSummary

private fun op(key: String): Operation = Operation.fromKey(key) ?: Operation.ADD

fun SessionEntity.toRecord() = SessionRecord(
    id = id,
    type = SessionType.fromKey(type),
    difficulty = Difficulty.fromKey(difficulty),
    operations = Operation.decode(operations).ifEmpty { Operation.entries.toSet() },
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = SessionStatus.fromKey(status),
    remainingMillis = remainingMillis,
    activeAnsweringMillis = activeAnsweringMillis,
)

fun SessionRecord.toEntity() = SessionEntity(
    id = id,
    type = type.key,
    difficulty = difficulty.key,
    operations = Operation.encode(operations),
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status.key,
    remainingMillis = remainingMillis,
    activeAnsweringMillis = activeAnsweringMillis,
)

fun SessionSummaryRow.toSummary() = SessionSummary(
    session = SessionEntity(
        id, type, difficulty, operations, startedAt, finishedAt, status, remainingMillis, activeAnsweringMillis,
    ).toRecord(),
    answered = answered,
    correct = correct,
)

fun QuestionEntity.toRecord() = QuestionRecord(
    id = id,
    sessionId = sessionId,
    position = position,
    first = firstOperand,
    operation = op(operator),
    second = secondOperand,
    correctAnswer = correctAnswer,
    options = answerOptions.split(',').mapNotNull { it.trim().toIntOrNull() },
    selectedAnswer = selectedAnswer,
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    hintUsed = hintUsed,
)

fun QuestionRecord.toEntity() = QuestionEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    firstOperand = first,
    operator = operation.key,
    secondOperand = second,
    correctAnswer = correctAnswer,
    answerOptions = options.joinToString(","),
    selectedAnswer = selectedAnswer,
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    hintUsed = hintUsed,
)

fun MistakeEntity.toRecord() = MistakeRecord(
    id = id,
    key = normalizedExpressionKey,
    first = firstOperand,
    operation = op(operator),
    second = secondOperand,
    correctAnswer = correctAnswer,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    resolvedAt = resolvedAt,
)

fun MistakeRecord.toEntity() = MistakeEntity(
    id = id,
    normalizedExpressionKey = key,
    firstOperand = first,
    operator = operation.key,
    secondOperand = second,
    correctAnswer = correctAnswer,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    resolvedAt = resolvedAt,
)

fun ProgressTotalsEntity?.toTotals(): ProgressTotals {
    val e = this ?: return ProgressTotals()
    return ProgressTotals(
        originalAnswered = e.originalAnswered,
        originalCorrect = e.originalCorrect,
        completedSessions = e.completedSessions,
        completedTenQuestionSessions = e.completedTenQuestionSessions,
        resolvedMistakes = e.resolvedMistakes,
        perOperationAnswered = mapOf(
            Operation.ADD to e.additionAnswered,
            Operation.SUBTRACT to e.subtractionAnswered,
            Operation.MULTIPLY to e.multiplicationAnswered,
            Operation.DIVIDE to e.divisionAnswered,
        ),
    )
}

fun ProgressTotals.toEntity() = ProgressTotalsEntity(
    id = 1,
    originalAnswered = originalAnswered,
    originalCorrect = originalCorrect,
    completedSessions = completedSessions,
    completedTenQuestionSessions = completedTenQuestionSessions,
    resolvedMistakes = resolvedMistakes,
    additionAnswered = answeredFor(Operation.ADD),
    subtractionAnswered = answeredFor(Operation.SUBTRACT),
    multiplicationAnswered = answeredFor(Operation.MULTIPLY),
    divisionAnswered = answeredFor(Operation.DIVIDE),
)
