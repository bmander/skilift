package com.skilift.app.ui.navigation

sealed class Screen(val route: String) {
    data object Map : Screen("map")
    data object TripDetails : Screen("trip_details/{itineraryIndex}") {
        fun createRoute(itineraryIndex: Int) = "trip_details/$itineraryIndex"
    }
}
