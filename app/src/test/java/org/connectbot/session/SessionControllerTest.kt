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

package org.connectbot.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * SessionControllerの接続ライフサイクルとコマンド送信をテストする。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )

    private lateinit var terminalManager: TerminalManager
    private lateinit var bridgesFlow: MutableStateFlow<List<TerminalBridge>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        terminalManager = mock()
        bridgesFlow = MutableStateFlow(emptyList())
        whenever(terminalManager.bridgesFlow).thenReturn(bridgesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle() = runTest(testDispatcher) {
        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        assertEquals(
            SessionController.SessionState.Idle,
            controller.sessionState.value
        )
    }

    @Test
    fun connectByHostId_existingBridge_reuses() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        bridgesFlow.value = listOf(mockBridge)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        val state = controller.sessionState.value
        assertTrue("Should be Active", state is SessionController.SessionState.Active)
        assertEquals(mockBridge, (state as SessionController.SessionState.Active).bridge)
    }

    @Test
    fun connectByHostId_newBridge_createsViaManager() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        whenever(terminalManager.openConnectionForHostId(1L)).thenReturn(mockBridge)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        val state = controller.sessionState.value
        assertTrue("Should be Active", state is SessionController.SessionState.Active)
    }

    @Test
    fun connectByHostId_nullBridge_setsError() = runTest(testDispatcher) {
        whenever(terminalManager.openConnectionForHostId(99L)).thenReturn(null)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(99L)
        advanceUntilIdle()

        val state = controller.sessionState.value
        assertTrue("Should be Error", state is SessionController.SessionState.Error)
    }

    @Test
    fun connectByHostId_exception_setsError() = runTest(testDispatcher) {
        whenever(terminalManager.openConnectionForHostId(1L))
            .thenThrow(IllegalArgumentException("Connection already open"))

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        val state = controller.sessionState.value
        assertTrue("Should be Error", state is SessionController.SessionState.Error)
        assertTrue(
            "Error message should contain exception message",
            (state as SessionController.SessionState.Error).message.contains("already open")
        )
    }

    @Test
    fun connectByHostId_alreadyActive_skips() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        bridgesFlow.value = listOf(mockBridge)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        assertTrue("Should be Active",
            controller.sessionState.value is SessionController.SessionState.Active)

        // 二重呼び出し: 状態は変わらないこと
        controller.connectByHostId(2L)
        advanceUntilIdle()

        val state = controller.sessionState.value as SessionController.SessionState.Active
        assertEquals("Should still be connected to original host", 1L, state.bridge.host.id)
    }

    @Test
    fun disconnect_setsDisconnectedState() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        bridgesFlow.value = listOf(mockBridge)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        controller.disconnect()

        assertEquals(
            SessionController.SessionState.Disconnected,
            controller.sessionState.value
        )
        verify(mockBridge).dispatchDisconnect(true)
    }

    @Test
    fun bridgeRemovedFromManager_setsDisconnected() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        bridgesFlow.value = listOf(mockBridge)

        // backgroundScopeではなくtestDispatcherで明示スコープを作成
        // (backgroundScopeはUnconfinedTestDispatcherを使用する場合があり、
        //  StateFlow collectのタイミング問題を回避するため)
        val controllerScope = CoroutineScope(testDispatcher + SupervisorJob())
        val controller = SessionController(terminalManager, dispatchers, controllerScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        assertTrue("Should be Active",
            controller.sessionState.value is SessionController.SessionState.Active)

        // TerminalManagerからbridgeが除去されたシミュレーション
        bridgesFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(
            "Should be Disconnected when bridge removed",
            SessionController.SessionState.Disconnected,
            controller.sessionState.value
        )

        controllerScope.cancel()
    }

    @Test
    fun sendCommand_activeState_injectsString() = runTest(testDispatcher) {
        val host = createHost(1L, "test-host")
        val mockBridge = createMockBridge(host)
        bridgesFlow.value = listOf(mockBridge)

        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        controller.connectByHostId(1L)
        advanceUntilIdle()

        controller.sendCommand("ls -la\n")

        verify(mockBridge).injectString("ls -la\n")
    }

    @Test
    fun sendCommand_notConnected_doesNothing() = runTest(testDispatcher) {
        val controller = SessionController(terminalManager, dispatchers, backgroundScope)
        advanceUntilIdle()

        // 接続前にコマンド送信を試みても例外が発生しないこと
        controller.sendCommand("ls\n")
    }

    private fun createHost(id: Long, hostname: String): Host = Host(
        id = id,
        hostname = hostname,
        nickname = hostname,
        protocol = "ssh",
        port = 22,
        username = "test"
    )

    /**
     * TerminalBridgeのモックを生成する。
     * bridge.host はモックのプロパティとしてwhenever()で設定する。
     * (直接代入はMockitoモックでは値が保持されないため)
     */
    private fun createMockBridge(host: Host): TerminalBridge {
        val bridge = mock<TerminalBridge>()
        whenever(bridge.host).thenReturn(host)
        whenever(bridge.isSessionOpen).thenReturn(true)
        whenever(bridge.isDisconnected).thenReturn(false)
        whenever(bridge.bellEvents).thenReturn(MutableSharedFlow())
        whenever(bridge.progressState).thenReturn(MutableStateFlow(null))
        return bridge
    }
}
