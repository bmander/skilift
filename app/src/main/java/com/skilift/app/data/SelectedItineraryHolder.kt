package com.skilift.app.data

import com.skilift.app.domain.model.Itinerary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedItineraryHolder @Inject constructor() {
    var itinerary: Itinerary? = null
}
