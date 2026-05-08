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

package io.shellpilot.app.ui.screens.hosteditor

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.util.SecurePasswordStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HostEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HostRepository
    private lateinit var pubkeyRepository: PubkeyRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var securePasswordStorage: SecurePasswordStorage

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        pubkeyRepository = mock()
        profileRepository = mock()
        prefs = mock()
        securePasswordStorage = mock()

        whenever(prefs.getLong(eq("defaultProfileId"), any())).thenReturn(0L)
        whenever(pubkeyRepository.getAll()).thenReturn(emptyList())
        whenever(profileRepository.getAll()).thenReturn(emptyList())
        whenever(repository.getSshHosts()).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun quickConnect_InvalidInput_BlocksSave() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQuickConnect("ssh://")
        viewModel.saveHost(useExpandedMode = false)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.quickConnectError)
        verify(repository, never()).saveHost(any())
    }

    @Test
    fun quickConnect_TelnetUri_SavesTelnetHost() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        whenever(repository.saveHost(any())).thenAnswer { invocation ->
            invocation.getArgument<Host>(0).copy(id = 3L)
        }

        viewModel.updateQuickConnect("telnet://example.com:2323/#router")
        viewModel.saveHost(useExpandedMode = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("telnet", state.protocol)
        assertEquals("example.com", state.hostname)
        assertEquals("2323", state.port)
        verify(securePasswordStorage, never()).savePassword(any(), any())
    }

    @Test
    fun saveHost_WhenChangingSshToTelnet_DeletesSavedPassword() = runTest {
        val existing = Host(
            id = 5L,
            nickname = "old",
            protocol = "ssh",
            username = "user",
            hostname = "old.example.com",
            port = 22
        )
        whenever(repository.findHostById(5L)).thenReturn(existing)
        whenever(repository.saveHost(any())).thenAnswer { invocation ->
            invocation.getArgument<Host>(0)
        }
        whenever(securePasswordStorage.hasPassword(5L)).thenReturn(true)

        val viewModel = createViewModel(hostId = 5L)
        advanceUntilIdle()

        viewModel.updateProtocol("telnet")
        viewModel.updateHostname("new.example.com")
        viewModel.updatePort("23")
        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        verify(securePasswordStorage).deletePassword(5L)
    }

    private fun createViewModel(hostId: Long = -1L): HostEditorViewModel {
        return HostEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("hostId" to hostId)),
            repository = repository,
            pubkeyRepository = pubkeyRepository,
            profileRepository = profileRepository,
            prefs = prefs,
            securePasswordStorage = securePasswordStorage
        )
    }
}
