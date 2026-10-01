package com.sumpilot.ui.mission

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.domain.generation.formatExpression
import com.sumpilot.domain.generation.spokenExpression
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.timer.formatClock
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.ConfirmDialog
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotIcons
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.PrimaryButton
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.theme.ExpressionStyle
import com.sumpilot.ui.theme.LocalReducedMotion
import com.sumpilot.ui.theme.PilotColors

@Composable
fun MissionScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onDiscarded: () -> Unit,
    vm: MissionViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()

    // Leaving the screen or backgrounding the app pauses the timer; returning resumes it.
    LifecycleStartEffect(vm) {
        vm.setScreenVisible(true)
        onStopOrDispose { vm.setScreenVisible(false) }
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is MissionEvent.Finished -> { vm.consumeEvent(); onFinished(e.sessionId) }
            MissionEvent.Discarded -> { vm.consumeEvent(); onDiscarded() }
            null -> Unit
        }
    }

    val title = state.session?.type?.label ?: "Mission"
    PilotScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (state.session != null) {
                TextButton(onClick = vm::requestEnd, modifier = Modifier.heightIn(min = 48.dp)) { Text("End mission") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.phase == MissionPhase.LOADING) {
                CircularProgressIndicator(Modifier.padding(48.dp).semantics { contentDescription = "Loading mission" })
            } else {
                MissionContent(state, vm)
            }
        }
    }

    if (state.showEndConfirm) {
        val answered = state.answeredCount
        ConfirmDialog(
            title = "End this mission?",
            text = if (answered > 0) {
                "Your $answered answered ${if (answered == 1) "question is" else "questions are"} kept. " +
                    "Questions you haven't answered won't count."
            } else {
                "You haven't answered any questions yet, so this mission won't be saved in history."
            },
            confirmLabel = "End mission",
            dismissLabel = "Keep going",
            onConfirm = vm::confirmEnd,
            onDismiss = vm::cancelEnd,
        )
    }
}

