package com.skilift.app.ui.map

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skilift.app.data.SelectedItineraryHolder
import com.skilift.app.data.repository.PreferencesRepository
import com.skilift.app.data.repository.TripRepository
import com.skilift.app.domain.model.GeocodingResult
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.LatLng
import com.skilift.app.domain.model.TimeSelection
import com.skilift.app.domain.model.TripPreferences
import com.skilift.app.ui.map.components.TriangleWeights
import com.skilift.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SearchTarget { ORIGIN, DESTINATION }

data class MapUiState(
    val origin: LatLng? = null,
    val destination: LatLng? = null,
    val originName: String? = null,
    val destinationName: String? = null,
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
    val preferences: TripPreferences = TripPreferences(),
    val userLocation: LatLng? = null,
    val originIsCurrentLocation: Boolean = false,
    val destinationIsCurrentLocation: Boolean = false,
    val isPuckMenu: Boolean = false,
    val timeSelection: TimeSelection = TimeSelection.DepartNow,
    val showTimePicker: Boolean = false,
    val selectedLegIndex: Int? = null,
    val showGeocoderSearch: Boolean = false,
    val geocoderSearchTarget: SearchTarget? = null,
    val geocoderQuery: String = "",
    val geocoderResults: List<GeocodingResult> = emptyList(),
    val isGeocoderLoading: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val preferencesRepository: PreferencesRepository,
    private val selectedItineraryHolder: SelectedItineraryHolder,
    application: Application
) : ViewModel() {

    private val geocoder = Geocoder(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var geocoderJob: Job? = null

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
        _uiState.update { it.copy(origin = point, originName = null, originIsCurrentLocation = false, showContextMenu = false, longPressPoint = null, isPuckMenu = false, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun setDestinationFromMenu() {
        val point = _uiState.value.longPressPoint ?: return
        val warning = if (!isInCoverageArea(point))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        _uiState.update { it.copy(destination = point, destinationName = null, destinationIsCurrentLocation = false, showContextMenu = false, longPressPoint = null, isPuckMenu = false, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, longPressPoint = null, isPuckMenu = false) }
    }

    fun onUserLocationChanged(latLng: LatLng) {
        _uiState.update { state ->
            state.copy(
                userLocation = latLng,
                origin = if (state.originIsCurrentLocation) latLng else state.origin,
                destination = if (state.destinationIsCurrentLocation) latLng else state.destination
            )
        }
    }

    fun onPuckTapped() {
        val location = _uiState.value.userLocation ?: return
        _uiState.update { it.copy(longPressPoint = location, showContextMenu = true, isPuckMenu = true) }
    }

    fun setOriginToCurrentLocation() {
        val location = _uiState.value.userLocation ?: return
        val warning = if (!isInCoverageArea(location))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        _uiState.update { it.copy(origin = location, originName = null, originIsCurrentLocation = true, showContextMenu = false, longPressPoint = null, isPuckMenu = false, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun setDestinationToCurrentLocation() {
        val location = _uiState.value.userLocation ?: return
        val warning = if (!isInCoverageArea(location))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        _uiState.update { it.copy(destination = location, destinationName = null, destinationIsCurrentLocation = true, showContextMenu = false, longPressPoint = null, isPuckMenu = false, error = warning) }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
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
        _uiState.update { it.copy(selectedItineraryIndex = index, selectedLegIndex = null) }
    }

    fun selectLeg(index: Int?) {
        _uiState.update { it.copy(selectedLegIndex = index) }
    }

    fun prepareForDetails(index: Int) {
        selectedItineraryHolder.itinerary = _uiState.value.itineraries.getOrNull(index)
    }

    fun clearOrigin() {
        _uiState.update {
            it.copy(
                origin = null,
                originName = null,
                originIsCurrentLocation = false,
                itineraries = emptyList(),
                selectedItineraryIndex = 0,
                error = null
            )
        }
    }

    fun clearDestination() {
        _uiState.update {
            it.copy(
                destination = null,
                destinationName = null,
                destinationIsCurrentLocation = false,
                itineraries = emptyList(),
                selectedItineraryIndex = 0,
                error = null
            )
        }
    }

    fun onTimeRowClicked() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun onTimePickerDismissed() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun onTimeSelectionConfirmed(selection: TimeSelection) {
        _uiState.update { it.copy(timeSelection = selection, showTimePicker = false) }
        searchTrips()
    }

    fun clearPoints() {
        _uiState.update {
            it.copy(
                origin = null,
                destination = null,
                originName = null,
                destinationName = null,
                originIsCurrentLocation = false,
                destinationIsCurrentLocation = false,
                itineraries = emptyList(),
                selectedItineraryIndex = 0,
                error = null
            )
        }
    }

    fun onLocationRowClicked(target: SearchTarget) {
        _uiState.update {
            it.copy(
                showGeocoderSearch = true,
                geocoderSearchTarget = target,
                geocoderQuery = "",
                geocoderResults = emptyList(),
                isGeocoderLoading = false
            )
        }
    }

    fun onGeocoderQueryChanged(query: String) {
        _uiState.update { it.copy(geocoderQuery = query) }
        geocoderJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(geocoderResults = emptyList(), isGeocoderLoading = false) }
            return
        }
        geocoderJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isGeocoderLoading = true) }
            val results = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5,
                        COVERAGE_SOUTH, COVERAGE_WEST, COVERAGE_NORTH, COVERAGE_EAST
                    )?.map { address ->
                        val featureName = address.featureName
                        val displayName = if (featureName != null && featureName.toDoubleOrNull() == null) {
                            featureName
                        } else {
                            query
                        }
                        GeocodingResult(
                            name = displayName,
                            address = address.getAddressLine(0),
                            location = LatLng(address.latitude, address.longitude)
                        )
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            _uiState.update { it.copy(geocoderResults = results, isGeocoderLoading = false) }
        }
    }

    fun onGeocoderResultSelected(result: GeocodingResult) {
        val target = _uiState.value.geocoderSearchTarget ?: return
        val warning = if (!isInCoverageArea(result.location))
            "This point is outside the data coverage area. Routing may be unavailable."
        else null
        when (target) {
            SearchTarget.ORIGIN -> _uiState.update {
                it.copy(
                    origin = result.location,
                    originName = result.name,
                    originIsCurrentLocation = false,
                    showGeocoderSearch = false,
                    geocoderSearchTarget = null,
                    error = warning
                )
            }
            SearchTarget.DESTINATION -> _uiState.update {
                it.copy(
                    destination = result.location,
                    destinationName = result.name,
                    destinationIsCurrentLocation = false,
                    showGeocoderSearch = false,
                    geocoderSearchTarget = null,
                    error = warning
                )
            }
        }
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    fun onCurrentLocationSelectedFromSearch() {
        val target = _uiState.value.geocoderSearchTarget ?: return
        _uiState.update {
            it.copy(
                showGeocoderSearch = false,
                geocoderSearchTarget = null,
                geocoderQuery = "",
                geocoderResults = emptyList(),
                isGeocoderLoading = false
            )
        }
        geocoderJob?.cancel()
        when (target) {
            SearchTarget.ORIGIN -> setOriginToCurrentLocation()
            SearchTarget.DESTINATION -> setDestinationToCurrentLocation()
        }
    }

    fun onGeocoderSearchDismissed() {
        geocoderJob?.cancel()
        _uiState.update {
            it.copy(
                showGeocoderSearch = false,
                geocoderSearchTarget = null,
                geocoderQuery = "",
                geocoderResults = emptyList(),
                isGeocoderLoading = false
            )
        }
    }

    private fun searchTrips() {
        val origin = _uiState.value.origin ?: return
        val destination = _uiState.value.destination ?: return
        val balance = _uiState.value.bikeTransitBalance
        val weights = _uiState.value.triangleWeights
        val timeSelection = _uiState.value.timeSelection

        val (reluctance, boardCost) = mapSliderToOtpPreferences(balance)

        val (dateTime, arriveBy) = when (timeSelection) {
            is TimeSelection.DepartNow -> null to false
            is TimeSelection.DepartAt -> formatEpochMillis(timeSelection.epochMillis) to false
            is TimeSelection.ArriveBy -> formatEpochMillis(timeSelection.epochMillis) to true
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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
                hillReluctance = _uiState.value.hillReluctance.toDouble(),
                dateTime = dateTime,
                arriveBy = arriveBy
            ).onSuccess { itineraries ->
                _uiState.update {
                    it.copy(
                        itineraries = itineraries,
                        selectedItineraryIndex = 0,
                        selectedLegIndex = null,
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

        fun formatEpochMillis(epochMillis: Long): String {
            val odt = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault()
            )
            return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        fun mapSliderToOtpPreferences(balance: Float): Pair<Double, Int> {
            val reluctance = 5.0 - (4.5 * balance)
            val boardCost = (1200 - (1140 * balance)).toInt()
            return reluctance to boardCost
        }
    }
}
