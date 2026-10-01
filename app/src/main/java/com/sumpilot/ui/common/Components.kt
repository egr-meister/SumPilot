package com.sumpilot.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumpilot.ui.theme.PilotColors

/** Standard screen frame: top bar with Back, edge-to-edge insets handled by Scaffold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = PilotColors.Navy,
                ),
            )
        },
    ) { padding -> content(padding) }
}

/** Cream instrument panel with a soft rim. */
@Composable
fun InstrumentPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = PilotColors.Cream,
        border = BorderStroke(2.dp, PilotColors.CreamEdge),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) PanelLabel(title)
            content()
        }
    }
}

@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = PilotColors.NavySoft,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (enabled) PilotColors.Teal else PilotColors.CreamEdge),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PilotColors.Teal),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Single-choice option with a 48 dp+ target, radio semantics and a check mark (not color alone). */
@Composable
fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val selectedColors = selected && enabled
    val mod = if (role == Role.Checkbox) {
        Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
    }
    Surface(
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        color = when {
            selectedColors -> PilotColors.Teal
            selected -> PilotColors.SkyDeep
            else -> PilotColors.White
        },
        contentColor = if (selectedColors) PilotColors.White else PilotColors.Navy,
        border = BorderStroke(2.dp, if (selected) PilotColors.Teal else PilotColors.CreamEdge),
    ) {
        Row(
            modifier = mod.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = PilotColors.NavySoft)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) } },
        containerColor = PilotColors.Cream,
    )
}

/** Labeled key/value row used on result screens. */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = PilotColors.NavySoft, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = PilotColors.Navy)
    }
}
