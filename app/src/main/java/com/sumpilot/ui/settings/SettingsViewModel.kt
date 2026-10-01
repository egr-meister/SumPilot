package com.sumpilot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.data.repository.AppSettings
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClearAction(val title: String, val explanation: String, val doneMessage: String) {
    CALCULATOR_HISTORY(
        "Clear calculator history",
        "Removes saved calculations. Practice progress, mistakes and badges are kept.",
        "Calculator history cleared.",
    ),
    SESSION_HISTORY(
        "Clear session history",
        "Removes finished missions from Session History. Unresolved mistakes are kept — use “Clear unresolved mistakes” to remove them. " +
            "Badges and lifetime totals are kept. A mission in progress is not affected.",
        "Session history cleared.",
    ),
    MISTAKES(
        "Clear unresolved mistakes",
        "Removes every mistake from Mistake Practice. Session history and badges are kept.",
        "Mistakes cleared.",
    ),
    PROGRESS(
        "Clear practice progress and badges",
        "Resets lifetime totals and removes all badges. Session history and mistakes are kept.",
        "Progress and badges cleared.",
    ),
    ALL(
        "Clear all local data",
        "Removes everything SumPilot has stored on this device: calculator history, missions (including one in progress), " +
            "mistakes, progress, badges and settings.",
        "All local data cleared.",
    ),
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settings

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setType(t: SessionType) { viewModelScope.launch { settingsRepo.setDefaultType(t) } }
    fun setDifficulty(d: Difficulty) { viewModelScope.launch { settingsRepo.setDefaultDifficulty(d) } }
    fun setSound(on: Boolean) {
        viewModelScope.launch {
            settingsRepo.setSound(on)
            if (on) container.soundPlayer.preload()
        }
    }
    fun setReducedMotion(on: Boolean) { viewModelScope.launch { settingsRepo.setReducedMotion(on) } }

    fun toggleOperation(op: Operation) {
        viewModelScope.launch {
            val current = settingsRepo.settings.first().enabledOperations
            val next = if (op in current) current - op else current + op
            if (next.isNotEmpty()) settingsRepo.setEnabledOperations(next)
        }
    }

    fun selectMixed() { viewModelScope.launch { settingsRepo.setEnabledOperations(Operation.entries.toSet()) } }

    fun clear(action: ClearAction) {
        viewModelScope.launch {
            when (action) {
                ClearAction.CALCULATOR_HISTORY -> container.calculatorRepository.clear()
                ClearAction.SESSION_HISTORY -> container.practiceService.clearSessionHistory()
                ClearAction.MISTAKES -> container.practiceService.clearMistakes()
                ClearAction.PROGRESS -> container.practiceService.clearProgressAndBadges()
                ClearAction.ALL -> {
                    container.calculatorRepository.clear()
                    container.practiceRepository.clearAllPracticeData()
                    container.settings.clearAll()
                }
            }
            _message.value = action.doneMessage
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
