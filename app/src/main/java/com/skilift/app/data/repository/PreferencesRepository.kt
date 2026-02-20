package com.skilift.app.data.repository

import com.skilift.app.data.local.UserPreferencesDataStore
import com.skilift.app.domain.model.TripPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PreferencesRepository @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) {
    val preferences: Flow<TripPreferences> = dataStore.preferences

    suspend fun updateBikeTransitBalance(value: Float) =
        dataStore.updateBikeTransitBalance(value)

    suspend fun updateTriangleFactors(time: Float, safety: Float, flatness: Float) =
        dataStore.updateTriangleFactors(time, safety, flatness)

    suspend fun updateMaxBikeSpeed(value: Float) =
        dataStore.updateMaxBikeSpeed(value)

    suspend fun updateHillReluctance(value: Float) =
        dataStore.updateHillReluctance(value)
}
