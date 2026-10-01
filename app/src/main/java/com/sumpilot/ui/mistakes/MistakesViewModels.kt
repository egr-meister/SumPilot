package com.sumpilot.ui.mistakes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.domain.generation.Explanations
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.sessions.MistakeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MistakesUiState(
    val loaded: Boolean = false,
    val sessionFilter: Long? = null,
    val mistakes: List<MistakeRecord> = emptyList(),
)

class MistakesViewModel(container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val sessionFilter: Long? = savedState.get<Long>("sessionId")?.takeIf { it > 0 }

    val state: StateFlow<MistakesUiState> = container.practiceRepository.observeUnresolvedMistakes(sessionFilter)
        .map { MistakesUiState(true, sessionFilter, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MistakesUiState(sessionFilter = sessionFilter))
}

enum class RetryPhase { LOADING, ANSWERING, CORRECT, INCORRECT, DONE }

data class RetryUiState(
    val phase: RetryPhase = RetryPhase.LOADING,
    val mistake: MistakeRecord? = null,
    val options: List<Int> = emptyList(),
    val selected: Int? = null,
    val position: Int = 0,
    val total: Int = 0,
    val hintVisible: Boolean = false,
    val hintText: String = "",
    val explanation: String = "",
    val submitting: Boolean = false,
    val newBadges: List<Badge> = emptyList(),
) {
    val hasNext: Boolean get() = position + 1 < total
}

/**
 * Untimed retries of unresolved mistakes. Never changes original session results or the
 * Accuracy Meter; a correct retry resolves the mistake.
 */
class RetryViewModel(private val container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val mistakeId: Long? = savedState.get<Long>("mistakeId")?.takeIf { it > 0 }
    private val sessionId: Long? = savedState.get<Long>("sessionId")?.takeIf { it > 0 }
    private var queue: List<Long> = emptyList()

    private val _state = MutableStateFlow(RetryUiState())
    val state: StateFlow<RetryUiState> = _state.asStateFlow()

    private val soundOn = container.settings.settings.map { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            queue = if (mistakeId != null) {
                listOf(mistakeId)
            } else {
                container.practiceRepository.unresolvedMistakes(sessionId).map { it.id }
            }
            show(0)
        }
    }

    private suspend fun show(index: Int) {
        if (index >= queue.size) {
            _state.update { it.copy(phase = RetryPhase.DONE, mistake = null, submitting = false) }
            return
        }
        val m = container.practiceRepository.mistake(queue[index])
        if (m == null || m.isResolved) {
            show(index + 1)
            return
        }
        _state.value = RetryUiState(
            phase = RetryPhase.ANSWERING,
            mistake = m,
            options = container.questionGenerator.optionsFor(m.first, m.operation, m.second),
            position = index,
            total = queue.size,
            hintText = Explanations.hint(m.first, m.operation, m.second),
        )
    }

    fun submit(option: Int) {
        val s = _state.value
        val m = s.mistake ?: return
        if (s.phase != RetryPhase.ANSWERING || s.submitting) return
        _state.update { it.copy(submitting = true, selected = option) }
        viewModelScope.launch {
            val out = container.practiceService.retryMistake(m.id, option)
            if (out == null) {
                show(s.position + 1)
                return@launch
            }
            if (soundOn.value) {
                if (out.isCorrect) container.soundPlayer.playCorrect() else container.soundPlayer.playGentle()
            }
            _state.update {
                it.copy(
                    phase = if (out.isCorrect) RetryPhase.CORRECT else RetryPhase.INCORRECT,
                    submitting = false,
                    hintVisible = false,
                    explanation = if (out.isCorrect) Explanations.explanation(m.first, m.operation, m.second) else "",
                    newBadges = out.newBadges,
                )
            }
        }
    }

    /** After an incorrect retry: another attempt with freshly shuffled options. */
    fun tryAgain() {
        val s = _state.value
        val m = s.mistake ?: return
        if (s.phase != RetryPhase.INCORRECT) return
        _state.update {
            it.copy(
                phase = RetryPhase.ANSWERING,
                selected = null,
                options = container.questionGenerator.optionsFor(m.first, m.operation, m.second),
            )
        }
    }

    /** Continue to the next queued mistake (resolved ones leave the active list now). */
    fun next() {
        val s = _state.value
        if (s.phase != RetryPhase.CORRECT && s.phase != RetryPhase.INCORRECT) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch { show(s.position + 1) }
    }

    fun toggleHint() = _state.update { it.copy(hintVisible = !it.hintVisible) }
}
