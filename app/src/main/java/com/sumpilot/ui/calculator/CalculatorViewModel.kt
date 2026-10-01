package com.sumpilot.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.data.repository.HistoryEntry
import com.sumpilot.domain.calculator.CalcOperator
import com.sumpilot.domain.calculator.CalculatorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CalcKey {
    data class Digit(val d: Char) : CalcKey
    data object Decimal : CalcKey
    data class Op(val op: CalcOperator) : CalcKey
    data object Equals : CalcKey
    data object Clear : CalcKey
    data object Backspace : CalcKey
    data object Sign : CalcKey
}

/** Calculator use never touches practice statistics. */
class CalculatorViewModel(private val container: AppContainer) : ViewModel() {
    private val engine = container.calculatorEngine
    private val repo = container.calculatorRepository

    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val history: StateFlow<List<HistoryEntry>> =
        repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _state.value = container.settings.loadCalculatorDraft()
            _loaded.value = true
        }
    }

    fun press(key: CalcKey) {
        if (!_loaded.value) return
        val current = _state.value
        val next = when (key) {
            is CalcKey.Digit -> engine.digit(current, key.d)
            CalcKey.Decimal -> engine.decimal(current)
            is CalcKey.Op -> engine.operator(current, key.op)
            CalcKey.Clear -> engine.clear()
            CalcKey.Backspace -> engine.backspace(current)
            CalcKey.Sign -> engine.toggleSign(current)
            CalcKey.Equals -> {
                val outcome = engine.equals(current)
                outcome.completed?.let { calc -> viewModelScope.launch { repo.add(calc) } }
                outcome.state
            }
        }
        update(next)
    }

    fun useResult(entry: HistoryEntry) = update(engine.useResult(entry.result))

    fun clearHistory() {
        viewModelScope.launch { repo.clear() }
    }

    private fun update(next: CalculatorState) {
        if (next == _state.value) return
        _state.value = next
        // The draft survives navigation and process recreation.
        container.applicationScope.launch { container.settings.saveCalculatorDraft(next) }
    }
}
