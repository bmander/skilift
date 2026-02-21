package com.skilift.app.ui.settings

import com.skilift.app.data.repository.PreferencesRepository
import com.skilift.app.domain.model.TripPreferences
import com.skilift.app.ui.map.components.TriangleWeights
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = mockk(relaxed = true)
        every { preferencesRepository.preferences } returns flowOf(TripPreferences())
        viewModel = SettingsViewModel(preferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial preferences are loaded from repository`() = runTest {
        val prefs = TripPreferences(bikeTransitBalance = 0.7f, maxBikeSpeedMps = 8.0f)
        every { preferencesRepository.preferences } returns flowOf(prefs)
        val vm = SettingsViewModel(preferencesRepository)
        advanceUntilIdle()

        assertEquals(0.7f, vm.preferences.value.bikeTransitBalance, 0.001f)
        assertEquals(8.0f, vm.preferences.value.maxBikeSpeedMps, 0.001f)
    }

    @Test
    fun `updateBikeTransitBalance updates state and calls repository`() = runTest {
        advanceUntilIdle()

        viewModel.updateBikeTransitBalance(0.8f)
        assertEquals(0.8f, viewModel.preferences.value.bikeTransitBalance, 0.001f)

        advanceUntilIdle()
        coVerify { preferencesRepository.updateBikeTransitBalance(0.8f) }
    }

    @Test
    fun `updateTriangleWeights updates state`() = runTest {
        advanceUntilIdle()

        val weights = TriangleWeights(time = 0.5f, safety = 0.3f, flatness = 0.2f)
        viewModel.updateTriangleWeights(weights)

        assertEquals(0.5f, viewModel.preferences.value.triangleTimeFactor, 0.001f)
        assertEquals(0.3f, viewModel.preferences.value.triangleSafetyFactor, 0.001f)
        assertEquals(0.2f, viewModel.preferences.value.triangleFlatnessFactor, 0.001f)
    }

    @Test
    fun `saveTriangleWeights calls repository with current values`() = runTest {
        advanceUntilIdle()

        val weights = TriangleWeights(time = 0.5f, safety = 0.3f, flatness = 0.2f)
        viewModel.updateTriangleWeights(weights)
        viewModel.saveTriangleWeights()

        advanceUntilIdle()
        coVerify {
            preferencesRepository.updateTriangleFactors(
                time = 0.5f,
                safety = 0.3f,
                flatness = 0.2f
            )
        }
    }

    @Test
    fun `updateMaxBikeSpeed updates state and calls repository`() = runTest {
        advanceUntilIdle()

        viewModel.updateMaxBikeSpeed(7.0f)
        assertEquals(7.0f, viewModel.preferences.value.maxBikeSpeedMps, 0.001f)

        advanceUntilIdle()
        coVerify { preferencesRepository.updateMaxBikeSpeed(7.0f) }
    }

    @Test
    fun `updateHillReluctance updates state without saving`() = runTest {
        advanceUntilIdle()

        viewModel.updateHillReluctance(3.0f)
        assertEquals(3.0f, viewModel.preferences.value.hillReluctance, 0.001f)

        advanceUntilIdle()
        coVerify(exactly = 0) { preferencesRepository.updateHillReluctance(any()) }
    }

    @Test
    fun `saveHillReluctance calls repository with current value`() = runTest {
        advanceUntilIdle()

        viewModel.updateHillReluctance(3.0f)
        viewModel.saveHillReluctance()

        advanceUntilIdle()
        coVerify { preferencesRepository.updateHillReluctance(3.0f) }
    }
}
