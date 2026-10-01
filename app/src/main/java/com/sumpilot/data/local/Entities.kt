package com.sumpilot.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Decimals are stored as canonical strings (BigDecimal.toPlainString without trailing zeros). */
@Entity(tableName = "calculations", indices = [Index("createdAt")])
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstOperand: String,
    val secondOperand: String,
    val operator: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "sessions", indices = [Index("status"), Index("finishedAt")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val difficulty: String,
    /** Comma-separated operation keys, e.g. "add,sub,mul,div". Immutable after creation. */
    val operations: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val remainingMillis: Long?,
    val activeAnsweringMillis: Long,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "position"], unique = true)],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val firstOperand: Int,
    val operator: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    /** Comma-separated options in display order. */
    val answerOptions: String,
    val selectedAnswer: Int?,
    val answeredAt: Long?,
    val isCorrect: Boolean?,
    val hintUsed: Boolean,
)

@Entity(tableName = "mistakes", indices = [Index(value = ["normalizedExpressionKey"], unique = true), Index("resolvedAt")])
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedExpressionKey: String,
    val firstOperand: Int,
    val operator: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val resolvedAt: Long?,
)

/**
 * Links a mistake to the session question(s) it came from, for session-filtered practice.
 * Deliberately has no foreign key to sessions: history retention must not delete mistakes.
 */
@Entity(
    tableName = "mistake_sources",
    primaryKeys = ["mistakeId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = MistakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class MistakeSourceEntity(
    val mistakeId: Long,
    val sessionId: Long,
    val questionId: Long,
)

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val badgeKey: String,
    val unlockedAt: Long,
)

/** Single-row table (id = 1) of lifetime totals, independent of history retention. */
@Entity(tableName = "progress_totals")
data class ProgressTotalsEntity(
    @PrimaryKey val id: Int = 1,
    val originalAnswered: Int = 0,
    val originalCorrect: Int = 0,
    val completedSessions: Int = 0,
    @ColumnInfo(defaultValue = "0") val completedTenQuestionSessions: Int = 0,
    val resolvedMistakes: Int = 0,
    val additionAnswered: Int = 0,
    val subtractionAnswered: Int = 0,
    val multiplicationAnswered: Int = 0,
    val divisionAnswered: Int = 0,
)
