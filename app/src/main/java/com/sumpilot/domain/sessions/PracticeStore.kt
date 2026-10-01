package com.sumpilot.domain.sessions

/**
 * Storage primitives used by [PracticeService]. Implemented by Room in production and by an
 * in-memory fake in unit tests. All methods are main-safe suspend functions.
 */
interface PracticeStore {
    /** Runs [block] atomically. Nested calls join the outer transaction. */
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun insertSession(session: SessionRecord): Long
    suspend fun updateSession(session: SessionRecord)
    suspend fun getSession(id: Long): SessionRecord?
    suspend fun getUnfinishedSession(): SessionRecord?
    suspend fun deleteSession(id: Long)

    /** Finished sessions, newest first. */
    suspend fun finishedSessionIdsNewestFirst(): List<Long>
    suspend fun deleteFinishedSessions()

    suspend fun insertQuestion(question: QuestionRecord): Long
    suspend fun getQuestion(id: Long): QuestionRecord?
    suspend fun questionsFor(sessionId: Long): List<QuestionRecord>

    /**
     * Records an answer only if the question has none yet. Returns the number of rows changed
     * (0 or 1), which is how duplicate submissions are rejected at the storage level.
     */
    suspend fun recordAnswerIfUnanswered(questionId: Long, selected: Int, isCorrect: Boolean, answeredAt: Long): Int
    suspend fun markHintUsed(questionId: Long)
    suspend fun deleteUnansweredQuestions(sessionId: Long)

    suspend fun findMistakeByKey(key: String): MistakeRecord?
    suspend fun getMistake(id: Long): MistakeRecord?
    suspend fun insertMistake(mistake: MistakeRecord): Long
    suspend fun updateMistake(mistake: MistakeRecord)
    suspend fun addMistakeSource(mistakeId: Long, sessionId: Long, questionId: Long)
    suspend fun unresolvedMistakes(sessionId: Long?): List<MistakeRecord>
    suspend fun deleteAllMistakes()

    suspend fun totals(): ProgressTotals
    suspend fun saveTotals(totals: ProgressTotals)

    suspend fun unlockedBadgeKeys(): Set<String>
    suspend fun unlockBadge(key: String, at: Long)
    suspend fun deleteAllBadges()
}
