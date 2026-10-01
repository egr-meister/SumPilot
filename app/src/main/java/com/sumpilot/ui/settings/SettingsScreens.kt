package com.sumpilot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumpilot.domain.model.Difficulty
import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.model.SessionType
import com.sumpilot.ui.AppViewModels
import com.sumpilot.ui.common.ChoiceButton
import com.sumpilot.ui.common.ChoiceGroup
import com.sumpilot.ui.common.InstrumentPanel
import com.sumpilot.ui.common.PilotScaffold
import com.sumpilot.ui.common.SecondaryButton
import com.sumpilot.ui.theme.PilotColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<ClearAction?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    PilotScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InstrumentPanel(title = "New missions", modifier = Modifier.fillMaxWidth()) {
                    Text("Changes apply to new missions. A mission in progress keeps its settings.",
                        style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
                    Text("Default mission type", style = MaterialTheme.typography.titleSmall)
                    ChoiceGroup {
                        SessionType.entries.forEach { t ->
                            ChoiceButton(
                                if (t == SessionType.TEN_QUESTIONS) "10 questions · no timer" else "5 minutes of answering time",
                                settings.defaultType == t,
                                { vm.setType(t) },
                            )
                        }
                    }
                    Text("Default difficulty", style = MaterialTheme.typography.titleSmall)
                    ChoiceGroup {
                        Difficulty.entries.forEach { d -> ChoiceButton(d.label, settings.defaultDifficulty == d, { vm.setDifficulty(d) }) }
                    }
                    Text("Enabled operations", style = MaterialTheme.typography.titleSmall)
                    val ops = settings.enabledOperations
                    ChoiceGroup {
                        ChoiceButton("Mixed", ops.size == Operation.entries.size, vm::selectMixed, role = Role.Checkbox)
                        Operation.entries.forEach { op ->
                            val on = op in ops
                            ChoiceButton(
                                "${op.symbol}  ${op.label}", on, { vm.toggleOperation(op) },
                                enabled = !(on && ops.size == 1), role = Role.Checkbox,
                            )
                        }
                    }
                }

                InstrumentPanel(title = "Sound and motion", modifier = Modifier.fillMaxWidth()) {
                    SwitchRow("Sound", "Short feedback sounds while the app is open. Follows your device volume.", settings.soundOn, vm::setSound)
                    SwitchRow("Reduce decorative animation", "Turns off fades and transitions.", settings.reducedMotion, vm::setReducedMotion)
                }

                InstrumentPanel(title = "Privacy", modifier = Modifier.fillMaxWidth()) {
                    Text("Everything stays on this device.", style = MaterialTheme.typography.bodyLarge)
                    SecondaryButton("Privacy information", onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth())
                }

                InstrumentPanel(title = "Data on this device", modifier = Modifier.fillMaxWidth()) {
                    Text("A grown-up check is shown before anything is removed.",
                        style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
                    ClearAction.entries.forEach { action ->
                        SecondaryButton(action.title, onClick = { pending = action }, modifier = Modifier.fillMaxWidth())
                    }
                }
                SnackbarHost(snackbar)
            }
        }
    }

    pending?.let { action ->
        AdultConfirmDialog(
            title = "${action.title}?",
            explanation = action.explanation,
            onConfirm = { pending = null; vm.clear(action) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PilotColors.NavySoft)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    PilotScaffold(title = "Privacy", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InstrumentPanel(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                PrivacyText.sections.forEach { (heading, body) ->
                    Text(heading, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Bundled privacy information (no network needed). */
object PrivacyText {
    val sections: List<Pair<String, String>> = listOf(
        "Everything stays on this device" to
            "SumPilot works completely offline. Calculations, missions, answers, mistakes, badges and settings are saved only " +
            "in the app's private storage on this device.",
        "No accounts, ads or tracking" to
            "SumPilot has no accounts, advertising, analytics, payments, cloud sync or external links. It does not ask for " +
            "your name or any other personal details.",
        "No internet access" to
            "The app does not request internet permission, so it cannot send anything anywhere.",
        "No special permissions" to
            "SumPilot does not use the camera, microphone, location or contacts.",
        "Backups" to
            "App data is excluded from cloud backup and device-to-device transfer, so it stays on this device.",
        "Removing data" to
            "A grown-up can remove calculator history, session history, mistakes, progress and badges, or everything at once, " +
            "from Settings. Uninstalling the app also removes all of its data.",
    )
}
