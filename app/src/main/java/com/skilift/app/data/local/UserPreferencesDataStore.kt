package com.skilift.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val TRIANGLE_TIME_FACTOR = floatPreferencesKey("triangle_time_factor")
        val TRIANGLE_SAFETY_FACTOR = floatPreferencesKey("triangle_safety_factor")
        val TRIANGLE_FLATNESS_FACTOR = floatPreferencesKey("triangle_flatness_factor")
        val MAX_BIKE_SPEED = floatPreferencesKey("max_bike_speed")
    }

    val preferences: Flow<TripPreferences> = context.dataStore.data.map { prefs ->
        TripPreferences(
            bikeTransitBalance = prefs[Keys.BIKE_TRANSIT_BALANCE] ?: 0.5f,
            triangleTimeFactor = prefs[Keys.TRIANGLE_TIME_FACTOR] ?: 0.3f,
            triangleSafetyFactor = prefs[Keys.TRIANGLE_SAFETY_FACTOR] ?: 0.4f,
            triangleFlatnessFactor = prefs[Keys.TRIANGLE_FLATNESS_FACTOR] ?: 0.3f,
            maxBikeSpeedMps = prefs[Keys.MAX_BIKE_SPEED] ?: 5.0f
        )
    }

    suspend fun updateBikeTransitBalance(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIKE_TRANSIT_BALANCE] = value
        }
    }

    suspend fun updateTriangleFactors(time: Float, safety: Float, flatness: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRIANGLE_TIME_FACTOR] = time
            prefs[Keys.TRIANGLE_SAFETY_FACTOR] = safety
            prefs[Keys.TRIANGLE_FLATNESS_FACTOR] = flatness
        }
    }

    suspend fun updateMaxBikeSpeed(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_BIKE_SPEED] = value
        }
    }
}
