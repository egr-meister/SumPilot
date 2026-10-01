package com.sumpilot.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Insert
    suspend fun insert(entity: CalculationEntity): Long

    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<CalculationEntity>>

    @Query("DELETE FROM calculations WHERE id NOT IN (SELECT id FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM calculations")
    suspend fun deleteAll()
}

/** Session summary row: a session plus answered/correct counts from its questions. */
data class SessionSummaryRow(
    val id: Long,
    val type: String,
    val difficulty: String,
    val operations: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val remainingMillis: Long?,
    val activeAnsweringMillis: Long,
    val answered: Int,
    val correct: Int,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'in_progress' ORDER BY id DESC LIMIT 1")
    suspend fun getUnfinished(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'in_progress' ORDER BY id DESC LIMIT 1")
    fun observeUnfinished(): Flow<SessionEntity?>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM sessions WHERE status != 'in_progress' ORDER BY finishedAt DESC, id DESC")
    suspend fun finishedIdsNewestFirst(): List<Long>

    @Query("DELETE FROM sessions WHERE status != 'in_progress'")
    suspend fun deleteFinished()

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT s.*,
               (SELECT COUNT(*) FROM questions q WHERE q.sessionId = s.id AND q.selectedAnswer IS NOT NULL) AS answered,
               (SELECT COUNT(*) FROM questions q WHERE q.sessionId = s.id AND q.isCorrect = 1) AS correct
        FROM sessions s
        WHERE s.status != 'in_progress'
        ORDER BY s.finishedAt DESC, s.id DESC
        LIMIT :limit
        """,
    )
    fun observeFinishedSummaries(limit: Int): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT s.*,
               (SELECT COUNT(*) FROM questions q WHERE q.sessionId = s.id AND q.selectedAnswer IS NOT NULL) AS answered,
               (SELECT COUNT(*) FROM questions q WHERE q.sessionId = s.id AND q.isCorrect = 1) AS correct
        FROM sessions s WHERE s.id = :id
        """,
    )
    fun observeSummary(id: Long): Flow<SessionSummaryRow?>
}

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(entity: QuestionEntity): Long

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun get(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY position")
    suspend fun forSession(sessionId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY position")
    fun observeForSession(sessionId: Long): Flow<List<QuestionEntity>>

    /** Conditional update: returns 0 if an answer already exists, so answers are counted once. */
    @Query(
        """
        UPDATE questions SET selectedAnswer = :selected, isCorrect = :isCorrect, answeredAt = :answeredAt
        WHERE id = :id AND selectedAnswer IS NULL
        """,
    )
    suspend fun recordAnswerIfUnanswered(id: Long, selected: Int, isCorrect: Boolean, answeredAt: Long): Int

    @Query("UPDATE questions SET hintUsed = 1 WHERE id = :id AND selectedAnswer IS NULL")
    suspend fun markHintUsed(id: Long)

    @Query("SELECT COUNT(*) FROM questions WHERE sessionId = :sessionId AND selectedAnswer IS NOT NULL")
    suspend fun answeredCount(sessionId: Long): Int

    @Query("DELETE FROM questions WHERE sessionId = :sessionId AND selectedAnswer IS NULL")
    suspend fun deleteUnanswered(sessionId: Long)
}

@Dao
interface MistakeDao {
    @Insert
    suspend fun insert(entity: MistakeEntity): Long

    @Update
    suspend fun update(entity: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE normalizedExpressionKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun get(id: Long): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(entity: MistakeSourceEntity)

    @Query("SELECT * FROM mistakes WHERE resolvedAt IS NULL ORDER BY lastSeenAt DESC, id DESC")
    suspend fun unresolved(): List<MistakeEntity>

    @Query(
        """
        SELECT * FROM mistakes WHERE resolvedAt IS NULL
        AND id IN (SELECT mistakeId FROM mistake_sources WHERE sessionId = :sessionId)
        ORDER BY lastSeenAt DESC, id DESC
        """,
    )
    suspend fun unresolvedForSession(sessionId: Long): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE resolvedAt IS NULL ORDER BY lastSeenAt DESC, id DESC")
    fun observeUnresolved(): Flow<List<MistakeEntity>>

    @Query(
        """
        SELECT * FROM mistakes WHERE resolvedAt IS NULL
        AND id IN (SELECT mistakeId FROM mistake_sources WHERE sessionId = :sessionId)
        ORDER BY lastSeenAt DESC, id DESC
        """,
    )
    fun observeUnresolvedForSession(sessionId: Long): Flow<List<MistakeEntity>>

    @Query("DELETE FROM mistakes")
    suspend fun deleteAll()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_totals WHERE id = 1")
    suspend fun totals(): ProgressTotalsEntity?

    @Query("SELECT * FROM progress_totals WHERE id = 1")
    fun observeTotals(): Flow<ProgressTotalsEntity?>

    @Upsert
    suspend fun saveTotals(entity: ProgressTotalsEntity)

    @Query("SELECT * FROM badges ORDER BY unlockedAt")
    suspend fun badges(): List<BadgeEntity>

    @Query("SELECT * FROM badges ORDER BY unlockedAt")
    fun observeBadges(): Flow<List<BadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlockBadge(entity: BadgeEntity)

    @Query("DELETE FROM badges")
    suspend fun deleteAllBadges()

    @Query("DELETE FROM progress_totals")
    suspend fun deleteTotals()
}
