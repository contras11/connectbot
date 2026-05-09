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

package io.shellpilot.app.ui.screens.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shellpilot.app.data.ProfileOrderRepository
import io.shellpilot.app.data.ShortcutRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.di.CoroutineDispatchers
import io.shellpilot.app.service.TerminalManager
import io.shellpilot.app.session.CliCommandRegistry
import io.shellpilot.app.session.CliToolProbe
import io.shellpilot.app.session.SessionController
import io.shellpilot.app.session.ShortcutExpander
import io.shellpilot.app.ui.navigation.NavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val shortcutRepository: ShortcutRepository,
    private val profileOrderRepository: ProfileOrderRepository
) : ViewModel() {

    /** Navigationルートから取得するホストID */
    val hostId: Long = savedStateHandle.get<Long>(NavArgs.HOST_ID) ?: -1L

    private var sessionController: SessionController? = null
    private var shortcutCollectionStarted = false

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
     * 変更理由: プロファイルタブの表示順序をShortcutBarに渡すための状態。
     * ProfileOrderRepositoryから読み込んだ順序を公開する。
     */
    private val _profileOrder = MutableStateFlow<List<String?>>(
        profileOrderRepository.getOrder()
    )

    /** プロファイルタブの表示順序 */
    val profileOrder: StateFlow<List<String?>> = _profileOrder.asStateFlow()

    private val _hiddenProfileIds = MutableStateFlow<Set<String>>(
        profileOrderRepository.getHiddenProfileIds()
    )

    /** 非表示にしたプロファイルタブID */
    val hiddenProfileIds: StateFlow<Set<String>> = _hiddenProfileIds.asStateFlow()

    /**
     * 変更理由: 意図的切断かどうかを追跡するフラグ。
     * trueの場合は「切断」ボタンからの操作。
     * falseの場合はexit/ネットワーク断等の非意図的切断で、自動リトライ対象。
     */
    private val _isIntentionalDisconnect = MutableStateFlow(false)

    /** UIが観測する意図的切断フラグ */
    val isIntentionalDisconnect: StateFlow<Boolean> = _isIntentionalDisconnect.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())

    /** Command Composerで再利用する直近送信コマンド */
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

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

        // 変更理由: URI/ショートカット由来のtemporary host(負ID)も既存bridgeを拾う。
        if (hostId != -1L) {
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
     * Command Composerから任意コマンドを送信する。
     *
     * 変更理由: UIコンポーネントからTerminalBridgeへ直接raw送信する責務を減らし、
     * SessionController経由の送信経路へ揃える。
     */
    fun sendComposerCommand(command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return

        sessionController?.sendCommand("$trimmedCommand\n")
        _commandHistory.value = buildList {
            add(trimmedCommand)
            _commandHistory.value
                .filterNot { it == trimmedCommand }
                .take(COMMAND_HISTORY_LIMIT - 1)
                .forEach(::add)
        }
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
        if (shortcutCollectionStarted) return
        shortcutCollectionStarted = true
        viewModelScope.launch {
            // 変更理由: セッション開始時は保存済みショートカットの読込だけに限定し、
            // 公式テンプレート同期はショートカット設定画面の明示操作に任せる。
            _shortcuts.value = filterShortcutsForHost(loadShortcutsOrEmpty())
            shortcutRepository.shortcuts.collect { list ->
                _shortcuts.value = filterShortcutsForHost(list)
            }
        }
        viewModelScope.launch {
            profileOrderRepository.order.collect { order ->
                _profileOrder.value = order
            }
        }
        viewModelScope.launch {
            profileOrderRepository.hiddenProfileIds.collect { hiddenIds ->
                _hiddenProfileIds.value = hiddenIds
            }
        }
    }

    /** ショートカット一覧を再読込する (設定画面から戻った際に呼び出す) */
    fun reloadShortcuts() {
        _profileOrder.value = profileOrderRepository.getOrder()
        _hiddenProfileIds.value = profileOrderRepository.getHiddenProfileIds()
        viewModelScope.launch {
            val list = loadShortcutsOrEmpty()
            _shortcuts.value = filterShortcutsForHost(list)
        }
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
            runCatching {
                val existing = shortcutRepository.loadAll()
                val existingTemplateKeys = existing.mapNotNull { it.templateKey }.toSet()
                val existingPairs = existing.map { it.label to it.category }.toSet()
                val maxOrder = existing.maxOfOrNull { it.order } ?: 0
                category.commands
                    .filter {
                        val key = it.templateKey
                        (key == null || key !in existingTemplateKeys) &&
                            (it.label to category.id) !in existingPairs
                    }
                    .forEachIndexed { index, cmd ->
                        shortcutRepository.save(
                            cmd.copy(order = maxOrder + index + 1, category = category.id)
                        )
                    }
                loadShortcuts()
            }.onFailure {
                Timber.e(it, "SessionViewModel: ツールコマンドの取り込みに失敗")
            }
        }
    }

    private suspend fun loadShortcutsOrEmpty(): List<Shortcut> = runCatching {
        shortcutRepository.loadAll()
    }.onFailure {
        // 変更理由: ショートカットJSONの破損やI/O失敗をセッション画面の停止に波及させない。
        Timber.e(it, "SessionViewModel: ショートカット読込に失敗")
    }.getOrElse {
        emptyList()
    }

    private fun filterShortcutsForHost(list: List<Shortcut>): List<Shortcut> = if (hostId > 0L) {
        list.filter { it.hostId == null || it.hostId == hostId }
            .sortedWith(compareBy<Shortcut> { it.hostId != null }.thenBy { it.order })
    } else {
        list.sortedBy { it.order }
    }

    override fun onCleared() {
        super.onCleared()
        sessionController?.release()
    }

    private companion object {
        const val COMMAND_HISTORY_LIMIT = 8
    }
}
