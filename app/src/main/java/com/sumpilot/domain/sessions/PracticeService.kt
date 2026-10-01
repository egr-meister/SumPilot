package com.sumpilot.domain.sessions

import com.sumpilot.domain.generation.QuestionGenerator
import com.sumpilot.domain.generation.expressionKey
import com.sumpilot.domain.model.Clock
import com.sumpilot.domain.model.MissionConfig
import com.sumpilot.domain.model.SessionStatus
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.progress.BadgeRules

/** Timing checkpoint persisted with every transition. */
data class TimingSnapshot(val remainingMillis: Long?, val activeMillis: Long)

data class AnswerOutcome(
    val question: QuestionRecord,
    val isCorrect: Boolean,
    val sessionCompleted: Boolean,
    val newBadges: List<Badge>,
)

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult
    data class UnfinishedExists(val sessionId: Long) : StartResult
}

sealed interface EndResult {
    /** No questions were answered, so the session was removed without adding history. */
    data object Discarded : EndResult
    data class Finished(val sessionId: Long) : EndResult
}

data class RetryOutcome(val isCorrect: Boolean, val mistake: MistakeRecord, val newBadges: List<Badge>)

/**
 * All practice rules: session lifecycle, scoring, mistakes, retention and badges.
 * Independent of Android so it can be unit tested with a fake store and fake clock.
 */
