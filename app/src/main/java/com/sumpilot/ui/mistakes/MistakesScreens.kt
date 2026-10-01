package com.sumpilot.ui.mistakes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.domain.generation.spokenExpression
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.EmptyState
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotIcons
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.PrimaryButton
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.theme.ExpressionStyle
import com.sumpilot.ui.theme.PilotColors

@Composable
fun MistakesScreen(
    onBack: () -> Unit,
    onRetry: (mistakeId: Long?, sessionId: Long?) -> Unit,
    vm: MistakesViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val title = if (state.sessionFilter != null) "Mistakes from this session" else "Mistake practice"
    PilotScaffold(title = title, onBack = onBack) { padding ->
        if (!state.loaded) return@PilotScaffold
        if (state.mistakes.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState("No mistakes to practice right now.") }
            return@PilotScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Try these again at your own pace. There is no timer.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PrimaryButton(
                        "Practice all (${state.mistakes.size})",
                        onClick = { onRetry(null, state.sessionFilter) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.mistakes, key = { it.id }) { m ->
                Surface(
                    color = PilotColors.Cream,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(2.dp, PilotColors.CreamEdge),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                ) {
                    // The correct answer is intentionally not shown in the list.
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text(
                                m.expression,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = spokenExpression(m.first, m.operation, m.second)
                                },
                            )
                            Text(m.operation.label, style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
                        }
                        SecondaryButton("Try again", onClick = { onRetry(m.id, state.sessionFilter) })
                    }
                }
            }
        }
    }
}

@Composable
fun RetryScreen(
    onBack: () -> Unit,
    vm: RetryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PilotScaffold(title = "Try again", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (state.phase) {
                RetryPhase.LOADING -> CircularProgressIndicator(Modifier.padding(48.dp))
                RetryPhase.DONE -> Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InstrumentPanel(title = "All done for now") {
                        Text("Nice work coming back to these.", style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton("Back to the list", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    }
                }
                else -> RetryContent(state, vm, onBack)
            }
        }
    }
}

@Composable
private fun RetryContent(state: RetryUiState, vm: RetryViewModel, onBack: () -> Unit) {
    val m = state.mistake ?: return
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.total > 1) {
            Text("Mistake ${state.position + 1} of ${state.total}", style = MaterialTheme.typography.titleMedium)
        }
        InstrumentPanel(contentPadding = PaddingValues(vertical = 28.dp, horizontal = 16.dp)) {
            Text(m.operation.label, style = MaterialTheme.typography.labelLarge, color = PilotColors.NavySoft,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                "${m.expression} = ?",
                style = ExpressionStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "What is ${spokenExpression(m.first, m.operation, m.second)}?"
                    heading()
                },
            )
        }

        val enabled = state.phase == RetryPhase.ANSWERING && !state.submitting
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.options.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        val chosen = state.selected == option && state.phase != RetryPhase.ANSWERING
                        Surface(
                            onClick = { vm.submit(option) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 76.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (chosen) PilotColors.SkyDeep else PilotColors.White,
                            contentColor = PilotColors.Navy,
                            border = BorderStroke(3.dp, if (chosen) PilotColors.Navy else PilotColors.CreamEdge),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(option.toString(), style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                    }
                }
            }
        }

        when (state.phase) {
            RetryPhase.ANSWERING -> {
                if (state.hintVisible) {
                    Surface(color = PilotColors.TealSoft, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Hint", style = MaterialTheme.typography.titleMedium)
                            Text(state.hintText, style = MaterialTheme.typography.bodyLarge)
                            SecondaryButton("Got it", onClick = vm::toggleHint)
                        }
                    }
                } else {
                    SecondaryButton("Hint", onClick = vm::toggleHint, icon = PilotIcons.Hint)
                }
            }
            RetryPhase.CORRECT -> InstrumentPanel(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("That’s right!", style = MaterialTheme.typography.headlineSmall, color = PilotColors.Teal)
                Text(state.explanation, style = MaterialTheme.typography.bodyLarge)
                state.newBadges.forEach { Text("New badge: ${it.title}", style = MaterialTheme.typography.titleSmall) }
                PrimaryButton(
                    if (state.hasNext) "Continue" else "Back to the list",
                    onClick = { if (state.hasNext) vm.next() else onBack() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RetryPhase.INCORRECT -> InstrumentPanel(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("Not this time. Let’s try once more.", style = MaterialTheme.typography.headlineSmall)
                Text(state.hintText, style = MaterialTheme.typography.bodyLarge)
                PrimaryButton("Try again", onClick = vm::tryAgain, modifier = Modifier.fillMaxWidth())
                if (state.hasNext) SecondaryButton("Next mistake", onClick = vm::next, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Back to the list", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }
    }
}
