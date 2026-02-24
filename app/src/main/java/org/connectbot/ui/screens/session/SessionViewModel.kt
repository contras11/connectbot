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

package org.connectbot.ui.screens.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.data.ShortcutRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Shortcut
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalManager
import org.connectbot.session.CliCommandRegistry
import org.connectbot.session.CliToolProbe
import org.connectbot.session.SessionController
import org.connectbot.session.ShortcutExpander
import org.connectbot.ui.navigation.NavArgs
import timber.log.Timber
import javax.inject.Inject

/**
 * SessionScreen用のViewModel。
 *
 * 変更理由: SessionControllerをCompose UIから利用するための橋渡し。
 * TerminalManagerはCompositionLocal経由で渡されるため、
 * initialize()で遅延初期化する設計とした。
 *
 * 機能拡充: ShortcutRepositoryを注入し、ショートカット一覧の取得と
 * プレースホルダ展開付きコマンド送信を追加。
 * プロファイル切替機能を追加し、カテゴリ別にショートカットを表示可能にした。
 * 意図的/非意図的切断の区別と自動リトライ機能を追加。
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val shortcutRepository: ShortcutRepository
) : ViewModel() {

    /** Navigationルートから取得するホストID */
    val hostId: Long = savedStateHandle.get<Long>(NavArgs.HOST_ID) ?: -1L

    private var sessionController: SessionController? = null

    private val _sessionState = MutableStateFlow<SessionController.SessionState>(
        SessionController.SessionState.Idle
    )

    /** UI側が観測するセッション状態 */
    val sessionState: StateFlow<SessionController.SessionState> = _sessionState.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())

    /** 現在のホストに適用可能なカスタムショートカット一覧 */
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts.asStateFlow()

    private val _probeResults = MutableStateFlow<List<CliToolProbe.ProbeResult>>(emptyList())

    /** CLIツール検出結果 (UI側で検出通知に使用) */
    val probeResults: StateFlow<List<CliToolProbe.ProbeResult>> = _probeResults.asStateFlow()

    /**
     * 変更理由: プロファイル別ショートカット切替のための状態。
     * nullはカスタム(ShortcutRepository)、文字列はCliCommandRegistryのカテゴリID。
     * デフォルトは "claude_code" に設定 (ユーザ要望: Claude Codeを初期選択)。
     */
    private val _selectedProfileId = MutableStateFlow<String?>("claude_code")

    /** 選択中のプロファイルID */
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    /**
     * 変更理由: 意図的切断かどうかを追跡するフラグ。
     * trueの場合は「切断」ボタンからの操作。
     * falseの場合はexit/ネットワーク断等の非意図的切断で、自動リトライ対象。
     */
    private val _isIntentionalDisconnect = MutableStateFlow(false)

    /** UIが観測する意図的切断フラグ */
    val isIntentionalDisconnect: StateFlow<Boolean> = _isIntentionalDisconnect.asStateFlow()

    /**
     * TerminalManagerが利用可能になった時点で呼び出す。
     * SessionControllerを生成し、状態の転送を開始する。
     * hostIdが有効であれば自動的に接続を開始する。
     * ショートカットの読込も開始する。
     */
    fun initialize(manager: TerminalManager) {
        // 二重初期化防止
        if (sessionController != null) return

        val controller = SessionController(
            terminalManager = manager,
            dispatchers = dispatchers,
            scope = viewModelScope
        )
        sessionController = controller

        // SessionControllerの状態をViewModelのStateFlowに転送
        viewModelScope.launch {
            controller.sessionState.collect { state ->
                _sessionState.value = state
            }
        }

        // ショートカットを読込
        loadShortcuts()

        // hostIdが指定されていれば自動接続
        if (hostId > 0L) {
            connect()
        }
    }

    /** SSH接続を開始する */
    fun connect() {
        _isIntentionalDisconnect.value = false
        viewModelScope.launch {
            sessionController?.connectByHostId(hostId)
        }
    }

    /**
     * 切断後に再接続する。
     *
     * 変更理由: exit後やネットワーク断後にDisconnected画面から
     * 再接続できるようにするため追加。
     * SessionControllerの状態をリセットし、新規接続を開始する。
     */
    fun reconnect() {
        _isIntentionalDisconnect.value = false
        viewModelScope.launch {
            sessionController?.reconnect(hostId)
        }
    }

    /**
     * 現在のセッションを意図的に切断する。
     *
     * 変更理由: 意図的切断フラグを設定し、自動リトライを抑制する。
     */
    fun disconnect() {
        _isIntentionalDisconnect.value = true
        sessionController?.disconnect()
    }

    /**
     * ショートカットのコマンドをプレースホルダ展開してターミナルに送信する。
     *
     * 変更理由: ShortcutBarからのタップを処理するため追加。
     * ShortcutExpanderでホスト情報を元にプレースホルダを展開した後、
     * SessionController.sendCommand()でターミナルに送信する。
     */
    fun executeShortcut(shortcut: Shortcut) {
        val state = _sessionState.value
        if (state !is SessionController.SessionState.Active) return

        val host: Host = state.bridge.host
        val expandedCommand = ShortcutExpander.expand(shortcut.command, host)
        sessionController?.sendCommand(expandedCommand)
    }

    /**
     * プロファイルを切り替える。
     *
     * 変更理由: ShortcutBarの段1タブから呼び出される。
     * nullでカスタム(ShortcutRepository)、文字列でCliCommandRegistryカテゴリを選択。
     */
    fun setProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    /** ホストIDに対応するショートカットを読込む */
    private fun loadShortcuts() {
        viewModelScope.launch {
            val list = if (hostId > 0L) {
                shortcutRepository.getForHost(hostId)
            } else {
                shortcutRepository.loadAll()
            }
            _shortcuts.value = list
        }
    }

    /** ショートカット一覧を再読込する (設定画面から戻った際に呼び出す) */
    fun reloadShortcuts() {
        loadShortcuts()
    }

    /**
     * 接続先ホストでCLIツールの存在を検出する。
     *
     * 変更理由: Claude Code / Codex 等のツールが利用可能かを
     * 動的に判定し、対応コマンドのインポートを提案するため追加。
     * プローブコマンドをターミナルに送信し、画面バッファをスキャンする。
     */
    fun probeCliTools() {
        val state = _sessionState.value
        if (state !is SessionController.SessionState.Active) return

        viewModelScope.launch {
            try {
                val results = CliToolProbe.probe(state.bridge)
                _probeResults.value = results
                val found = results.filter { it.found }.map { it.binary }
                Timber.d("CliToolProbe: 検出されたツール: $found")
            } catch (e: Exception) {
                Timber.e(e, "CliToolProbe: 検出エラー")
            }
        }
    }

    /**
     * 検出されたツールのコマンドをショートカットに一括追加する。
     *
     * @param toolId CliCommandRegistry内のカテゴリID
     */
    fun importToolCommands(toolId: String) {
        val category = CliCommandRegistry.findCategory(toolId) ?: return
        viewModelScope.launch {
            val existing = shortcutRepository.loadAll()
            val existingLabels = existing.map { it.label }.toSet()
            // 重複ラベルを除外して追加
            val maxOrder = existing.maxOfOrNull { it.order } ?: 0
            category.commands
                .filter { it.label !in existingLabels }
                .forEachIndexed { index, cmd ->
                    shortcutRepository.save(
                        cmd.copy(order = maxOrder + index + 1)
                    )
                }
            loadShortcuts()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionController?.release()
    }
}
