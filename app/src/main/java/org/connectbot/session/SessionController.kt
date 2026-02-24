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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import timber.log.Timber

/**
 * SSH接続のライフサイクルを管理するコントローラー。
 *
 * 変更理由: UIから直接SSHコア（TerminalManager / TerminalBridge）に
 * 触れないための抽象化レイヤーとして新設。
 * SessionScreen -> SessionViewModel -> SessionController -> TerminalManager
 * という依存方向を強制し、関心の分離を実現する。
 */
class SessionController(
    private val terminalManager: TerminalManager,
    private val dispatchers: CoroutineDispatchers,
    private val scope: CoroutineScope
) {
    /**
     * セッションの状態を表す sealed class。
     * UI側はこの状態に応じて表示を切り替える。
     */
    sealed class SessionState {
        /** 初期状態: bridge未生成 */
        data object Idle : SessionState()

        /** bridge生成中（TerminalManager経由） */
        data object Loading : SessionState()

        /**
         * bridge生成完了。SSH接続・認証はbridge内部で非同期進行中。
         * Terminal Composableを表示して接続進捗をユーザに見せる。
         */
        data class Active(val bridge: TerminalBridge) : SessionState()

        /** bridge生成に失敗 */
        data class Error(val message: String) : SessionState()

        /** 切断済み */
        data object Disconnected : SessionState()
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)

    /** UI側が観測するセッション状態 */
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /** 現在のbridgeへの参照（内部管理用） */
    private var currentBridge: TerminalBridge? = null

    init {
        // TerminalManagerのbridgesFlowを監視し、bridgeがリストから除去された場合は切断として扱う
        scope.launch {
            terminalManager.bridgesFlow.collect { bridges ->
                val bridge = currentBridge
                if (bridge != null && bridge !in bridges) {
                    currentBridge = null
                    _sessionState.value = SessionState.Disconnected
                }
            }
        }
    }

    /**
     * 指定されたhostIdでSSH接続を開始する。
     *
     * 処理フロー:
     * 1. 既存bridgeがあればそれを再利用（二重接続を防止）
     * 2. なければTerminalManager.openConnectionForHostId()で新規生成
     * 3. bridge生成と同時にSSH接続が非同期で開始される
     * 4. 認証プロンプトはbridge.promptManager経由でUI側に通知される
     */
    suspend fun connectByHostId(hostId: Long) {
        // 二重呼び出しガード
        val current = _sessionState.value
        if (current is SessionState.Loading || current is SessionState.Active) {
            Timber.d("SessionController: 既に接続中または接続済み、スキップ")
            return
        }

        _sessionState.value = SessionState.Loading

        try {
            withContext(dispatchers.io) {
                // 既存bridgeを検索（二重接続防止）
                val existingBridge = terminalManager.bridgesFlow.value.find {
                    it.host.id == hostId
                }

                if (existingBridge != null) {
                    currentBridge = existingBridge
                    _sessionState.value = SessionState.Active(existingBridge)
                    return@withContext
                }

                // TerminalManager経由で新規bridgeを生成
                // openConnectionForHostId内部でstartConnection()が呼ばれ、
                // SSH接続が非同期で開始される
                val bridge = terminalManager.openConnectionForHostId(hostId)
                if (bridge != null) {
                    currentBridge = bridge
                    _sessionState.value = SessionState.Active(bridge)
                } else {
                    _sessionState.value = SessionState.Error(
                        "ホストが見つかりません (ID: $hostId)"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SessionController: bridge生成エラー")
            _sessionState.value = SessionState.Error(
                e.message ?: "接続の開始に失敗しました"
            )
        }
    }

    /**
     * 現在のセッションを切断する。
     * bridge.dispatchDisconnect()を呼び出し、TerminalManagerがbridgeを除去するのを待つ。
     */
    fun disconnect() {
        currentBridge?.dispatchDisconnect(true)
        currentBridge = null
        _sessionState.value = SessionState.Disconnected
    }

    /**
     * 切断後に再接続する。
     *
     * 変更理由: exit後にDisconnected画面の「再接続」ボタンから
     * 新しいSSH接続を開始できるようにするため追加。
     * 状態をIdleにリセットし、connectByHostId()を呼び出す。
     */
    suspend fun reconnect(hostId: Long) {
        currentBridge = null
        _sessionState.value = SessionState.Idle
        connectByHostId(hostId)
    }

    /**
     * ターミナルにコマンド文字列を送信する。
     * bridge.injectString()経由でSSHセッションに書き込む。
     *
     * 変更理由: ショートカットバーからのコマンド送信に対応するため追加。
     *
     * @param command 送信するコマンド文字列（改行を含めること）
     */
    fun sendCommand(command: String) {
        val bridge = currentBridge
        if (bridge == null) {
            Timber.w("SessionController: bridge未接続のためコマンド送信をスキップ")
            return
        }
        bridge.injectString(command)
    }

    /**
     * リソースを解放する。ViewModel.onCleared()で呼び出すこと。
     * bridge自体の切断は行わない（バックグラウンド維持のため）。
     */
    fun release() {
        currentBridge = null
    }
}
