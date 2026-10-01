package com.sumpilot.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.SessionSummary
import com.sumpilot.domain.sessions.StartResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResultsUiState(
    val loaded: Boolean = false,
    val summary: SessionSummary? = null,
    /** Answered questions only, in order. Unanswered questions never appear in review. */
    val answered: List<QuestionRecord> = emptyList(),
    val unresolvedFromSession: Int = 0,
)

class ResultsViewModel(private val container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    val sessionId: Long = savedState.get<Long>("sessionId") ?: -1L
    private val repo = container.practiceRepository

    val state: StateFlow<ResultsUiState> = combine(
        repo.observeSummary(sessionId),
        repo.observeQuestions(sessionId),
        repo.observeUnresolvedMistakes(sessionId),
    ) { summary, questions, mistakes ->
        ResultsUiState(true, summary, questions.filter { it.isAnswered }, mistakes.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState())

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    /** "Start another mission" with the same configuration as this session. */
    fun startAnother() {
        val config = state.value.summary?.session?.config ?: return
        viewModelScope.launch {
            when (container.practiceService.startSession(config)) {
                is StartResult.Started, is StartResult.UnfinishedExists -> _started.value = true
            }
        }
    }

    fun consumeStarted() {
        _started.value = false
    }
}
