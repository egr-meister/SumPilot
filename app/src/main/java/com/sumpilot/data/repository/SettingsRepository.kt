package com.sumpilot.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sumpilot.domain.calculator.CalcOperator
import com.sumpilot.domain.calculator.CalculatorState
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.MissionConfig
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    val defaultType: SessionType = SessionType.TEN_QUESTIONS,
    val defaultDifficulty: Difficulty = Difficulty.EASY,
    val enabledOperations: Set<Operation> = Operation.entries.toSet(),
    val soundOn: Boolean = false,
    val reducedMotion: Boolean = false,
) {
    val missionConfig: MissionConfig get() = MissionConfig(defaultType, defaultDifficulty, enabledOperations)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "sumpilot_settings")

/** Preferences and the calculator draft, stored with DataStore in app-private storage. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val type = stringPreferencesKey("default_type")
        val difficulty = stringPreferencesKey("default_difficulty")
        val operations = stringPreferencesKey("enabled_operations")
        val sound = booleanPreferencesKey("sound_on")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val draftFirst = stringPreferencesKey("calc_first")
        val draftOperator = stringPreferencesKey("calc_operator")
        val draftSecond = stringPreferencesKey("calc_second")
        val draftResult = stringPreferencesKey("calc_result")
        val draftRounded = booleanPreferencesKey("calc_result_rounded")
        val draftExpression = stringPreferencesKey("calc_result_expression")
    }

    private val safeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = safeData.map { p ->
        AppSettings(
            defaultType = SessionType.fromKey(p[Keys.type]),
            defaultDifficulty = Difficulty.fromKey(p[Keys.difficulty]),
            enabledOperations = p[Keys.operations]?.let { Operation.decode(it) }?.ifEmpty { null }
                ?: Operation.entries.toSet(),
            soundOn = p[Keys.sound] ?: false,
            reducedMotion = p[Keys.reducedMotion] ?: false,
        )
    }

    suspend fun setDefaultType(type: SessionType) = store.edit { it[Keys.type] = type.key }
    suspend fun setDefaultDifficulty(d: Difficulty) = store.edit { it[Keys.difficulty] = d.key }

    /** Ignores attempts to disable the last enabled operation. */
    suspend fun setEnabledOperations(ops: Set<Operation>) {
        if (ops.isEmpty()) return
        store.edit { it[Keys.operations] = Operation.encode(ops) }
    }

    suspend fun setSound(on: Boolean) = store.edit { it[Keys.sound] = on }
    suspend fun setReducedMotion(on: Boolean) = store.edit { it[Keys.reducedMotion] = on }

    suspend fun loadCalculatorDraft(): CalculatorState {
        val p = safeData.first()
        return CalculatorState(
            first = p[Keys.draftFirst].orEmpty(),
            operator = CalcOperator.fromKey(p[Keys.draftOperator]),
            second = p[Keys.draftSecond].orEmpty(),
            result = p[Keys.draftResult],
            resultRounded = p[Keys.draftRounded] ?: false,
            resultExpression = p[Keys.draftExpression],
        )
    }

    suspend fun saveCalculatorDraft(state: CalculatorState) = store.edit { p ->
        p[Keys.draftFirst] = state.first
        p[Keys.draftSecond] = state.second
        p[Keys.draftRounded] = state.resultRounded
        if (state.operator != null) p[Keys.draftOperator] = state.operator.key else p.remove(Keys.draftOperator)
        if (state.result != null) p[Keys.draftResult] = state.result else p.remove(Keys.draftResult)
        if (state.resultExpression != null) p[Keys.draftExpression] = state.resultExpression else p.remove(Keys.draftExpression)
    }

    suspend fun clearCalculatorDraft() = store.edit { p ->
        p.remove(Keys.draftFirst)
        p.remove(Keys.draftOperator)
        p.remove(Keys.draftSecond)
        p.remove(Keys.draftResult)
        p.remove(Keys.draftRounded)
        p.remove(Keys.draftExpression)
    }

    suspend fun clearAll() = store.edit { it.clear() }
}
