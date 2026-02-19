package com.skilift.app.ui.tripdetails

import androidx.lifecycle.ViewModel
import com.skilift.app.domain.model.Itinerary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor() : ViewModel() {

    private val _itinerary = MutableStateFlow<Itinerary?>(null)
    val itinerary: StateFlow<Itinerary?> = _itinerary.asStateFlow()

    fun setItinerary(itinerary: Itinerary) {
        _itinerary.value = itinerary
    }
}
