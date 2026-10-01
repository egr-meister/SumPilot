package com.sumpilot.domain

import com.sumpilot.domain.generation.QuestionGenerator
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.MissionConfig
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionStatus
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.progress.BadgeRules
import com.sumpilot.domain.sessions.EndResult
import com.sumpilot.domain.sessions.PracticeService
import com.sumpilot.domain.sessions.ProgressTotals
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.Scoring
import com.sumpilot.domain.sessions.StartResult
import com.sumpilot.domain.sessions.TimingSnapshot
import com.sumpilot.testing.FakeClock
import com.sumpilot.testing.InMemoryPracticeStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeServiceTest {
    private val store = InMemoryPracticeStore()
    private val clock = FakeClock()
    private val service = PracticeService(store, QuestionGenerator(Random(5)), clock)
    private val untimed = TimingSnapshot(null, 0)

    private fun wrong(q: QuestionRecord) = q.options.first { it != q.correctAnswer }

    private suspend fun startTen(config: MissionConfig = MissionConfig()): Long =
        (service.startSession(config) as StartResult.Started).sessionId

    @Test fun untimedSessionCompletesAfterTenthAnswer() = runBlocking {
        val id = startTen()
        assertEquals(10, store.questionsFor(id).size)
        repeat(10) { i ->
            val q = assertNotNull(service.currentQuestion(id))
            clock.advance(1_000)
            val out = assertNotNull(service.submitAnswer(id, q.id, if (i % 3 == 0) wrong(q) else q.correctAnswer, untimed))
            assertEquals(i == 9, out.sessionCompleted)
        }
        val s = assertNotNull(store.getSession(id))
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertNotNull(s.finishedAt)
        val summary = Scoring.summarize(s, store.questionsFor(id))
        assertEquals(10, summary.answered)
        assertEquals(6, summary.correct)
        assertEquals(60, summary.accuracyPercent)
        assertEquals(1, store.totalsValue.completedTenQuestionSessions)
        assertTrue(Badge.MISSION_COMPLETE.key in store.badges)
        assertTrue(Badge.FIRST_FLIGHT.key in store.badges)
    }

    @Test fun duplicateAnswersAreIgnored() = runBlocking {
        val id = startTen()
        val q = assertNotNull(service.currentQuestion(id))
        assertNotNull(service.submitAnswer(id, q.id, wrong(q), untimed))
        assertNull(service.submitAnswer(id, q.id, q.correctAnswer, untimed))
        assertNull(service.submitAnswer(id, q.id, wrong(q), untimed))
        assertEquals(1, store.totalsValue.originalAnswered)
        assertEquals(0, store.totalsValue.originalCorrect)
        assertEquals(false, store.getQuestion(q.id)!!.isCorrect)
        assertEquals(1, store.mistakes.size)
    }

    @Test fun onlyOneUnfinishedSessionAtATime() = runBlocking {
        val id = startTen()
        val second = service.startSession(MissionConfig(type = SessionType.FIVE_MINUTES))
        assertEquals(StartResult.UnfinishedExists(id), second)
    }

    @Test fun earlyEndPreservesAnsweredAndExcludesUnanswered() = runBlocking {
        val id = startTen()
        val q1 = service.currentQuestion(id)!!
        service.submitAnswer(id, q1.id, q1.correctAnswer, untimed)
        val q2 = service.currentQuestion(id)!!
        service.submitAnswer(id, q2.id, wrong(q2), untimed)
        val result = service.endEarly(id, untimed)
        assertIs<EndResult.Finished>(result)
        val s = store.getSession(id)!!
        assertEquals(SessionStatus.ENDED_EARLY, s.status)
        val summary = Scoring.summarize(s, store.questionsFor(id))
        assertEquals(2, summary.answered)
        assertEquals(50, summary.accuracyPercent)
        assertEquals(0, store.totalsValue.completedSessions)
        assertEquals(1, store.unresolvedMistakes(id).size)
    }

    @Test fun sessionWithNoAnswersIsDiscarded() = runBlocking {
        val id = startTen()
        assertEquals(EndResult.Discarded, service.endEarly(id, untimed))
        assertNull(store.getSession(id))
        assertTrue(store.questions.isEmpty())
    }

    @Test fun timedSessionGeneratesOnDemandAndCompletesWithoutPenalty() = runBlocking {
        val id = (service.startSession(MissionConfig(type = SessionType.FIVE_MINUTES)) as StartResult.Started).sessionId
        assertEquals(1, store.questionsFor(id).size, "first question persisted before display")
        repeat(5) {
            val q = service.nextTimedQuestion(id)
            assertEquals(q, service.nextTimedQuestion(id), "pending question is not regenerated")
            service.submitAnswer(id, q.id, q.correctAnswer, TimingSnapshot(200_000, 100_000))
        }
        // Timer expired with a question pending: the child skips it.
        val pending = service.nextTimedQuestion(id)
        val end = service.completeTimedSession(id, TimingSnapshot(0, SessionType.FIVE_MINUTE_MILLIS))
        assertIs<EndResult.Finished>(end)
        val s = store.getSession(id)!!
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertNull(store.getQuestion(pending.id), "skipped question dropped, not counted")
        val summary = Scoring.summarize(s, store.questionsFor(id))
        assertEquals(5, summary.answered)
        assertEquals(100, summary.accuracyPercent)
        assertEquals(0, store.mistakes.size)
        assertEquals(1, store.totalsValue.completedSessions)
        assertEquals(0, store.totalsValue.completedTenQuestionSessions)
    }

    @Test fun answerRecordedAtExpiryCountsOnce() = runBlocking {
        val id = (service.startSession(MissionConfig(type = SessionType.FIVE_MINUTES)) as StartResult.Started).sessionId
        val q = service.nextTimedQuestion(id)
        assertNotNull(service.submitAnswer(id, q.id, q.correctAnswer, TimingSnapshot(0, SessionType.FIVE_MINUTE_MILLIS)))
        service.completeTimedSession(id, TimingSnapshot(0, SessionType.FIVE_MINUTE_MILLIS))
        assertNull(service.submitAnswer(id, q.id, q.correctAnswer, TimingSnapshot(0, 0)))
        assertEquals(1, store.totalsValue.originalAnswered)
        assertEquals(1, Scoring.summarize(store.getSession(id)!!, store.questionsFor(id)).answered)
    }

    @Test fun timingIsPersistedAtTransitions() = runBlocking {
        val id = (service.startSession(MissionConfig(type = SessionType.FIVE_MINUTES)) as StartResult.Started).sessionId
        service.saveTiming(id, TimingSnapshot(123_000, 177_000))
        assertEquals(123_000, store.getSession(id)!!.remainingMillis)
        assertEquals(177_000, store.getSession(id)!!.activeAnsweringMillis)
    }

    @Test fun retriesDoNotRewriteOriginalResults() = runBlocking {
        val id = startTen()
        val q = service.currentQuestion(id)!!
        service.submitAnswer(id, q.id, wrong(q), untimed)
        service.endEarly(id, untimed)
        val mistake = store.unresolvedMistakes(null).single()
        val before = Scoring.summarize(store.getSession(id)!!, store.questionsFor(id))
        val totalsBefore = store.totalsValue

        val bad = service.retryMistake(mistake.id, mistake.correctAnswer + 1)!!
        assertFalse(bad.isCorrect)
        assertEquals(1, store.unresolvedMistakes(null).size)

        val good = service.retryMistake(mistake.id, mistake.correctAnswer)!!
        assertTrue(good.isCorrect)
        assertTrue(store.unresolvedMistakes(null).isEmpty())
        assertEquals(before, Scoring.summarize(store.getSession(id)!!, store.questionsFor(id)))
        assertEquals(totalsBefore.originalAnswered, store.totalsValue.originalAnswered)
        assertEquals(totalsBefore.originalCorrect, store.totalsValue.originalCorrect)
        assertEquals(1, store.totalsValue.resolvedMistakes)
        assertTrue(Badge.CAREFUL_RETURN.key in store.badges)
        assertTrue(good.newBadges.contains(Badge.CAREFUL_RETURN))
        // Resolving again does not double count.
        service.retryMistake(mistake.id, mistake.correctAnswer)
        assertEquals(1, store.totalsValue.resolvedMistakes)
    }

    @Test fun mistakesDeduplicateAndReopen() = runBlocking {
        // Same expression answered wrongly in two sessions → one entry.
        val id1 = startTen(MissionConfig(operations = setOf(Operation.ADD)))
        val q = service.currentQuestion(id1)!!
        service.submitAnswer(id1, q.id, wrong(q), untimed)
        service.endEarly(id1, untimed)
        val mistake = store.mistakes.values.single()
        service.retryMistake(mistake.id, mistake.correctAnswer)
        assertNotNull(store.getMistake(mistake.id)!!.resolvedAt)

        // Craft a second session containing the reversed expression.
        val id2 = startTen(MissionConfig(operations = setOf(Operation.ADD)))
        val target = service.currentQuestion(id2)!!
        val reversed = target.copy(first = q.second, second = q.first, correctAnswer = q.correctAnswer,
            options = listOf(q.correctAnswer, q.correctAnswer + 1, q.correctAnswer + 2, q.correctAnswer + 3))
        store.questions[target.id] = reversed
        service.submitAnswer(id2, target.id, q.correctAnswer + 1, untimed)

        assertEquals(1, store.mistakes.size, "deduplicated by normalised expression")
        assertNull(store.getMistake(mistake.id)!!.resolvedAt, "reopened after resolution")
        assertEquals(1, store.unresolvedMistakes(id2).size)
        assertEquals(1, store.unresolvedMistakes(id1).size)
    }

    @Test fun historyKeepsLatestHundredSessionsButNotMistakes() = runBlocking {
        val first = startTen()
        val q = service.currentQuestion(first)!!
        service.submitAnswer(first, q.id, wrong(q), untimed)
        service.endEarly(first, untimed)
        repeat(100) {
            clock.advance(60_000)
            val id = startTen()
            val qq = service.currentQuestion(id)!!
            service.submitAnswer(id, qq.id, qq.correctAnswer, untimed)
            service.endEarly(id, untimed)
        }
        assertEquals(100, store.finishedSessionIdsNewestFirst().size)
        assertNull(store.getSession(first), "oldest session removed")
        assertTrue(store.questionsFor(first).isEmpty())
        assertEquals(1, store.unresolvedMistakes(null).size, "mistakes survive retention")
        assertEquals(101, store.totalsValue.originalAnswered, "lifetime totals survive retention")
    }

    @Test fun clearHistoryKeepsMistakes() = runBlocking {
        val id = startTen()
        val q = service.currentQuestion(id)!!
        service.submitAnswer(id, q.id, wrong(q), untimed)
        service.endEarly(id, untimed)
        service.clearSessionHistory()
        assertTrue(store.sessions.isEmpty())
        assertEquals(1, store.unresolvedMistakes(null).size)
        service.clearMistakes()
        assertTrue(store.unresolvedMistakes(null).isEmpty())
    }

    @Test fun hintUseIsRecordedButNotPenalised() = runBlocking {
        val id = startTen()
        val q = service.currentQuestion(id)!!
        service.markHintUsed(q.id)
        val out = service.submitAnswer(id, q.id, q.correctAnswer, untimed)!!
        assertTrue(out.isCorrect)
        assertTrue(store.getQuestion(q.id)!!.hintUsed)
        assertEquals(1, store.totalsValue.originalCorrect)
    }

    @Test fun badgeRules() {
        assertTrue(BadgeRules.earned(ProgressTotals()).isEmpty())
        val t = ProgressTotals(
            originalAnswered = 50, completedSessions = 5, completedTenQuestionSessions = 1, resolvedMistakes = 1,
            perOperationAnswered = Operation.entries.associateWith { 1 },
        )
        assertEquals(Badge.entries.toSet(), BadgeRules.earned(t))
        assertFalse(Badge.FOUR_DIRECTIONS in BadgeRules.earned(t.copy(perOperationAnswered = mapOf(Operation.ADD to 49))))
        assertEquals(listOf(Badge.FIRST_FLIGHT), BadgeRules.newlyEarned(ProgressTotals(originalAnswered = 1), emptySet()))
        assertTrue(BadgeRules.newlyEarned(ProgressTotals(originalAnswered = 1), setOf(Badge.FIRST_FLIGHT.key)).isEmpty())
    }

    @Test fun badgesUnlockOnceWithDate() = runBlocking {
        val id = startTen(MissionConfig(difficulty = Difficulty.HARD))
        val q = service.currentQuestion(id)!!
        clock.wall = 1_800_000_000_000L
        service.submitAnswer(id, q.id, q.correctAnswer, untimed)
        assertEquals(1_800_000_000_000L, store.badges[Badge.FIRST_FLIGHT.key])
        clock.wall += 10_000
        val q2 = service.currentQuestion(id)!!
        val out = service.submitAnswer(id, q2.id, q2.correctAnswer, untimed)!!
        assertFalse(Badge.FIRST_FLIGHT in out.newBadges)
        assertEquals(1_800_000_000_000L, store.badges[Badge.FIRST_FLIGHT.key])
    }

    @Test fun accuracyWithZeroAnswersIsNotFabricated() {
        assertNull(Scoring.accuracyPercent(0, 0))
        assertEquals(67, Scoring.accuracyPercent(2, 3))
    }
}
