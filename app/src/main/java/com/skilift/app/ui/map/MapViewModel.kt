package com.skilift.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skilift.app.data.SelectedItineraryHolder
import com.skilift.app.data.repository.PreferencesRepository
import com.skilift.app.data.repository.TripRepository
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.TripPreferences
import com.skilift.app.ui.map.components.TriangleWeights
import com.skilift.app.BuildConfig
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
    val triangleWeights: TriangleWeights = TriangleWeights(0.3f, 0.4f, 0.3f),
    val maxBikeSpeedMps: Float = 5.0f,
    val hillReluctance: Float = 1.0f,
    val longPressPoint: LatLng? = null,
    val showContextMenu: Boolean = false,
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
                    bikeTransitBalance = prefs.bikeTransitBalance,
                    maxBikeSpeedMps = prefs.maxBikeSpeedMps,
                    hillReluctance = prefs.hillReluctance,
                    triangleWeights = TriangleWeights(
                        time = prefs.triangleTimeFactor,
                        safety = prefs.triangleSafetyFactor,
                        flatness = prefs.triangleFlatnessFactor
                    )
                ) }
            }
        }
    }

    fun onMapLongPress(latLng: LatLng) {
        _uiState.update { it.copy(longPressPoint = latLng, showContextMenu = true) }
    }

    fun setOriginFromMenu() {
        val point = _uiState.value.longPressPoint ?: return
        val warning = if (!isInCoverageArea(point))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        _uiState.update { it.copy(origin = point, showContextMenu = false, longPressPoint = null, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun setDestinationFromMenu() {
        val point = _uiState.value.longPressPoint ?: return
        val warning = if (!isInCoverageArea(point))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        _uiState.update { it.copy(destination = point, showContextMenu = false, longPressPoint = null, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, longPressPoint = null) }
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

    fun onTriangleWeightsChanged(weights: TriangleWeights) {
        _uiState.update { it.copy(triangleWeights = weights) }
    }

    fun onTriangleWeightsChangeFinished() {
        val weights = _uiState.value.triangleWeights
        viewModelScope.launch {
            preferencesRepository.updateTriangleFactors(
                time = weights.time,
                safety = weights.safety,
                flatness = weights.flatness
            )
        }
        searchTrips()
    }

    fun onMaxBikeSpeedChanged(value: Float) {
        _uiState.update { it.copy(maxBikeSpeedMps = value) }
    }

    fun onMaxBikeSpeedChangeFinished() {
        viewModelScope.launch {
            preferencesRepository.updateMaxBikeSpeed(_uiState.value.maxBikeSpeedMps)
        }
        searchTrips()
    }

    fun onHillReluctanceChanged(value: Float) {
        _uiState.update { it.copy(hillReluctance = value) }
    }

    fun onHillReluctanceChangeFinished() {
        viewModelScope.launch {
            preferencesRepository.updateHillReluctance(_uiState.value.hillReluctance)
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
                error = null
            )
        }
    }

    private fun searchTrips() {
        val origin = _uiState.value.origin ?: return
        val destination = _uiState.value.destination ?: return
        val balance = _uiState.value.bikeTransitBalance
        val weights = _uiState.value.triangleWeights

        val (reluctance, boardCost) = mapSliderToOtpPreferences(balance)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            tripRepository.planTrip(
                origin = origin,
                destination = destination,
                bicycleReluctance = reluctance,
                bicycleBoardCost = boardCost,
                bicycleSpeed = _uiState.value.maxBikeSpeedMps.toDouble(),
                triangleTimeFactor = weights.time.toDouble(),
                triangleSafetyFactor = weights.safety.toDouble(),
                triangleFlatnessFactor = weights.flatness.toDouble(),
                hillReluctance = _uiState.value.hillReluctance.toDouble()
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
        // Coverage area from gradle.properties REGION_BBOX_* values
        val COVERAGE_NORTH = BuildConfig.REGION_BBOX_NORTH
        val COVERAGE_SOUTH = BuildConfig.REGION_BBOX_SOUTH
        val COVERAGE_EAST = BuildConfig.REGION_BBOX_EAST
        val COVERAGE_WEST = BuildConfig.REGION_BBOX_WEST

        val CENTER_LATITUDE = (COVERAGE_SOUTH + COVERAGE_NORTH) / 2.0
        val CENTER_LONGITUDE = (COVERAGE_WEST + COVERAGE_EAST) / 2.0

        fun isInCoverageArea(latLng: LatLng): Boolean =
            latLng.latitude in COVERAGE_SOUTH..COVERAGE_NORTH &&
                latLng.longitude in COVERAGE_WEST..COVERAGE_EAST

        fun mapSliderToOtpPreferences(balance: Float): Pair<Double, Int> {
            val reluctance = 5.0 - (4.5 * balance)
            val boardCost = (1200 - (1140 * balance)).toInt()
            return reluctance to boardCost
        }
    }
}