@Composable
private fun MissionContent(state: MissionUiState, vm: MissionViewModel) {
    val session = state.session ?: return
    val reduced = LocalReducedMotion.current
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusStrip(state)

        if (state.phase == MissionPhase.PAUSED) {
            InstrumentPanel(title = "Mission paused") {
                Text(
                    if (session.type == SessionType.FIVE_MINUTES) {
                        "Your answers are saved and the answering time is paused."
                    } else {
                        "Your answers are saved. Continue whenever you're ready."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton("Resume mission", onClick = vm::resumeMission, icon = Icons.Filled.PlayArrow, modifier = Modifier.fillMaxWidth())
            }
            return@Column
        }

        val question = state.question
        if (question == null) {
            // Timed session whose time ended with no pending question (e.g. restored after expiry).
            InstrumentPanel(title = "Time's up") {
                Text("Your answering time is finished. Nice flying!", style = MaterialTheme.typography.bodyLarge)
                PrimaryButton("See results", onClick = vm::next, modifier = Modifier.fillMaxWidth())
            }
            return@Column
        }

        if (state.timeUp && state.phase == MissionPhase.ANSWERING) {
            Surface(
                color = PilotColors.YellowSoft,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Answering time is finished.", style = MaterialTheme.typography.titleMedium)
                    Text("You can still answer this question, or skip it. Skipped questions don't count as mistakes.")
                    SecondaryButton("Skip and see results", onClick = vm::skipAfterTimeUp, enabled = !state.submitting)
                }
            }
        }

        // Expression
        InstrumentPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 28.dp, horizontal = 16.dp)) {
            Text(
                text = question.operation.label,
                style = MaterialTheme.typography.labelLarge,
                color = PilotColors.NavySoft,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${formatExpression(question.first, question.operation, question.second)} = ?",
                style = ExpressionStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "What is ${spokenExpression(question.first, question.operation, question.second)}?"
                        heading()
                    },
            )
        }

        AnswerGrid(state, vm)

        if (state.phase == MissionPhase.ANSWERING) {
            if (state.hintVisible) {
                Surface(
                    color = PilotColors.TealSoft,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(PilotIcons.Hint, contentDescription = null, tint = PilotColors.Navy)
                            Spacer(Modifier.size(8.dp))
                            Text("Hint", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(state.hintText, style = MaterialTheme.typography.bodyLarge)
                        if (session.type == SessionType.FIVE_MINUTES && !state.timeUp) {
                            Text("Answering time is paused while the hint is open.", style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
                        }
                        SecondaryButton("Got it", onClick = vm::closeHint)
                    }
                }
            } else {
                SecondaryButton("Hint", onClick = vm::openHint, icon = PilotIcons.Hint, enabled = !state.submitting)
            }
        }

        AnimatedVisibility(
            visible = state.phase == MissionPhase.FEEDBACK && state.feedback != null,
            enter = if (reduced) EnterTransition.None else fadeIn(),
            exit = if (reduced) ExitTransition.None else fadeOut(),
        ) {
            state.feedback?.let { FeedbackCard(state, it, vm) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusStrip(state: MissionUiState) {
    val session = state.session ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val progress = if (session.type == SessionType.TEN_QUESTIONS) {
            val current = (state.answeredCount + if (state.phase == MissionPhase.FEEDBACK) 0 else 1)
                .coerceIn(1, SessionType.TEN_QUESTION_COUNT)
            "Question $current of ${SessionType.TEN_QUESTION_COUNT}"
        } else {
            "${state.answeredCount} answered"
        }
        Column {
            Text(progress, style = MaterialTheme.typography.titleMedium)
            Text(session.difficulty.label, style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
        }
        val remaining = state.remainingMillis
        if (session.type == SessionType.FIVE_MINUTES && remaining != null) {
            val paused = state.phase != MissionPhase.ANSWERING || state.hintVisible || state.timeUp
            Surface(
                color = PilotColors.YellowSoft,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(2.dp, PilotColors.Yellow),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = if (paused) "paused" else "running"
                },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        "Answering time",
                        style = MaterialTheme.typography.labelMedium,
                        color = PilotColors.NavySoft,
                    )
                    Text(
                        formatClock(remaining) + if (paused && !state.timeUp) "  (paused)" else "",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerGrid(state: MissionUiState, vm: MissionViewModel) {
    val question = state.question ?: return
    val feedback = state.feedback
    val enabled = state.phase == MissionPhase.ANSWERING && !state.submitting
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    AnswerButton(
                        value = option,
                        enabled = enabled,
                        isSelected = feedback?.selected == option,
                        isCorrectAnswer = feedback != null && option == feedback.correctAnswer,
                        showResult = feedback != null,
                        onClick = { vm.submitAnswer(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerButton(
    value: Int,
    enabled: Boolean,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    showResult: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        showResult && isCorrectAnswer -> PilotColors.TealSoft
        showResult && isSelected -> PilotColors.YellowSoft
        else -> PilotColors.White
    }
    val border = when {
        showResult && isCorrectAnswer -> PilotColors.Teal
        showResult && isSelected -> PilotColors.Coral
        else -> PilotColors.CreamEdge
    }
    val status = when {
        showResult && isCorrectAnswer && isSelected -> "Your answer. Correct answer."
        showResult && isCorrectAnswer -> "Correct answer."
        showResult && isSelected -> "Your answer."
        else -> null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 76.dp)
            .semantics { if (status != null) stateDescription = status },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = PilotColors.Navy,
        border = BorderStroke(3.dp, border),
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineLarge)
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCorrectAnswer) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        when {
                            isCorrectAnswer && isSelected -> "Your answer"
                            isCorrectAnswer -> "Correct answer"
                            else -> "Your answer"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(state: MissionUiState, feedback: AnswerFeedback, vm: MissionViewModel) {
    InstrumentPanel(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            if (feedback.isCorrect) "That’s right!" else "Let’s look at the answer.",
            style = MaterialTheme.typography.headlineSmall,
            color = if (feedback.isCorrect) PilotColors.Teal else PilotColors.Navy,
        )
        if (!feedback.isCorrect) {
            Text(
                "You chose ${feedback.selected}. The answer is ${feedback.correctAnswer}.",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(feedback.explanation, style = MaterialTheme.typography.bodyLarge)
        state.newBadges.forEach { badge ->
            Surface(color = PilotColors.YellowSoft, shape = MaterialTheme.shapes.small) {
                Text(
                    "New badge: ${badge.title}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        PrimaryButton(
            text = if (state.nextLeadsToResults) "See results" else "Next question",
            onClick = vm::next,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
