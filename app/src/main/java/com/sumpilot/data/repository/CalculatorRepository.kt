package com.sumpilot.data.repository

import com.sumpilot.data.local.AppDatabase
import com.sumpilot.data.local.CalculationEntity
import com.sumpilot.domain.calculator.CalcOperator
import com.sumpilot.domain.calculator.CompletedCalculation
import com.sumpilot.domain.model.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

data class HistoryEntry(
    val id: Long,
    val first: String,
    val operator: CalcOperator,
    val second: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

/** Calculator history: the latest 50 successful calculations. Independent of practice data. */
class CalculatorRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.calculationDao()

    fun observeHistory(): Flow<List<HistoryEntry>> = dao.observeLatest(HISTORY_LIMIT).map { list ->
        list.mapNotNull { e ->
            val op = CalcOperator.fromKey(e.operator) ?: return@mapNotNull null
            HistoryEntry(e.id, e.firstOperand, op, e.secondOperand, e.result, e.rounded, e.createdAt)
        }
    }

    fun observeLatest(): Flow<HistoryEntry?> = observeHistory().map { it.firstOrNull() }

    suspend fun add(calc: CompletedCalculation) = db.withTransaction {
        dao.insert(
            CalculationEntity(
                firstOperand = calc.first,
                secondOperand = calc.second,
                operator = calc.operator.key,
                result = calc.result,
                rounded = calc.rounded,
                createdAt = clock.now(),
            ),
        )
        dao.trimTo(HISTORY_LIMIT)
    }

    suspend fun clear() = dao.deleteAll()

    companion object {
        const val HISTORY_LIMIT = 50
    }
}
