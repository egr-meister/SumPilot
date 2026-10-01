package com.sumpilot.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.domain.generation.Explanations
import com.sumpilot.domain.generation.spokenExpression
import com.sumpilot.domain.model.SessionStatus
import com.sumpilot.domain.model.SessionType
import com.sumpilot.domain.sessions.QuestionRecord
import com.sumpilot.domain.timer.formatDuration
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.EmptyState
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.PrimaryButton
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.common.StatRow
import com.sumpilot.ui.format.formatDateTime
import com.sumpilot.ui.format.operationsLabel
import com.sumpilot.ui.theme.PilotColors

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    onPracticeMistakes: (Long) -> Unit,
    onStartAnother: () -> Unit,
    onBackToPanel: () -> Unit,
    vm: ResultsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val started by vm.started.collectAsStateWithLifecycle()
    LaunchedEffect(started) {
        if (started) { vm.consumeStarted(); onStartAnother() }
    }

    PilotScaffold(title = "Session result", onBack = onBack) { padding ->
        val summary = state.summary
        if (!state.loaded) return@PilotScaffold
        if (summary == null) {
            Box(Modifier.padding(padding)) { EmptyState("This session is no longer in history.") }
            return@PilotScaffold
        }
        val s = summary.session
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InstrumentPanel(title = "Flight log", modifier = Modifier.fillMaxWidth()) {
                    StatRow("Session type", s.type.label)
                    StatRow("Difficulty", s.difficulty.label)
                    StatRow("Operations", operationsLabel(s.operations))
                    StatRow("Status", s.status.label)
                    StatRow("Date", formatDateTime(s.finishedAt ?: s.startedAt))
                    if (s.type == SessionType.FIVE_MINUTES) {
                        StatRow("Active answering time", formatDuration(s.activeAnsweringMillis))
                    }
                }
                InstrumentPanel(title = "Answers", modifier = Modifier.fillMaxWidth()) {
                    StatRow("Answered", summary.answered.toString())
                    StatRow("Correct", summary.correct.toString())
                    StatRow("Incorrect", summary.incorrect.toString())
                    StatRow("Accuracy", summary.accuracyPercent?.let { "$it%" } ?: "No answers recorded")
                    if (s.status == SessionStatus.ENDED_EARLY) {
                        Text(
                            "Ended early. Only answered questions count toward accuracy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PilotColors.NavySoft,
                        )
                    }
                }
                PrimaryButton("Review answers", onClick = { onReview(s.id) }, modifier = Modifier.fillMaxWidth(), enabled = summary.answered > 0)
                SecondaryButton(
                    if (state.unresolvedFromSession > 0) "Practice mistakes (${state.unresolvedFromSession})" else "Practice mistakes",
                    onClick = { onPracticeMistakes(s.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton("Start another mission", onClick = vm::startAnother, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Back to panel", onClick = onBackToPanel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    vm: ResultsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PilotScaffold(title = "Review answers", onBack = onBack) { padding ->
        if (!state.loaded) return@PilotScaffold
        if (state.answered.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState("No answers recorded.") }
            return@PilotScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(state.answered, key = { it.id }) { q -> ReviewCard(q, Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
        }
    }
}

@Composable
private fun ReviewCard(q: QuestionRecord, modifier: Modifier) {
    val correct = q.isCorrect == true
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = PilotColors.Cream,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (correct) PilotColors.Teal else PilotColors.CreamEdge),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${q.position}. ", style = MaterialTheme.typography.titleMedium, color = PilotColors.NavySoft)
                Text(q.expression, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics {
                    contentDescription = spokenExpression(q.first, q.operation, q.second)
                })
                if (correct) Icon(Icons.Filled.Check, contentDescription = null, tint = PilotColors.Teal, modifier = Modifier.size(24.dp))
            }
            Text(
                if (correct) "Correct" else "Let’s look at this one",
                style = MaterialTheme.typography.titleSmall,
                color = if (correct) PilotColors.Teal else PilotColors.Coral,
            )
            Text("Your answer: ${q.selectedAnswer}", style = MaterialTheme.typography.bodyLarge)
            Text("Correct answer: ${q.correctAnswer}", style = MaterialTheme.typography.bodyLarge)
            Text(Explanations.explanation(q.first, q.operation, q.second), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (q.hintUsed) "Hint used" else "No hint used",
                style = MaterialTheme.typography.labelMedium,
                color = PilotColors.NavySoft,
            )
        }
    }
}
