package com.sumpilot.ui.mission

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumpilot.AppContainer
import com.sumpilot.domain.generation.Explanations
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.sessions.EndResult
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.sessions.SessionRecord
import com.sumpilot.domain.sessions.TimingSnapshot
import com.sumpilot.domain.timer.ActiveTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MissionPhase {
    /** Loading the saved session. */
    LOADING,

    /** Restored after process recreation: waits for "Resume mission". Timer paused. */
    PAUSED,

    /** A question is ready for interaction. The timer runs only in this phase. */
    ANSWERING,

    /** The answer was recorded; feedback and explanation are shown. Timer paused. */
    FEEDBACK,
}

data class AnswerFeedback(
    val selected: Int,
    val correctAnswer: Int,
    val isCorrect: Boolean,
    val explanation: String,
)

data class MissionUiState(
    val phase: MissionPhase = MissionPhase.LOADING,
    val session: SessionRecord? = null,
    val question: QuestionRecord? = null,
    val answeredCount: Int = 0,
    val remainingMillis: Long? = null,
    val timeUp: Boolean = false,
    val hintVisible: Boolean = false,
    val hintText: String = "",
    val feedback: AnswerFeedback? = null,
    val sessionCompleted: Boolean = false,
    val submitting: Boolean = false,
    val showEndConfirm: Boolean = false,
    val newBadges: List<Badge> = emptyList(),
) {
    val isTimed: Boolean get() = session?.type == SessionType.FIVE_MINUTES

    /** After feedback, the next action leads to results instead of another question. */
    val nextLeadsToResults: Boolean get() = sessionCompleted || (isTimed && timeUp)
}

sealed interface MissionEvent {
    data class Finished(val sessionId: Long) : MissionEvent
    data object Discarded : MissionEvent
}

class MissionViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val service = container.practiceService
    private val clock = container.clock

    private val _state = MutableStateFlow(MissionUiState())
    val state: StateFlow<MissionUiState> = _state.asStateFlow()

    private val _event = MutableStateFlow<MissionEvent?>(null)
    val event: StateFlow<MissionEvent?> = _event.asStateFlow()

    private val soundOn = container.settings.settings.map { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var timer = ActiveTimer(limitMillis = null)
    private var screenVisible = false
    private var tickJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val session = container.practiceRepository.unfinishedSession()
            if (session == null) {
                _event.value = MissionEvent.Discarded
                return@launch
            }
            timer = ActiveTimer.restore(session.limitMillis, session.remainingMillis, session.activeAnsweringMillis)
            val autoStart = savedState.get<Boolean>(KEY_AUTO_START) == true
            val alreadyStarted = savedState.get<Boolean>(KEY_STARTED) == true
            // Only a fresh Start/Resume tap goes straight to answering. After process recreation
            // (or reboot), the session is restored paused and requires "Resume mission".
            val gated = !autoStart || alreadyStarted
            savedState[KEY_STARTED] = true

            val timeUp = timer.isExpired(clock.elapsedRealtime())
            val question = when {
                session.type == SessionType.TEN_QUESTIONS -> service.currentQuestion(session.id)
                timeUp -> service.currentQuestion(session.id) // never generate after expiry
                else -> service.nextTimedQuestion(session.id)
            }
            if (session.type == SessionType.TEN_QUESTIONS && question == null) {
                // All answered (should already be completed) — go to results.
                _event.value = MissionEvent.Finished(session.id)
                return@launch
            }
            val answered = answeredCount(session.id)
            _state.value = MissionUiState(
                phase = if (gated) MissionPhase.PAUSED else MissionPhase.ANSWERING,
                session = session,
                question = question,
                answeredCount = answered,
                remainingMillis = timer.remainingMillis(clock.elapsedRealtime()),
                timeUp = timeUp,
                hintText = question?.let { Explanations.hint(it.first, it.operation, it.second) }.orEmpty(),
            )
            updateTimer()
        }
    }

    private suspend fun answeredCount(sessionId: Long): Int =
        container.practiceRepository.answeredCount(sessionId)

    // ---- Visibility & timer -------------------------------------------------------------

    /** Called on ON_START / ON_STOP of the mission screen (covers backgrounding and navigation away). */
    fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
        updateTimer()
    }

    private fun timerShouldRun(s: MissionUiState): Boolean =
        screenVisible && s.phase == MissionPhase.ANSWERING && s.question != null && !s.hintVisible &&
            !s.showEndConfirm && !s.submitting && !s.timeUp

    /** Resumes or pauses the timer to match the UI state. Persists checkpoints when pausing. */
    private fun updateTimer() {
        val s = _state.value
        val now = clock.elapsedRealtime()
        if (s.session == null) return
        if (timerShouldRun(s)) {
            if (!timer.isRunning) {
                timer = timer.resume(now)
                startTicking()
            }
        } else if (timer.isRunning) {
            timer = timer.pause(now)
            tickJob?.cancel()
            _state.update { it.copy(remainingMillis = timer.remainingMillis(now)) }
            persistTiming()
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive && timer.isRunning) {
                val now = clock.elapsedRealtime()
                if (timer.isExpired(now)) {
                    onExpired()
                    break
                }
                _state.update { it.copy(remainingMillis = timer.remainingMillis(now)) }
                delay(TICK_MILLIS)
            }
        }
    }

    /** Timer expiry: keep the current question available; no penalty, no new question. */
    private fun onExpired() {
        val now = clock.elapsedRealtime()
        timer = timer.pause(now)
        _state.update { it.copy(timeUp = true, remainingMillis = 0L) }
        persistTiming()
    }

    private fun snapshot(): TimingSnapshot {
        val now = clock.elapsedRealtime()
        return TimingSnapshot(timer.remainingMillis(now), timer.activeMillis(now))
    }

    private fun persistTiming() {
        val id = _state.value.session?.id ?: return
        val snap = snapshot()
        // Application scope on Main.immediate: survives screen disposal and keeps write order.
        container.applicationScope.launch { service.saveTiming(id, snap) }
    }

    // ---- User actions -------------------------------------------------------------------

    fun resumeMission() {
        if (_state.value.phase != MissionPhase.PAUSED) return
        _state.update { it.copy(phase = MissionPhase.ANSWERING) }
        updateTimer()
    }

    fun submitAnswer(option: Int) {
        val s = _state.value
        val session = s.session ?: return
        val question = s.question ?: return
        // Main-thread ordering: the first tap wins; later taps and re-entries are ignored.
        if (s.phase != MissionPhase.ANSWERING || s.submitting || question.isAnswered) return
        _state.update { it.copy(submitting = true) }
        updateTimer() // pauses: feedback time is never counted
        val timing = snapshot()
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) { service.submitAnswer(session.id, question.id, option, timing) }
            if (outcome == null) {
                // Already recorded (e.g. restored state); reload from storage.
                _state.update { it.copy(submitting = false) }
                reloadCurrent()
                return@launch
            }
            if (soundOn.value) {
                if (outcome.isCorrect) container.soundPlayer.playCorrect() else container.soundPlayer.playGentle()
            }
            _state.update {
                it.copy(
                    phase = MissionPhase.FEEDBACK,
                    submitting = false,
                    hintVisible = false,
                    question = outcome.question,
                    answeredCount = it.answeredCount + 1,
                    sessionCompleted = outcome.sessionCompleted,
                    newBadges = outcome.newBadges,
                    feedback = AnswerFeedback(
                        selected = option,
                        correctAnswer = question.correctAnswer,
                        isCorrect = outcome.isCorrect,
                        explanation = Explanations.explanation(question.first, question.operation, question.second),
                    ),
                )
            }
        }
    }

    /** "Next question" (or "See results"). Never auto-advances. */
    fun next() {
        val s = _state.value
        val session = s.session ?: return
        if (s.submitting) return
        if (s.phase != MissionPhase.FEEDBACK && !(s.timeUp && s.question == null)) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when {
                s.sessionCompleted -> _event.value = MissionEvent.Finished(session.id)
                s.isTimed && (s.timeUp || timer.isExpired(clock.elapsedRealtime())) -> finishTimed(session.id)
                else -> {
                    val nextQuestion = if (s.isTimed) service.nextTimedQuestion(session.id) else service.currentQuestion(session.id)
                    if (nextQuestion == null) {
                        _event.value = MissionEvent.Finished(session.id)
                    } else {
                        showQuestion(nextQuestion)
                    }
                }
            }
        }
    }

    /** After expiry the child may skip the remaining question; it is not counted. */
    fun skipAfterTimeUp() {
        val s = _state.value
        val session = s.session ?: return
        if (!s.timeUp || s.phase != MissionPhase.ANSWERING || s.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch { finishTimed(session.id) }
    }

    private suspend fun finishTimed(sessionId: Long) {
        val result = withContext(NonCancellable) { service.completeTimedSession(sessionId, snapshot()) }
        _event.value = when (result) {
            is EndResult.Finished -> MissionEvent.Finished(result.sessionId)
            EndResult.Discarded -> MissionEvent.Discarded
        }
    }

    private fun showQuestion(q: QuestionRecord) {
        _state.update {
            it.copy(
                phase = MissionPhase.ANSWERING,
                question = q,
                feedback = null,
                hintVisible = false,
                hintText = Explanations.hint(q.first, q.operation, q.second),
                submitting = false,
                newBadges = emptyList(),
            )
        }
        updateTimer()
    }

    private suspend fun reloadCurrent() {
        val session = _state.value.session ?: return
        val q = service.currentQuestion(session.id)
        _state.update { it.copy(answeredCount = answeredCount(session.id)) }
        if (q != null) showQuestion(q) else if (!_state.value.isTimed) _event.value = MissionEvent.Finished(session.id)
    }

    fun openHint() {
        val s = _state.value
        val q = s.question ?: return
        if (s.phase != MissionPhase.ANSWERING || s.hintVisible) return
        _state.update { it.copy(hintVisible = true, question = q.copy(hintUsed = true)) }
        updateTimer() // hint open → timer paused
        viewModelScope.launch { withContext(NonCancellable) { service.markHintUsed(q.id) } }
    }

    fun closeHint() {
        _state.update { it.copy(hintVisible = false) }
        updateTimer()
    }

    fun requestEnd() {
        _state.update { it.copy(showEndConfirm = true) }
        updateTimer() // a blocking dialog pauses the timer
    }

    fun cancelEnd() {
        _state.update { it.copy(showEndConfirm = false) }
        updateTimer()
    }

    fun confirmEnd() {
        val session = _state.value.session ?: return
        _state.update { it.copy(showEndConfirm = false, submitting = true) }
        updateTimer()
        viewModelScope.launch {
            val result = withContext(NonCancellable) { service.endEarly(session.id, snapshot()) }
            _event.value = when (result) {
                is EndResult.Finished -> MissionEvent.Finished(result.sessionId)
                EndResult.Discarded -> MissionEvent.Discarded
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    override fun onCleared() {
        // Persist the latest checkpoint if the timer was somehow still running.
        if (timer.isRunning) {
            timer = timer.pause(clock.elapsedRealtime())
            val id = _state.value.session?.id
            if (id != null) {
                val snap = snapshot()
                container.applicationScope.launch { service.saveTiming(id, snap) }
            }
        }
        super.onCleared()
    }

    companion object {
        const val KEY_AUTO_START = "autoStart"
        private const val KEY_STARTED = "missionStartedOnce"
        private const val TICK_MILLIS = 200L
    }
}
