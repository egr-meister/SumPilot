package com.sumpilot.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sumpilot.AppContainer
import com.sumpilot.SumPilotApplication
import com.sumpilot.ui.calculator.CalculatorViewModel
import com.sumpilot.ui.history.HistoryViewModel
import com.sumpilot.ui.mission.MissionViewModel
import com.sumpilot.ui.mistakes.MistakesViewModel
import com.sumpilot.ui.mistakes.RetryViewModel
import com.sumpilot.ui.panel.PanelViewModel
import com.sumpilot.ui.results.ResultsViewModel
import com.sumpilot.ui.settings.SettingsViewModel

/** Manual DI: one factory creating every ViewModel from the application's [AppContainer]. */
object AppViewModels {
    private fun androidx.lifecycle.viewmodel.CreationExtras.container(): AppContainer =
        (this[APPLICATION_KEY] as SumPilotApplication).container

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { PanelViewModel(container()) }
        initializer { CalculatorViewModel(container()) }
        initializer { MissionViewModel(container(), createSavedStateHandle()) }
        initializer { ResultsViewModel(container(), createSavedStateHandle()) }
        initializer { HistoryViewModel(container()) }
        initializer { MistakesViewModel(container(), createSavedStateHandle()) }
        initializer { RetryViewModel(container(), createSavedStateHandle()) }
        initializer { SettingsViewModel(container()) }
    }
}
