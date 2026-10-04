package com.vaazhstudios.appgotchi.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.DemoData
import com.vaazhstudios.appgotchi.data.DemoMode
import com.vaazhstudios.appgotchi.data.StoreSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoStores : TodayUiState
    data class Loaded(val sections: List<StoreSection>, val isDemo: Boolean = false) : TodayUiState
}

class TodayViewModel(private val repository: AppsRepository, private val demoMode: DemoMode) : ViewModel() {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        // The StateFlow emits its current value first, so this also performs the initial load
        viewModelScope.launch { demoMode.enabled.collect { refresh() } }
        viewModelScope.launch {
            repository.credentialChanges.collect {
                // A real key ends the demo; turning it off triggers the reload above
                if (demoMode.enabled.value) demoMode.disable() else refresh()
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        if (demoMode.enabled.value) {
            _state.value = TodayUiState.Loaded(DemoData.sections, isDemo = true)
            return
        }
        _state.value = TodayUiState.Loading
        loadJob = viewModelScope.launch {
            val sections = repository.loadSections()
            _state.value = if (sections.isEmpty()) TodayUiState.NoStores else TodayUiState.Loaded(sections)
        }
    }

    fun tryDemo() = demoMode.enable()

    fun exitDemo() = demoMode.disable()
}