class PracticeService(
    private val store: PracticeStore,
    private val generator: QuestionGenerator,
    private val clock: Clock,
) {

    suspend fun startSession(config: MissionConfig): StartResult = store.transaction {
        store.getUnfinishedSession()?.let { return@transaction StartResult.UnfinishedExists(it.id) }
        val now = clock.now()
        val session = SessionRecord(
            type = config.type,
            difficulty = config.difficulty,
            operations = config.operations,
            startedAt = now,
            remainingMillis = if (config.type == SessionType.FIVE_MINUTES) SessionType.FIVE_MINUTE_MILLIS else null,
        )
        val id = store.insertSession(session)
        when (config.type) {
            SessionType.TEN_QUESTIONS -> {
                generator.generateFixedSet(SessionType.TEN_QUESTION_COUNT, config.difficulty, config.operations)
                    .forEachIndexed { index, q ->
                        store.insertQuestion(
                            QuestionRecord(
                                sessionId = id, position = index + 1, first = q.first, operation = q.operation,
                                second = q.second, correctAnswer = q.correctAnswer, options = q.options,
                            ),
                        )
                    }
            }
            // The first timed question is generated and persisted before it is displayed.
            SessionType.FIVE_MINUTES -> generateTimedQuestion(id, session)
        }
        StartResult.Started(id)
    }

    /** The first unanswered question of a session, or null when all are answered. */
    suspend fun currentQuestion(sessionId: Long): QuestionRecord? =
        store.questionsFor(sessionId).sortedBy { it.position }.firstOrNull { !it.isAnswered }

    /**
     * For timed sessions: returns the pending question, or generates and persists the next one.
     * Never called after the timer has expired.
     */
    suspend fun nextTimedQuestion(sessionId: Long): QuestionRecord = store.transaction {
        currentQuestion(sessionId) ?: run {
            val session = requireNotNull(store.getSession(sessionId))
            generateTimedQuestion(sessionId, session)
        }
    }

    private suspend fun generateTimedQuestion(sessionId: Long, session: SessionRecord): QuestionRecord {
        val existing = store.questionsFor(sessionId).sortedBy { it.position }
        val q = generator.generateNextTimed(
            difficulty = session.difficulty,
            operations = session.operations,
            previousOperations = existing.map { it.operation },
            recentKeys = existing.takeLast(QuestionGenerator.RECENT_WINDOW).map { it.key },
        )
        val record = QuestionRecord(
            sessionId = sessionId, position = (existing.maxOfOrNull { it.position } ?: 0) + 1,
            first = q.first, operation = q.operation, second = q.second,
            correctAnswer = q.correctAnswer, options = q.options,
        )
        val id = store.insertQuestion(record)
        return record.copy(id = id)
    }

    /**
     * Records exactly one answer for a question. Returns null if the question was already
     * answered or the session is no longer in progress (duplicate taps are ignored).
     */
    suspend fun submitAnswer(sessionId: Long, questionId: Long, selected: Int, timing: TimingSnapshot): AnswerOutcome? =
        store.transaction {
            val session = store.getSession(sessionId) ?: return@transaction null
            if (session.isFinished) return@transaction null
            val question = store.getQuestion(questionId) ?: return@transaction null
            if (question.sessionId != sessionId || question.isAnswered) return@transaction null
            val correct = selected == question.correctAnswer
            val now = clock.now()
            if (store.recordAnswerIfUnanswered(questionId, selected, correct, now) != 1) return@transaction null

            var totals = store.totals()
            totals = totals.copy(
                originalAnswered = totals.originalAnswered + 1,
                originalCorrect = totals.originalCorrect + if (correct) 1 else 0,
                perOperationAnswered = totals.perOperationAnswered +
                    (question.operation to totals.answeredFor(question.operation) + 1),
            )

            if (!correct) recordMistake(question, sessionId, now)

            var updatedSession = session.copy(
                remainingMillis = timing.remainingMillis,
                activeAnsweringMillis = timing.activeMillis,
            )
            var completed = false
            if (session.type == SessionType.TEN_QUESTIONS) {
                val answered = store.questionsFor(sessionId).count { it.isAnswered }
                if (answered >= SessionType.TEN_QUESTION_COUNT) {
                    completed = true
                    updatedSession = updatedSession.copy(status = SessionStatus.COMPLETED, finishedAt = now)
                    totals = totals.copy(
                        completedSessions = totals.completedSessions + 1,
                        completedTenQuestionSessions = totals.completedTenQuestionSessions + 1,
                    )
                }
            }
            store.updateSession(updatedSession)
            store.saveTotals(totals)
            if (completed) applyRetention()
            val badges = unlockBadges(totals, now)
            AnswerOutcome(
                question = question.copy(selectedAnswer = selected, isCorrect = correct, answeredAt = now),
                isCorrect = correct,
                sessionCompleted = completed,
                newBadges = badges,
            )
        }

    private suspend fun recordMistake(question: QuestionRecord, sessionId: Long, now: Long) {
        val key = expressionKey(question.first, question.operation, question.second)
        val existing = store.findMistakeByKey(key)
        val mistakeId = if (existing == null) {
            store.insertMistake(
                MistakeRecord(
                    key = key, first = question.first, operation = question.operation, second = question.second,
                    correctAnswer = question.correctAnswer, firstSeenAt = now, lastSeenAt = now,
                ),
            )
        } else {
            // Deduplicated by normalised expression; reopened if it had been resolved.
            store.updateMistake(existing.copy(lastSeenAt = now, resolvedAt = null))
            existing.id
        }
        store.addMistakeSource(mistakeId, sessionId, question.id)
    }

    suspend fun saveTiming(sessionId: Long, timing: TimingSnapshot) = store.transaction {
        val session = store.getSession(sessionId) ?: return@transaction
        if (session.isFinished) return@transaction
        store.updateSession(session.copy(remainingMillis = timing.remainingMillis, activeAnsweringMillis = timing.activeMillis))
    }

    suspend fun markHintUsed(questionId: Long) {
        val q = store.getQuestion(questionId) ?: return
        if (!q.isAnswered && !q.hintUsed) store.markHintUsed(questionId)
    }

    /**
     * Completes a five-minute session after the timer expired and the current question was
     * answered or skipped. Unanswered questions are dropped and never counted as errors.
     */
    suspend fun completeTimedSession(sessionId: Long, timing: TimingSnapshot): EndResult =
        finish(sessionId, timing, SessionStatus.COMPLETED)

    /** "End mission": keeps answered questions and marks the session ended early. */
    suspend fun endEarly(sessionId: Long, timing: TimingSnapshot): EndResult =
        finish(sessionId, timing, SessionStatus.ENDED_EARLY)

    private suspend fun finish(sessionId: Long, timing: TimingSnapshot, status: SessionStatus): EndResult =
        store.transaction {
            val session = store.getSession(sessionId) ?: return@transaction EndResult.Discarded
            if (session.isFinished) return@transaction EndResult.Finished(sessionId)
            val answered = store.questionsFor(sessionId).count { it.isAnswered }
            if (answered == 0) {
                store.deleteSession(sessionId)
                return@transaction EndResult.Discarded
            }
            val now = clock.now()
            store.deleteUnansweredQuestions(sessionId)
            store.updateSession(
                session.copy(
                    status = status,
                    finishedAt = now,
                    remainingMillis = timing.remainingMillis,
                    activeAnsweringMillis = timing.activeMillis,
                ),
            )
            if (status == SessionStatus.COMPLETED) {
                val t = store.totals()
                val updated = t.copy(completedSessions = t.completedSessions + 1)
                store.saveTotals(updated)
                unlockBadges(updated, now)
            }
            applyRetention()
            EndResult.Finished(sessionId)
        }

    /** Retry a mistake. Never changes original session results, totals of answers, or accuracy. */
    suspend fun retryMistake(mistakeId: Long, selected: Int): RetryOutcome? = store.transaction {
        val mistake = store.getMistake(mistakeId) ?: return@transaction null
        val now = clock.now()
        val correct = selected == mistake.correctAnswer
        if (!correct) {
            val updated = mistake.copy(lastSeenAt = now)
            store.updateMistake(updated)
            return@transaction RetryOutcome(false, updated, emptyList())
        }
        if (mistake.isResolved) return@transaction RetryOutcome(true, mistake, emptyList())
        val resolved = mistake.copy(resolvedAt = now)
        store.updateMistake(resolved)
        val t = store.totals()
        val updatedTotals = t.copy(resolvedMistakes = t.resolvedMistakes + 1)
        store.saveTotals(updatedTotals)
        RetryOutcome(true, resolved, unlockBadges(updatedTotals, now))
    }

    private suspend fun unlockBadges(totals: ProgressTotals, now: Long): List<Badge> {
        val newBadges = BadgeRules.newlyEarned(totals, store.unlockedBadgeKeys())
        newBadges.forEach { store.unlockBadge(it.key, now) }
        return newBadges
    }

    /** Keeps the latest [HISTORY_LIMIT] answered sessions. Mistakes and totals are unaffected. */
    private suspend fun applyRetention() {
        store.finishedSessionIdsNewestFirst().drop(HISTORY_LIMIT).forEach { store.deleteSession(it) }
    }

    suspend fun clearSessionHistory() = store.transaction { store.deleteFinishedSessions() }

    suspend fun clearMistakes() = store.transaction { store.deleteAllMistakes() }

    suspend fun clearProgressAndBadges() = store.transaction {
        store.saveTotals(ProgressTotals())
        store.deleteAllBadges()
    }

    companion object {
        const val HISTORY_LIMIT = 100
    }
}
