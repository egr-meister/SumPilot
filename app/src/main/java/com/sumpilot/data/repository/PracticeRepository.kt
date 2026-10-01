package com.sumpilot.data.repository

import androidx.room.withTransaction
import com.sumpilot.data.local.AppDatabase
import com.sumpilot.data.local.BadgeEntity
import com.sumpilot.data.local.MistakeSourceEntity
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.sessions.MistakeRecord
import com.sumpilot.domain.sessions.PracticeStore
import com.sumpilot.domain.sessions.ProgressTotals
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.SessionRecord
import com.sumpilot.domain.sessions.SessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [PracticeStore]. Room's suspend DAOs run off the main thread. */
class RoomPracticeStore(private val db: AppDatabase) : PracticeStore {
    private val sessions = db.sessionDao()
    private val questions = db.questionDao()
    private val mistakes = db.mistakeDao()
    private val progress = db.progressDao()

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun insertSession(session: SessionRecord): Long = sessions.insert(session.toEntity())
    override suspend fun updateSession(session: SessionRecord) = sessions.update(session.toEntity())
    override suspend fun getSession(id: Long): SessionRecord? = sessions.get(id)?.toRecord()
    override suspend fun getUnfinishedSession(): SessionRecord? = sessions.getUnfinished()?.toRecord()
    override suspend fun deleteSession(id: Long) = sessions.delete(id)
    override suspend fun finishedSessionIdsNewestFirst(): List<Long> = sessions.finishedIdsNewestFirst()
    override suspend fun deleteFinishedSessions() = sessions.deleteFinished()

    override suspend fun insertQuestion(question: QuestionRecord): Long = questions.insert(question.toEntity())
    override suspend fun getQuestion(id: Long): QuestionRecord? = questions.get(id)?.toRecord()
    override suspend fun questionsFor(sessionId: Long): List<QuestionRecord> = questions.forSession(sessionId).map { it.toRecord() }
    override suspend fun recordAnswerIfUnanswered(questionId: Long, selected: Int, isCorrect: Boolean, answeredAt: Long): Int =
        questions.recordAnswerIfUnanswered(questionId, selected, isCorrect, answeredAt)
    override suspend fun markHintUsed(questionId: Long) = questions.markHintUsed(questionId)
    override suspend fun deleteUnansweredQuestions(sessionId: Long) = questions.deleteUnanswered(sessionId)

    override suspend fun findMistakeByKey(key: String): MistakeRecord? = mistakes.findByKey(key)?.toRecord()
    override suspend fun getMistake(id: Long): MistakeRecord? = mistakes.get(id)?.toRecord()
    override suspend fun insertMistake(mistake: MistakeRecord): Long = mistakes.insert(mistake.toEntity())
    override suspend fun updateMistake(mistake: MistakeRecord) = mistakes.update(mistake.toEntity())
    override suspend fun addMistakeSource(mistakeId: Long, sessionId: Long, questionId: Long) =
        mistakes.insertSource(MistakeSourceEntity(mistakeId, sessionId, questionId))
    override suspend fun unresolvedMistakes(sessionId: Long?): List<MistakeRecord> =
        (if (sessionId == null) mistakes.unresolved() else mistakes.unresolvedForSession(sessionId)).map { it.toRecord() }
    override suspend fun deleteAllMistakes() = mistakes.deleteAll()

    override suspend fun totals(): ProgressTotals = progress.totals().toTotals()
    override suspend fun saveTotals(totals: ProgressTotals) = progress.saveTotals(totals.toEntity())

    override suspend fun unlockedBadgeKeys(): Set<String> = progress.badges().map { it.badgeKey }.toSet()
    override suspend fun unlockBadge(key: String, at: Long) = progress.unlockBadge(BadgeEntity(key, at))
    override suspend fun deleteAllBadges() = progress.deleteAllBadges()
}

data class UnlockedBadge(val badge: Badge, val unlockedAt: Long)

/** Observable read models for the UI. Writes go through PracticeService. */
class PracticeRepository(private val db: AppDatabase) {
    private val sessions = db.sessionDao()
    private val questions = db.questionDao()
    private val mistakes = db.mistakeDao()
    private val progress = db.progressDao()

    fun observeUnfinishedSession(): Flow<SessionRecord?> = sessions.observeUnfinished().map { it?.toRecord() }

    fun observeHistory(limit: Int = 100): Flow<List<SessionSummary>> =
        sessions.observeFinishedSummaries(limit).map { rows -> rows.map { it.toSummary() } }

    fun observeLatestFinished(): Flow<SessionSummary?> =
        sessions.observeFinishedSummaries(1).map { it.firstOrNull()?.toSummary() }

    fun observeSummary(sessionId: Long): Flow<SessionSummary?> = sessions.observeSummary(sessionId).map { it?.toSummary() }

    fun observeQuestions(sessionId: Long): Flow<List<QuestionRecord>> =
        questions.observeForSession(sessionId).map { list -> list.map { it.toRecord() } }

    fun observeUnresolvedMistakes(sessionId: Long?): Flow<List<MistakeRecord>> =
        (if (sessionId == null) mistakes.observeUnresolved() else mistakes.observeUnresolvedForSession(sessionId))
            .map { list -> list.map { it.toRecord() } }

    fun observeTotals(): Flow<ProgressTotals> = progress.observeTotals().map { it.toTotals() }

    fun observeBadges(): Flow<List<UnlockedBadge>> = progress.observeBadges().map { list ->
        list.mapNotNull { e -> Badge.fromKey(e.badgeKey)?.let { UnlockedBadge(it, e.unlockedAt) } }
    }

    suspend fun session(id: Long): SessionRecord? = sessions.get(id)?.toRecord()

    suspend fun unfinishedSession(): SessionRecord? = sessions.getUnfinished()?.toRecord()

    suspend fun answeredCount(sessionId: Long): Int = questions.answeredCount(sessionId)

    suspend fun questionsFor(sessionId: Long): List<QuestionRecord> = questions.forSession(sessionId).map { it.toRecord() }

    suspend fun unresolvedMistakes(sessionId: Long?): List<MistakeRecord> =
        (if (sessionId == null) mistakes.unresolved() else mistakes.unresolvedForSession(sessionId)).map { it.toRecord() }

    suspend fun mistake(id: Long): MistakeRecord? = mistakes.get(id)?.toRecord()

    /** Removes every practice record, including an unfinished session. */
    suspend fun clearAllPracticeData() = db.withTransaction {
        sessions.deleteAll()
        mistakes.deleteAll()
        progress.deleteAllBadges()
        progress.deleteTotals()
    }
}
