package com.skilift.app.data.repository

import com.skilift.app.data.local.UserPreferencesDataStore
import com.skilift.app.domain.model.CyclingOptimization
import com.skilift.app.domain.model.TripPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PreferencesRepository @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) {
    val preferences: Flow<TripPreferences> = dataStore.preferences

    suspend fun updateBikeTransitBalance(value: Float) =
        dataStore.updateBikeTransitBalance(value)

    suspend fun updateCyclingOptimization(value: CyclingOptimization) =
        dataStore.updateCyclingOptimization(value)

    suspend fun updateMaxBikeSpeed(value: Float) =
        dataStore.updateMaxBikeSpeed(value)
}
