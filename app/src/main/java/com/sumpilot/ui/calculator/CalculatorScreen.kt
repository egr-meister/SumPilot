package com.sumpilot.ui.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.data.repository.HistoryEntry
import com.sumpilot.domain.calculator.CalcOperator
import com.sumpilot.domain.calculator.CalculatorState
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.ConfirmDialog
import com.sumpilot.ui.common.EmptyState
import com.sumpilot.ui.common.PilotIcons
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.format.formatDateTime
import com.sumpilot.ui.theme.PilotColors

@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    vm: CalculatorViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    // Back closes the history view first, then leaves the calculator.
    BackHandler(enabled = showHistory) { showHistory = false }

    PilotScaffold(
        title = if (showHistory) "Calculator history" else "Calculator",
        onBack = { if (showHistory) showHistory = false else onBack() },
        actions = {
            if (!showHistory) {
                IconButton(onClick = { showHistory = true }) {
                    Icon(PilotIcons.History, contentDescription = "History")
                }
            } else if (history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear calculator history")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showHistory) {
                HistoryList(history) { entry ->
                    vm.useResult(entry)
                    showHistory = false
                }
            } else {
                CalculatorBody(state, vm::press, onHistory = { showHistory = true })
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear calculator history?",
            text = "This removes saved calculations. Practice progress is not affected.",
            confirmLabel = "Clear",
            onConfirm = { confirmClear = false; vm.clearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun CalculatorBody(state: CalculatorState, onKey: (CalcKey) -> Unit, onHistory: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Display(state, Modifier.fillMaxWidth())
                    SecondaryButton("History", onClick = onHistory, icon = PilotIcons.History)
                }
                Keypad(onKey, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Display(state, Modifier.fillMaxWidth())
                Keypad(onKey, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun Display(state: CalculatorState, modifier: Modifier) {
    val expression: String
    val main: String
    val spoken: String
    when {
        state.error != null -> {
            expression = state.expressionText
            main = state.error
            spoken = state.error
        }
        state.isShowingResult -> {
            expression = "${state.resultExpression.orEmpty()} ="
            val prefix = if (state.resultRounded) "≈ " else ""
            main = prefix + CalculatorState.displayOperand(state.result.orEmpty())
            spoken = "${state.resultExpression.orEmpty().replace("×", "times").replace("÷", "divided by").replace("−", "minus")} equals " +
                (if (state.resultRounded) "approximately " else "") + CalculatorState.spokenOperand(state.result.orEmpty())
        }
        else -> {
            expression = ""
            main = state.expressionText.ifEmpty { "0" }
            spoken = state.spokenExpression.ifEmpty { "0" }
        }
    }
    Surface(
        modifier = modifier.heightIn(min = 140.dp),
        color = PilotColors.Navy,
        contentColor = PilotColors.Cream,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(3.dp, PilotColors.CreamEdge),
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                expression,
                style = MaterialTheme.typography.titleMedium,
                color = PilotColors.YellowSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                main,
                style = if (state.error != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
                color = if (state.error != null) PilotColors.YellowSoft else PilotColors.Cream,
                textAlign = TextAlign.End,
                maxLines = 3,
            )
            if (state.isShowingResult && state.resultRounded) {
                Text("Rounded to 6 decimal places", style = MaterialTheme.typography.labelMedium, color = PilotColors.YellowSoft)
            }
        }
    }
}

private data class KeySpec(
    val label: String,
    val spoken: String,
    val key: CalcKey,
    val kind: KeyKind = KeyKind.Digit,
    val icon: ImageVector? = null,
    val weight: Float = 1f,
)

private enum class KeyKind { Digit, Operator, Action, Equals }

private val keyRows: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("C", "clear", CalcKey.Clear, KeyKind.Action),
        KeySpec("⌫", "backspace", CalcKey.Backspace, KeyKind.Action, icon = PilotIcons.Backspace),
        KeySpec("±", "change sign", CalcKey.Sign, KeyKind.Action),
        KeySpec("÷", "divided by", CalcKey.Op(CalcOperator.DIVIDE), KeyKind.Operator),
    ),
    listOf(digit('7'), digit('8'), digit('9'), KeySpec("×", "times", CalcKey.Op(CalcOperator.MULTIPLY), KeyKind.Operator)),
    listOf(digit('4'), digit('5'), digit('6'), KeySpec("−", "minus", CalcKey.Op(CalcOperator.SUBTRACT), KeyKind.Operator)),
    listOf(digit('1'), digit('2'), digit('3'), KeySpec("+", "plus", CalcKey.Op(CalcOperator.ADD), KeyKind.Operator)),
    listOf(
        digit('0').copy(weight = 2f),
        KeySpec(".", "decimal point", CalcKey.Decimal),
        KeySpec("=", "equals", CalcKey.Equals, KeyKind.Equals),
    ),
)

private fun digit(c: Char) = KeySpec(c.toString(), c.toString(), CalcKey.Digit(c))

@Composable
private fun Keypad(onKey: (CalcKey) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec -> KeyButton(spec, onKey) }
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(spec: KeySpec, onKey: (CalcKey) -> Unit) {
    val (bg, fg) = when (spec.kind) {
        KeyKind.Digit -> PilotColors.White to PilotColors.Navy
        KeyKind.Operator -> PilotColors.TealSoft to PilotColors.Navy
        KeyKind.Action -> PilotColors.YellowSoft to PilotColors.Navy
        KeyKind.Equals -> PilotColors.Teal to PilotColors.White
    }
    Surface(
        onClick = { onKey(spec.key) },
        modifier = Modifier
            .weight(spec.weight)
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = spec.spoken },
        shape = MaterialTheme.shapes.medium,
        color = bg,
        contentColor = fg,
        border = BorderStroke(2.dp, if (spec.kind == KeyKind.Equals) PilotColors.Teal else PilotColors.CreamEdge),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (spec.icon != null) {
                Icon(spec.icon, contentDescription = null, tint = fg)
            } else {
                Text(spec.label, style = MaterialTheme.typography.headlineSmall, color = fg)
            }
        }
    }
}

@Composable
private fun HistoryList(history: List<HistoryEntry>, onUse: (HistoryEntry) -> Unit) {
    if (history.isEmpty()) {
        EmptyState("No calculations yet.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(history, key = { it.id }) { entry ->
            Surface(
                color = PilotColors.Cream,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(2.dp, PilotColors.CreamEdge),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        val expr = "${CalculatorState.displayOperand(entry.first)} ${entry.operator.symbol} ${CalculatorState.displayOperand(entry.second)}"
                        Text(expr, style = MaterialTheme.typography.bodyLarge, color = PilotColors.NavySoft)
                        Text(
                            "= ${if (entry.rounded) "≈ " else ""}${CalculatorState.displayOperand(entry.result)}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(formatDateTime(entry.createdAt), style = MaterialTheme.typography.labelMedium, color = PilotColors.NavySoft)
                    }
                    TextButton(onClick = { onUse(entry) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use result") }
                }
            }
        }
    }
}
