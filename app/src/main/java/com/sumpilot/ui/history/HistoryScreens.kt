package com.sumpilot.ui.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.AppContainer
import com.sumpilot.data.repository.UnlockedBadge
import com.sumpilot.domain.progress.Badge
import com.sumpilot.domain.sessions.ProgressTotals
import com.sumpilot.domain.sessions.SessionSummary
import com.sumpilot.domain.sessions.PracticeService
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.EmptyState
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.StatRow
import com.sumpilot.ui.format.formatDate
import com.sumpilot.ui.format.formatDateTime
import com.sumpilot.ui.theme.PilotColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val loaded: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val badges: List<UnlockedBadge> = emptyList(),
    val totals: ProgressTotals = ProgressTotals(),
)

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.practiceRepository
    val state: StateFlow<HistoryUiState> = combine(
        repo.observeHistory(PracticeService.HISTORY_LIMIT),
        repo.observeBadges(),
        repo.observeTotals(),
    ) { sessions, badges, totals -> HistoryUiState(true, sessions, badges, totals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PilotScaffold(title = "Session history", onBack = onBack) { padding ->
        if (!state.loaded) return@PilotScaffold
        if (state.sessions.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState("No sessions yet. Finished missions will appear here.") }
            return@PilotScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "The latest ${PracticeService.HISTORY_LIMIT} sessions are kept on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PilotColors.NavySoft,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
            items(state.sessions, key = { it.session.id }) { summary ->
                SessionRow(summary, { onOpenSession(summary.session.id) }, Modifier.widthIn(max = 640.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary, onClick: () -> Unit, modifier: Modifier) {
    val s = summary.session
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        color = PilotColors.Cream,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, PilotColors.CreamEdge),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatDateTime(s.finishedAt ?: s.startedAt), style = MaterialTheme.typography.titleMedium)
            Text("${s.type.label} · ${s.difficulty.label}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${summary.answered} answered · " +
                    (summary.accuracyPercent?.let { "$it% accuracy" } ?: "No answers recorded") +
                    " · ${s.status.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = PilotColors.NavySoft,
            )
        }
    }
}

@Composable
fun BadgesScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val unlocked = state.badges.associateBy { it.badge }
    PilotScaffold(title = "Badges", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "Badges celebrate practising. They never depend on speed or perfect scores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PilotColors.NavySoft,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
            items(Badge.entries) { badge ->
                BadgeRow(badge, unlocked[badge], Modifier.widthIn(max = 640.dp).fillMaxWidth())
            }
            item {
                InstrumentPanel(title = "Practice so far", modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    StatRow("Mission questions answered", state.totals.originalAnswered.toString())
                    StatRow("Missions finished", state.totals.completedSessions.toString())
                    StatRow("Mistakes fixed", state.totals.resolvedMistakes.toString())
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, unlocked: UnlockedBadge?, modifier: Modifier) {
    val earned = unlocked != null
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = if (earned) "Unlocked" else "Not unlocked yet"
        },
        color = if (earned) PilotColors.YellowSoft else PilotColors.Cream,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (earned) PilotColors.Yellow else PilotColors.CreamEdge),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (earned) PilotColors.Teal else PilotColors.CreamEdge,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(badge.title, style = MaterialTheme.typography.titleMedium)
                Text(badge.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (unlocked != null) "Unlocked ${formatDate(unlocked.unlockedAt)}" else "Not yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = PilotColors.NavySoft,
                )
            }
        }
    }
}
