package com.sumpilot.testing

import com.sumpilot.domain.model.Clock
import com.sumpilot.domain.sessions.MistakeRecord
import com.sumpilot.domain.sessions.PracticeStore
import com.sumpilot.domain.sessions.ProgressTotals
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.SessionRecord

class FakeClock(var elapsed: Long = 1_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    override fun elapsedRealtime(): Long = elapsed
    override fun now(): Long = wall
    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }
}

/** In-memory PracticeStore mirroring the Room implementation's semantics. */
class InMemoryPracticeStore : PracticeStore {
    val sessions = LinkedHashMap<Long, SessionRecord>()
    val questions = LinkedHashMap<Long, QuestionRecord>()
    val mistakes = LinkedHashMap<Long, MistakeRecord>()
    val mistakeSources = mutableListOf<Triple<Long, Long, Long>>()
    var totalsValue = ProgressTotals()
    val badges = LinkedHashMap<String, Long>()
    private var nextId = 1L

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun insertSession(session: SessionRecord): Long {
        val id = nextId++
        sessions[id] = session.copy(id = id)
        return id
    }
    override suspend fun updateSession(session: SessionRecord) { sessions[session.id] = session }
    override suspend fun getSession(id: Long) = sessions[id]
    override suspend fun getUnfinishedSession() = sessions.values.firstOrNull { !it.isFinished }
    override suspend fun deleteSession(id: Long) {
        sessions.remove(id)
        questions.values.removeAll { it.sessionId == id }
    }
    override suspend fun finishedSessionIdsNewestFirst(): List<Long> =
        sessions.values.filter { it.isFinished }
            .sortedWith(compareByDescending<SessionRecord> { it.finishedAt }.thenByDescending { it.id })
            .map { it.id }
    override suspend fun deleteFinishedSessions() {
        finishedSessionIdsNewestFirst().forEach { deleteSession(it) }
    }

    override suspend fun insertQuestion(question: QuestionRecord): Long {
        val id = nextId++
        questions[id] = question.copy(id = id)
        return id
    }
    override suspend fun getQuestion(id: Long) = questions[id]
    override suspend fun questionsFor(sessionId: Long) =
        questions.values.filter { it.sessionId == sessionId }.sortedBy { it.position }
    override suspend fun recordAnswerIfUnanswered(questionId: Long, selected: Int, isCorrect: Boolean, answeredAt: Long): Int {
        val q = questions[questionId] ?: return 0
        if (q.selectedAnswer != null) return 0
        questions[questionId] = q.copy(selectedAnswer = selected, isCorrect = isCorrect, answeredAt = answeredAt)
        return 1
    }
    override suspend fun markHintUsed(questionId: Long) {
        questions[questionId]?.let { questions[questionId] = it.copy(hintUsed = true) }
    }
    override suspend fun deleteUnansweredQuestions(sessionId: Long) {
        questions.values.removeAll { it.sessionId == sessionId && it.selectedAnswer == null }
    }

    override suspend fun findMistakeByKey(key: String) = mistakes.values.firstOrNull { it.key == key }
    override suspend fun getMistake(id: Long) = mistakes[id]
    override suspend fun insertMistake(mistake: MistakeRecord): Long {
        val id = nextId++
        mistakes[id] = mistake.copy(id = id)
        return id
    }
    override suspend fun updateMistake(mistake: MistakeRecord) { mistakes[mistake.id] = mistake }
    override suspend fun addMistakeSource(mistakeId: Long, sessionId: Long, questionId: Long) {
        mistakeSources += Triple(mistakeId, sessionId, questionId)
    }
    override suspend fun unresolvedMistakes(sessionId: Long?): List<MistakeRecord> =
        mistakes.values.filter { m ->
            m.resolvedAt == null && (sessionId == null || mistakeSources.any { it.first == m.id && it.second == sessionId })
        }
    override suspend fun deleteAllMistakes() {
        mistakes.clear()
        mistakeSources.clear()
    }

    override suspend fun totals() = totalsValue
    override suspend fun saveTotals(totals: ProgressTotals) { totalsValue = totals }
    override suspend fun unlockedBadgeKeys() = badges.keys.toSet()
    override suspend fun unlockBadge(key: String, at: Long) { badges.putIfAbsent(key, at) }
    override suspend fun deleteAllBadges() = badges.clear()
}
