/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shellpilot.app.ui.screens.profiles

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.shellpilot.app.data.ColorSchemeRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )
    private lateinit var profileRepository: ProfileRepository
    private lateinit var colorSchemeRepository: ColorSchemeRepository
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        profileRepository = mock()
        colorSchemeRepository = mock()
        prefs = mock()

        whenever(profileRepository.observeAll()).thenReturn(flowOf(emptyList()))
        whenever(profileRepository.getById(any())).thenReturn(null)
        whenever(colorSchemeRepository.getAllSchemes()).thenReturn(emptyList())
        whenever(prefs.getString(eq("customFonts"), any())).thenReturn("")
        whenever(prefs.getString(eq("customTerminalTypes"), any())).thenReturn("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createProfile_TrimsNameBeforeDuplicateCheckAndCreate() = runTest {
        whenever(profileRepository.nameExists("Work", null)).thenReturn(false)
        val viewModel = ProfileListViewModel(profileRepository)
        advanceUntilIdle()

        viewModel.createProfile("  Work  ")
        advanceUntilIdle()

        verify(profileRepository).nameExists("Work")
        verify(profileRepository).create("Work")
        assertFalse(viewModel.uiState.value.showCreateDialog)
        assertEquals(null, viewModel.uiState.value.createError)
    }

    @Test
    fun save_TrimsNameBeforeDuplicateCheckAndPersist() = runTest {
        whenever(profileRepository.nameExists("Trimmed", null)).thenReturn(false)
        val viewModel = createEditorViewModel()
        advanceUntilIdle()

        viewModel.updateName("  Trimmed  ")
        viewModel.save {}
        advanceUntilIdle()

        val profileCaptor = argumentCaptor<Profile>()
        verify(profileRepository).nameExists("Trimmed", null)
        verify(profileRepository).save(profileCaptor.capture())
        assertEquals("Trimmed", profileCaptor.firstValue.name)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(null, viewModel.uiState.value.saveError)
    }

    @Test
    fun save_WhenRepositoryFails_ClearsSavingAndShowsError() = runTest {
        whenever(profileRepository.nameExists("Work", null)).thenReturn(false)
        whenever(profileRepository.save(any())).thenThrow(RuntimeException("db down"))
        val viewModel = createEditorViewModel()
        advanceUntilIdle()

        viewModel.updateName("Work")
        viewModel.save { error("保存失敗時に成功コールバックを呼んではいけません") }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("db down", viewModel.uiState.value.saveError)
    }

    @Test
    fun save_WhenTrimmedNameIsBlank_DoesNotEnterSaving() = runTest {
        val viewModel = createEditorViewModel()
        advanceUntilIdle()

        viewModel.updateName("   ")
        viewModel.save {}
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("プロファイル名を入力してください", viewModel.uiState.value.saveError)
    }

    @Test
    fun save_WhenTrimmedNameDuplicates_ClearsSavingAndShowsError() = runTest {
        whenever(profileRepository.nameExists("Work", null)).thenReturn(true)
        val viewModel = createEditorViewModel()
        advanceUntilIdle()

        viewModel.updateName("  Work  ")
        viewModel.save {}
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("同じ名前のプロファイルがすでにあります", viewModel.uiState.value.saveError)
    }

    private fun createEditorViewModel(profileId: Long = -1L): ProfileEditorViewModel {
        return ProfileEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("profileId" to profileId)),
            profileRepository = profileRepository,
            colorSchemeRepository = colorSchemeRepository,
            prefs = prefs,
            context = RuntimeEnvironment.getApplication(),
            dispatchers = dispatchers
        )
    }
}
