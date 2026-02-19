package com.skilift.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skilift.app.domain.model.CyclingOptimization
import com.skilift.app.domain.model.TripPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val BIKE_TRANSIT_BALANCE = floatPreferencesKey("bike_transit_balance")
        val CYCLING_OPTIMIZATION = stringPreferencesKey("cycling_optimization")
        val MAX_BIKE_SPEED = floatPreferencesKey("max_bike_speed")
    }

    val preferences: Flow<TripPreferences> = context.dataStore.data.map { prefs ->
        TripPreferences(
            bikeTransitBalance = prefs[Keys.BIKE_TRANSIT_BALANCE] ?: 0.5f,
            cyclingOptimization = prefs[Keys.CYCLING_OPTIMIZATION]
                ?.let { CyclingOptimization.valueOf(it) }
                ?: CyclingOptimization.SAFE_STREETS,
            maxBikeSpeedMps = prefs[Keys.MAX_BIKE_SPEED] ?: 5.0f
        )
    }

    suspend fun updateBikeTransitBalance(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIKE_TRANSIT_BALANCE] = value
        }
    }

    suspend fun updateCyclingOptimization(value: CyclingOptimization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CYCLING_OPTIMIZATION] = value.name
        }
    }

    suspend fun updateMaxBikeSpeed(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_BIKE_SPEED] = value
        }
    }
}
