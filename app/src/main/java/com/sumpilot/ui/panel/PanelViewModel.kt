package com.sumpilot.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.data.repository.AppSettings
import com.sumpilot.data.repository.HistoryEntry
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.sessions.EndResult
import com.sumpilot.domain.sessions.SessionRecord
import com.sumpilot.domain.sessions.SessionSummary
import com.sumpilot.domain.sessions.StartResult
import com.sumpilot.domain.sessions.TimingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PanelUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val unfinished: SessionRecord? = null,
    val latestSession: SessionSummary? = null,
    val latestCalculation: HistoryEntry? = null,
    val unresolvedMistakes: Int = 0,
)

sealed interface PanelEvent {
    data object OpenMission : PanelEvent
    data class ViewResult(val sessionId: Long) : PanelEvent
}

class PanelViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.practiceRepository
    private val settingsRepo = container.settings

    val state: StateFlow<PanelUiState> = combine(
        settingsRepo.settings,
        repo.observeUnfinishedSession(),
        repo.observeLatestFinished(),
        container.calculatorRepository.observeLatest(),
        repo.observeUnresolvedMistakes(null).map { it.size },
    ) { settings, unfinished, latest, calc, mistakes ->
        PanelUiState(true, settings, unfinished, latest, calc, mistakes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    private val _event = MutableStateFlow<PanelEvent?>(null)
    val event: StateFlow<PanelEvent?> = _event.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setType(type: SessionType) = viewModelScope.launch { settingsRepo.setDefaultType(type) }

    fun setDifficulty(d: Difficulty) = viewModelScope.launch { settingsRepo.setDefaultDifficulty(d) }

    fun selectMixed() = viewModelScope.launch { settingsRepo.setEnabledOperations(Operation.entries.toSet()) }

    /** Toggles one operation; at least one must remain enabled. */
    fun toggleOperation(op: Operation) = viewModelScope.launch {
        val current = settingsRepo.settings.first().enabledOperations
        val next = if (op in current) current - op else current + op
        if (next.isNotEmpty()) settingsRepo.setEnabledOperations(next)
    }

    fun startMission() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val config = settingsRepo.settings.first().missionConfig
                when (container.practiceService.startSession(config)) {
                    is StartResult.Started, is StartResult.UnfinishedExists -> _event.value = PanelEvent.OpenMission
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun resumeMission() {
        _event.value = PanelEvent.OpenMission
    }

    /** Ends the unfinished mission from the panel (after confirmation). */
    fun endUnfinished() {
        val session = state.value.unfinished ?: return
        viewModelScope.launch {
            val result = container.practiceService.endEarly(
                session.id,
                TimingSnapshot(session.remainingMillis, session.activeAnsweringMillis),
            )
            if (result is EndResult.Finished) _event.value = PanelEvent.ViewResult(result.sessionId)
        }
    }

    fun consumeEvent() {
        _event.value = null
    }
}
