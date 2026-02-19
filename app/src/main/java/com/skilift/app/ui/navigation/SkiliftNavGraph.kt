package com.skilift.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skilift.app.ui.map.MapScreen
import com.skilift.app.ui.settings.SettingsScreen
import com.skilift.app.ui.tripdetails.TripDetailsScreen

@Composable
fun SkiliftNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Map.route) {
        composable(Screen.Map.route) {
            MapScreen(
                onItinerarySelected = { index ->
                    navController.navigate(Screen.TripDetails.createRoute(index))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.TripDetails.route,
            arguments = listOf(navArgument("itineraryIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val itineraryIndex = backStackEntry.arguments?.getInt("itineraryIndex") ?: 0
            TripDetailsScreen(
                itineraryIndex = itineraryIndex,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
