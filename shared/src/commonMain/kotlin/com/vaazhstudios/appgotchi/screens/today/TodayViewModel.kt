package com.vaazhstudios.appgotchi.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.StoreSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoStores : TodayUiState
    data class Loaded(val sections: List<StoreSection>) : TodayUiState
}

class TodayViewModel(private val repository: AppsRepository) : ViewModel() {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            repository.credentialChanges.collect { refresh() }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _state.value = TodayUiState.Loading
        loadJob = viewModelScope.launch {
            val sections = repository.loadSections()
            _state.value = if (sections.isEmpty()) TodayUiState.NoStores else TodayUiState.Loaded(sections)
        }
    }
}
