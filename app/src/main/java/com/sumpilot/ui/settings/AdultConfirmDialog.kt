package com.sumpilot.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sumpilot.ui.theme.PilotColors
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Grown-up check before destructive actions. It only guards against accidental taps by a child;
 * it is not authentication. Two ways to confirm:
 *  1. type the answer to a two-digit addition, or
 *  2. press and hold the confirm button for three seconds (screen readers: long-press action).
 */
@Composable
fun AdultConfirmDialog(
    title: String,
    explanation: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val a by rememberSaveable { mutableStateOf(Random.nextInt(21, 59)) }
    val b by rememberSaveable { mutableStateOf(Random.nextInt(21, 39)) }
    var input by rememberSaveable { mutableStateOf("") }
    val correct = input.trim().toIntOrNull() == a + b

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PilotColors.Cream,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation)
                Text("Grown-up check", style = MaterialTheme.typography.titleSmall)
                Text("What is $a + $b?", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = input,
                    onValueChange = { v -> input = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Answer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Or press and hold the button below for 3 seconds.", style = MaterialTheme.typography.bodySmall)
                HoldToConfirmButton(onConfirmed = onConfirm)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = correct, modifier = Modifier.heightIn(min = 48.dp)) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
private fun HoldToConfirmButton(onConfirmed: () -> Unit, holdMillis: Long = 3_000L) {
    var holding by rememberSaveable { mutableStateOf(false) }
    var progress by rememberSaveable { mutableFloatStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        val steps = 30
        for (i in 1..steps) {
            delay(holdMillis / steps)
            progress = i / steps.toFloat()
        }
        holding = false
        onConfirmed()
    }

    Surface(
        color = PilotColors.YellowSoft,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Press and hold to confirm"
                // Screen readers: double-tap and hold triggers this long-press action.
                onLongClick(label = "Confirm") { onConfirmed(); true }
                onClick(label = "Hold to confirm") { false }
            },
    ) {
        Column(Modifier.heightIn(min = 56.dp), verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (holding) "Keep holding…" else "Press and hold to confirm", style = MaterialTheme.typography.labelLarge)
            }
            if (holding) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
