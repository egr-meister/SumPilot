package com.sumpilot.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.domain.calculator.CalculatorState
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionType
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.ChoiceButton
import com.sumpilot.ui.common.ChoiceGroup
import com.sumpilot.ui.common.ConfirmDialog
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotIcons
import com.sumpilot.ui.common.PrimaryButton
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.format.formatDateTime
import com.sumpilot.ui.theme.PilotColors

@Composable
fun PanelScreen(
    onOpenCalculator: () -> Unit,
    onOpenMission: () -> Unit,
    onViewResult: (Long) -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: PanelViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(event) {
        when (val e = event) {
            PanelEvent.OpenMission -> { vm.consumeEvent(); onOpenMission() }
            is PanelEvent.ViewResult -> { vm.consumeEvent(); onViewResult(e.sessionId) }
            null -> Unit
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth >= 720.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PanelHeader()
                val calculator: @Composable (Modifier) -> Unit = { m -> CalculatorDisplayPanel(state, onOpenCalculator, m) }
                val mission: @Composable (Modifier) -> Unit = { m ->
                    MissionPanel(
                        state = state,
                        busy = busy,
                        onType = { vm.setType(it) },
                        onDifficulty = { vm.setDifficulty(it) },
                        onMixed = { vm.selectMixed() },
                        onToggle = { vm.toggleOperation(it) },
                        onStart = vm::startMission,
                        onResume = vm::resumeMission,
                        onEnd = { confirmEnd = true },
                        modifier = m,
                    )
                }
                val meter: @Composable (Modifier) -> Unit = { m -> AccuracyMeterPanel(state, m) }
                val result: @Composable (Modifier) -> Unit = { m -> SessionResultStrip(state, onViewResult, m) }

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        mission(Modifier.weight(1.15f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            calculator(Modifier.fillMaxWidth())
                            meter(Modifier.fillMaxWidth())
                        }
                    }
                    result(Modifier.fillMaxWidth())
                } else {
                    calculator(Modifier.fillMaxWidth())
                    mission(Modifier.fillMaxWidth())
                    meter(Modifier.fillMaxWidth())
                    result(Modifier.fillMaxWidth())
                }
                ControlsRow(
                    unresolved = state.unresolvedMistakes,
                    onOpenMistakes = onOpenMistakes,
                    onOpenHistory = onOpenHistory,
                    onOpenBadges = onOpenBadges,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "End the unfinished mission?",
            text = "Answered questions are kept in history. Questions you haven't answered won't count.",
            confirmLabel = "End mission",
            dismissLabel = "Keep it",
            onConfirm = { confirmEnd = false; vm.endUnfinished() },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun PanelHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(PilotIcons.Plane, contentDescription = null, tint = PilotColors.Teal, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("SumPilot", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("Pilot panel", style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
        }
    }
}

@Composable
private fun CalculatorDisplayPanel(state: PanelUiState, onOpen: () -> Unit, modifier: Modifier) {
    val calc = state.latestCalculation
    val summary = if (calc == null) {
        "Ready to calculate."
    } else {
        val expr = "${CalculatorState.displayOperand(calc.first)} ${calc.operator.symbol} ${CalculatorState.displayOperand(calc.second)}"
        "$expr = ${if (calc.rounded) "≈ " else ""}${CalculatorState.displayOperand(calc.result)}"
    }
    InstrumentPanel(modifier = modifier, title = "Calculator screen") {
        Surface(
            color = PilotColors.Navy,
            contentColor = PilotColors.Cream,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClickLabel = "Open calculator", onClick = onOpen)
                .semantics(mergeDescendants = true) {},
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.End) {
                Text(
                    if (calc == null) "" else "Latest calculation",
                    style = MaterialTheme.typography.labelMedium,
                    color = PilotColors.YellowSoft,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
        SecondaryButton("Open calculator", onClick = onOpen, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MissionPanel(
    state: PanelUiState,
    busy: Boolean,
    onType: (SessionType) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onMixed: () -> Unit,
    onToggle: (Operation) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier,
) {
    val unfinished = state.unfinished
    // While a mission is unfinished, show its saved configuration (read-only).
    val type = unfinished?.type ?: state.settings.defaultType
    val difficulty = unfinished?.difficulty ?: state.settings.defaultDifficulty
    val ops = unfinished?.operations ?: state.settings.enabledOperations
    val editable = unfinished == null

    InstrumentPanel(modifier = modifier, title = "Practice mission") {
        if (unfinished != null) {
            Surface(color = PilotColors.YellowSoft, shape = MaterialTheme.shapes.small) {
                Text(
                    "You have a mission in progress. Resume it or end it before starting a new one.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text("Session type", style = MaterialTheme.typography.titleSmall)
        ChoiceGroup {
            ChoiceButton("10 questions · no timer", type == SessionType.TEN_QUESTIONS, { onType(SessionType.TEN_QUESTIONS) }, enabled = editable)
            ChoiceButton("5 minutes of answering time", type == SessionType.FIVE_MINUTES, { onType(SessionType.FIVE_MINUTES) }, enabled = editable)
        }

        Text("Difficulty", style = MaterialTheme.typography.titleSmall)
        ChoiceGroup {
            Difficulty.entries.forEach { d ->
                ChoiceButton(d.label, difficulty == d, { onDifficulty(d) }, enabled = editable)
            }
        }

        Text("Operations", style = MaterialTheme.typography.titleSmall)
        val allOn = ops.size == Operation.entries.size
        ChoiceGroup {
            ChoiceButton("Mixed", allOn, onMixed, enabled = editable, role = Role.Checkbox)
            Operation.entries.forEach { op ->
                val on = op in ops
                ChoiceButton(
                    text = "${op.symbol}  ${op.label}",
                    selected = on,
                    onClick = { onToggle(op) },
                    // The last enabled operation cannot be switched off.
                    enabled = editable && !(on && ops.size == 1),
                    role = Role.Checkbox,
                )
            }
        }
        if (editable && ops.size == 1) {
            Text("At least one operation stays on.", style = MaterialTheme.typography.bodySmall, color = PilotColors.NavySoft)
        }

        Spacer(Modifier.size(4.dp))
        if (unfinished != null) {
            PrimaryButton("Resume mission", onClick = onResume, icon = Icons.Filled.PlayArrow, modifier = Modifier.fillMaxWidth())
            SecondaryButton("End mission", onClick = onEnd, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(
                "Start mission",
                onClick = onStart,
                enabled = state.loaded && !busy,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AccuracyMeterPanel(state: PanelUiState, modifier: Modifier) {
    val latest = state.latestSession
    val percent = latest?.accuracyPercent
    val description = if (latest == null || percent == null) {
        "Last session accuracy: no session yet."
    } else {
        "Last session accuracy: $percent percent. ${latest.correct} of ${latest.answered} correct."
    }
    InstrumentPanel(modifier = modifier, title = "Accuracy meter") {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AccuracyGauge(percent, Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(2f))
            Text("Last session accuracy", style = MaterialTheme.typography.titleSmall, color = PilotColors.NavySoft)
            if (latest == null || percent == null) {
                Text("No session yet", style = MaterialTheme.typography.headlineSmall)
            } else {
                Text("$percent%", style = MaterialTheme.typography.headlineMedium)
                Text("${latest.correct} of ${latest.answered} correct", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Restrained semicircle: track, value arc and three ticks. Draws no value when there is none. */
@Composable
private fun AccuracyGauge(percent: Int?, modifier: Modifier) {
    val track = PilotColors.CreamEdge
    val value = PilotColors.Teal
    val tick = PilotColors.NavySoft
    Canvas(modifier) {
        val stroke = size.height * 0.16f
        val diameter = minOf(size.width, size.height * 2f) - stroke
        val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (percent != null && percent > 0) {
            drawArc(value, 180f, 180f * percent / 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val center = Offset(size.width / 2f, topLeft.y + diameter / 2f)
        val r = diameter / 2f
        listOf(180.0, 270.0, 360.0).forEach { deg ->
            val rad = Math.toRadians(deg)
            val inner = r - stroke
            val outer = r - stroke * 0.55f
            drawLine(
                tick,
                Offset(center.x + (inner * kotlin.math.cos(rad)).toFloat(), center.y + (inner * kotlin.math.sin(rad)).toFloat()),
                Offset(center.x + (outer * kotlin.math.cos(rad)).toFloat(), center.y + (outer * kotlin.math.sin(rad)).toFloat()),
                strokeWidth = 3f,
            )
        }
    }
}

@Composable
private fun SessionResultStrip(state: PanelUiState, onView: (Long) -> Unit, modifier: Modifier) {
    val latest = state.latestSession
    InstrumentPanel(modifier = modifier, title = "Session result") {
        if (latest == null) {
            Text("No session yet. Your latest mission will appear here.", style = MaterialTheme.typography.bodyLarge)
        } else {
            FlightLogRow(
                items = listOf(
                    "Answered" to latest.answered.toString(),
                    "Correct" to latest.correct.toString(),
                    "Date" to formatDateTime(latest.session.finishedAt ?: latest.session.startedAt),
                ),
            )
            SecondaryButton("View result", onClick = { onView(latest.session.id) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlightLogRow(items: List<Pair<String, String>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (label, value) ->
            Surface(
                color = PilotColors.White,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, PilotColors.CreamEdge),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = PilotColors.NavySoft)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsRow(
    unresolved: Int,
    onOpenMistakes: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ControlButton(
            if (unresolved > 0) "Mistake practice ($unresolved)" else "Mistake practice",
            Icons.Filled.Refresh,
            onOpenMistakes,
        )
        ControlButton("Session history", PilotIcons.History, onOpenHistory)
        ControlButton("Badges", Icons.Filled.Star, onOpenBadges)
        ControlButton("Settings", Icons.Filled.Settings, onOpenSettings)
    }
}

@Composable
private fun ControlButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Box { SecondaryButton(text, onClick = onClick, icon = icon) }
}
