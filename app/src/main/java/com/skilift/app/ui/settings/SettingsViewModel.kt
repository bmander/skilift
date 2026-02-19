package com.skilift.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skilift.app.data.repository.PreferencesRepository
import com.skilift.app.domain.model.CyclingOptimization
import com.skilift.app.domain.model.TripPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _preferences = MutableStateFlow(TripPreferences())
    val preferences: StateFlow<TripPreferences> = _preferences.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _preferences.value = prefs
            }
        }
    }

    fun updateBikeTransitBalance(value: Float) {
        _preferences.update { it.copy(bikeTransitBalance = value) }
        viewModelScope.launch {
            preferencesRepository.updateBikeTransitBalance(value)
        }
    }

    fun updateCyclingOptimization(value: CyclingOptimization) {
        _preferences.update { it.copy(cyclingOptimization = value) }
        viewModelScope.launch {
            preferencesRepository.updateCyclingOptimization(value)
        }
    }

    fun updateMaxBikeSpeed(value: Float) {
        _preferences.update { it.copy(maxBikeSpeedMps = value) }
        viewModelScope.launch {
            preferencesRepository.updateMaxBikeSpeed(value)
        }
    }
}
