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
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.util.SecurePasswordStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun saveHost_WhenPasswordStorageFails_ShowsError() = runTest {
        whenever(repository.saveHost(any())).thenAnswer { invocation ->
            invocation.getArgument<Host>(0).copy(id = 9L)
        }
        whenever(securePasswordStorage.savePassword(eq(9L), eq("secret"))).thenReturn(false)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNickname("app")
        viewModel.updateUsername("user")
        viewModel.updateHostname("example.com")
        viewModel.updatePort("22")
        viewModel.updatePassword("secret")
        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        assertEquals("パスワードを安全に保存できませんでした。端末の認証情報ストレージを確認してください", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saveSucceeded)
        assertFalse(viewModel.uiState.value.isSaving)
        verify(repository).deleteHost(any())
    }

    @Test
    fun loadJumpHosts_ExcludesCandidatesThatPointBackToCurrentHost() = runTest {
        val current = Host(
            id = 1L,
            nickname = "app",
            hostname = "app.example.com",
            port = 22
        )
        val cyclicCandidate = Host(
            id = 2L,
            nickname = "jump-to-app",
            hostname = "jump.example.com",
            port = 22,
            jumpHostId = 1L
        )
        val validCandidate = Host(
            id = 3L,
            nickname = "bastion",
            hostname = "bastion.example.com",
            port = 22
        )
        whenever(repository.findHostById(1L)).thenReturn(current)
        whenever(repository.getSshHosts()).thenReturn(listOf(current, cyclicCandidate, validCandidate))

        val viewModel = createViewModel(hostId = 1L)
        advanceUntilIdle()

        val candidateIds = viewModel.uiState.value.availableJumpHosts.map { it.id }
        assertFalse(candidateIds.contains(1L))
        assertFalse(candidateIds.contains(2L))
        assertEquals(listOf(3L), candidateIds)
    }

    @Test
    fun saveHost_RejectsJumpHostCycle() = runTest {
        val current = Host(
            id = 1L,
            nickname = "app",
            hostname = "app.example.com",
            port = 22
        )
        val cyclicCandidate = Host(
            id = 2L,
            nickname = "jump-to-app",
            hostname = "jump.example.com",
            port = 22,
            jumpHostId = 1L
        )
        whenever(repository.findHostById(1L)).thenReturn(current)
        whenever(repository.getSshHosts()).thenReturn(listOf(current, cyclicCandidate))

        val viewModel = createViewModel(hostId = 1L)
        advanceUntilIdle()

        viewModel.updateJumpHostId(2L)
        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        assertEquals("Jump Host が循環参照になるため保存できません", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
        verify(repository, never()).saveHost(any())
    }

    private fun createViewModel(hostId: Long = -1L): HostEditorViewModel = HostEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("hostId" to hostId)),
        repository = repository,
        pubkeyRepository = pubkeyRepository,
        profileRepository = profileRepository,
        prefs = prefs,
        securePasswordStorage = securePasswordStorage
    )
}
