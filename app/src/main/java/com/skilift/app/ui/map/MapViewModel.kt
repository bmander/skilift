package com.skilift.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skilift.app.data.SelectedItineraryHolder
import com.skilift.app.data.repository.PreferencesRepository
import com.skilift.app.data.repository.TripRepository
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.TripPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val origin: LatLng? = null,
    val destination: LatLng? = null,
    val itineraries: List<Itinerary> = emptyList(),
    val selectedItineraryIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val bikeTransitBalance: Float = 0.5f,
    val isSelectingOrigin: Boolean = true,
    val preferences: TripPreferences = TripPreferences()
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val preferencesRepository: PreferencesRepository,
    private val selectedItineraryHolder: SelectedItineraryHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(
                    preferences = prefs,
                    bikeTransitBalance = prefs.bikeTransitBalance
                ) }
            }
        }
    }

    fun onMapTap(latLng: LatLng) {
        if (_uiState.value.isSelectingOrigin) {
            _uiState.update { it.copy(origin = latLng, isSelectingOrigin = false, error = null) }
        } else {
            _uiState.update { it.copy(destination = latLng, isSelectingOrigin = true, error = null) }
            searchTrips()
        }
    }

    fun onSliderChanged(value: Float) {
        _uiState.update { it.copy(bikeTransitBalance = value) }
    }

    fun onSliderChangeFinished() {
        viewModelScope.launch {
            preferencesRepository.updateBikeTransitBalance(_uiState.value.bikeTransitBalance)
        }
        searchTrips()
    }

    fun selectItinerary(index: Int) {
        _uiState.update { it.copy(selectedItineraryIndex = index) }
    }

    fun prepareForDetails(index: Int) {
        selectedItineraryHolder.itinerary = _uiState.value.itineraries.getOrNull(index)
    }

    fun clearPoints() {
        _uiState.update {
            it.copy(
                origin = null,
                destination = null,
                itineraries = emptyList(),
                selectedItineraryIndex = 0,
                isSelectingOrigin = true,
                error = null
            )
        }
    }

    private fun searchTrips() {
        val origin = _uiState.value.origin ?: return
        val destination = _uiState.value.destination ?: return
        val balance = _uiState.value.bikeTransitBalance

        val (reluctance, boardCost) = mapSliderToOtpPreferences(balance)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            tripRepository.planTrip(
                origin = origin,
                destination = destination,
                bicycleReluctance = reluctance,
                bicycleBoardCost = boardCost,
                bicycleSpeed = _uiState.value.preferences.maxBikeSpeedMps.toDouble()
            ).onSuccess { itineraries ->
                _uiState.update {
                    it.copy(
                        itineraries = itineraries,
                        selectedItineraryIndex = 0,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to plan trip"
                    )
                }
            }
        }
    }

    companion object {
        fun mapSliderToOtpPreferences(balance: Float): Pair<Double, Int> {
            val reluctance = 5.0 - (4.5 * balance)
            val boardCost = (1200 - (1140 * balance)).toInt()
            return reluctance to boardCost
        }
    }
}
