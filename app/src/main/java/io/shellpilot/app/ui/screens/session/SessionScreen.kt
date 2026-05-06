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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import io.shellpilot.app.R
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.service.TerminalBridge
import io.shellpilot.app.session.CliCommandRegistry
import io.shellpilot.app.session.SessionController
import org.connectbot.terminal.Terminal
import io.shellpilot.app.ui.LocalTerminalManager
import io.shellpilot.app.ui.components.FloatingTextInputDialog
import io.shellpilot.app.ui.components.InlinePrompt
import io.shellpilot.app.ui.components.SHORTCUT_BAR_HEIGHT_DP
import io.shellpilot.app.ui.components.ShellPilotTopBar
import io.shellpilot.app.ui.components.ShortcutBar
import io.shellpilot.app.ui.components.TERMINAL_KEYBOARD_HEIGHT_DP
import io.shellpilot.app.ui.components.TerminalKeyboard
import io.shellpilot.app.util.rememberTerminalTypefaceResultFromStoredValue

/** 非意図的切断時の自動リトライまでの秒数 */
private const val AUTO_RETRY_SECONDS = 5

/**
 * SessionController経由でSSH接続を管理する画面。
 *
 * 変更理由: 既存ConsoleScreenの複雑さ（848行）を避け、
 * SessionControllerパターンによる最小限のターミナル表示画面を新設。
 *
 * 機能拡充:
 * - TerminalKeyboard (Ctrl/Esc/矢印等) を上段バーとして常時表示。
 * - ShortcutBar をプロファイルタブ＋ショートカット2段構成で下段に配置。
 * - Disconnected状態で意図的切断は選択肢表示、非意図的切断は自動リトライ。
 * - プロファイル別ショートカット切替をサポート。
 *
 * レイアウト構造 (TerminalContent):
 *   Scaffold (TopBar + content padding で navigationBars を処理)
 *     Column {
 *       Terminal         (weight=1f, 残り全領域)
 *       TerminalKeyboard (上段: Ctrl/Esc/矢印キー等, 48dp)
 *       ShortcutBar      (下段: プロファイルタブ + ショートカット, 105dp)
 *     }
 *     InlinePrompt (BottomCenter overlay, 両バー合計高さ分パディング)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShortcutSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val terminalManager = LocalTerminalManager.current
    val sessionState by viewModel.sessionState.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val probeResults by viewModel.probeResults.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val profileOrder by viewModel.profileOrder.collectAsState()
    val isIntentionalDisconnect by viewModel.isIntentionalDisconnect.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // TerminalManagerが利用可能になったらViewModelを初期化
    LaunchedEffect(terminalManager) {
        terminalManager?.let { viewModel.initialize(it) }
    }

    // プローブ結果をSnackbarで通知し、検出ツールのコマンドを自動インポート
    LaunchedEffect(probeResults) {
        val found = probeResults.filter { it.found }
        if (found.isNotEmpty()) {
            val names = found.mapNotNull { result ->
                CliCommandRegistry.findCategory(result.toolId)?.displayName
            }
            // 検出されたツールのコマンドを自動インポート
            found.forEach { result ->
                viewModel.importToolCommands(result.toolId)
            }
            snackbarHostState.showSnackbar(
                "検出: ${names.joinToString(", ")} -- コマンドを追加しました"
            )
        }
    }

    // 変更理由: ScaffoldのcontentWindowInsetsにnavigationBarsが含まれるため、
    // innerPaddingがナビゲーションバー分のボトムパディングを持つ。
    // consumeWindowInsetsと組み合わせることで子Composableへのinsets二重適用を防ぐ。
    Scaffold(
        topBar = {
            SessionTopBar(
                sessionState = sessionState,
                onNavigateBack = onNavigateBack,
                onDisconnect = { viewModel.disconnect() },
                onNavigateToShortcutSettings = {
                    onNavigateToShortcutSettings()
                },
                onProbeCliTools = { viewModel.probeCliTools() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.imeAnimationTarget)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.imeAnimationTarget)
        ) {
            when (val state = sessionState) {
                is SessionController.SessionState.Idle -> {
                    IdleContent(
                        onConnect = { viewModel.connect() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SessionController.SessionState.Loading -> {
                    LoadingContent(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SessionController.SessionState.Active -> {
                    // 変更理由: key(state.bridge)を追加し、bridgeが変わった際に
                    // 古いTerminalContentの全effectsが確実にdisposeされるようにする。
                    // Active→Disconnected遷移時の古いbridge参照によるクラッシュを防止。
                    key(state.bridge) {
                        TerminalContent(
                            bridge = state.bridge,
                            customShortcuts = shortcuts,
                            selectedProfileId = selectedProfileId,
                            profileOrder = profileOrder,
                            onProfileChange = { viewModel.setProfile(it) },
                            onShortcutClick = { shortcut ->
                                viewModel.executeShortcut(shortcut)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                is SessionController.SessionState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.connect() },
                        onBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SessionController.SessionState.Disconnected -> {
                    // 変更理由: 意図的切断は自動でホーム画面に戻る。
                    // 非意図的切断のみカウントダウン付きリトライ画面を表示。
                    // LaunchedEffectのキーをisIntentionalDisconnectに変更し、
                    // recomposition時の不要な再発火を防止。
                    if (isIntentionalDisconnect) {
                        LaunchedEffect(isIntentionalDisconnect) {
                            onNavigateBack()
                        }
                    } else {
                        DisconnectedContent(
                            onReconnect = { viewModel.reconnect() },
                            onBack = onNavigateBack,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

/**
 * セッション画面のトップバー。
 * 接続状態に応じてタイトルとアクションを切り替える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    sessionState: SessionController.SessionState,
    onNavigateBack: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToShortcutSettings: () -> Unit,
    onProbeCliTools: () -> Unit
) {
    val title = when (sessionState) {
        is SessionController.SessionState.Active -> sessionState.bridge.host.nickname
        is SessionController.SessionState.Loading -> "接続中..."
        is SessionController.SessionState.Error -> "エラー"
        is SessionController.SessionState.Disconnected -> "切断済み"
        else -> "セッション"
    }

    ShellPilotTopBar(
        title = title,
        subtitle = when (sessionState) {
            is SessionController.SessionState.Active -> "SSH端末とAI CLIの作業画面"
            is SessionController.SessionState.Loading -> "セッションを初期化中"
            is SessionController.SessionState.Error -> "接続を確認してください"
            is SessionController.SessionState.Disconnected -> "再接続または終了を選択"
            else -> "SSH端末"
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_back)
                )
            }
        },
        actions = {
            // 変更理由: 接続先のCLIツールを動的検出するボタンを追加
            if (sessionState is SessionController.SessionState.Active) {
                IconButton(onClick = onProbeCliTools) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "ツール検出"
                    )
                }
            }
            // 変更理由: ショートカット設定画面へのナビゲーションボタンを追加
            IconButton(onClick = onNavigateToShortcutSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "ショートカット設定"
                )
            }
            if (sessionState is SessionController.SessionState.Active) {
                TextButton(onClick = onDisconnect) {
                    Text("切断")
                }
            }
        }
    )
}

/**
 * 初期状態の表示。手動接続ボタンを提供。
 */
@Composable
private fun IdleContent(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "接続準備完了",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConnect) {
            Text("接続開始")
        }
    }
}

/**
 * bridge生成中のローディング表示。
 */
@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "セッションを準備中...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * ターミナル表示 + TerminalKeyboard(上段) + ShortcutBar(下段) + 認証プロンプト。
 *
 * レイアウト構造:
 *   Box(fillMaxSize) {
 *     Column(fillMaxSize) {
 *       Terminal          (weight=1f, 残り全領域)
 *       TerminalKeyboard  (上段: Ctrl/Esc/矢印等, TERMINAL_KEYBOARD_HEIGHT_DP=30dp)
 *       ShortcutBar       (下段: プロファイルタブ+ショートカット, SHORTCUT_BAR_HEIGHT_DP=85dp)
 *     }
 *     InlinePrompt (BottomCenter overlay)
 *   }
 *
 * WindowInsets対応:
 * - ScaffoldのcontentWindowInsetsがnavigationBarsを含む → innerPaddingがボトムパディングを持つ
 * - consumeWindowInsetsにより子Composableへのinsets二重適用を防ぐ
 * - ShortcutBar内のwindowInsetsPadding(navigationBars)はScaffoldで消費済みのため実質0
 * - TerminalKeyboardはオーバーレイではなくColumnの固定要素として配置 (ConsoleScreenとは異なる)
 *
 * 変更理由:
 * - TerminalKeyboard(Ctrl/Esc/矢印)をColumnに追加し、ConsoleScreenと同等の
 *   特殊キー操作をSessionScreenでも提供する。
 * - オーバーレイではなくColumn内配置にすることでターミナル領域が正しく縮小し、
 *   ナビゲーションバーとの干渉を防ぐ。
 * - InlinePromptのbottomPaddingを両バー合計高さに更新。
 */
@Composable
private fun TerminalContent(
    bridge: TerminalBridge,
    customShortcuts: List<Shortcut>,
    selectedProfileId: String?,
    // 変更理由: プロファイルタブの表示順序をShortcutBarに渡す
    profileOrder: List<String?> = emptyList(),
    onProfileChange: (String?) -> Unit,
    onShortcutClick: (Shortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    val termFocusRequester = remember { FocusRequester() }
    // IMEの表示状態をTerminalKeyboardと共有するための状態
    var showSoftwareKeyboard by remember { mutableStateOf(true) }
    // 変更理由: 日本語入力(IME)対応のFloatingTextInputDialogの表示状態。
    // ConsoleScreen.ktと同じパターンで✏️ボタンから起動する。
    var showTextInputDialog by remember { mutableStateOf(false) }

    val fontResult = rememberTerminalTypefaceResultFromStoredValue(bridge.fontFamily)
    val fontSize by bridge.fontSizeFlow.collectAsState()

    LaunchedEffect(Unit) {
        termFocusRequester.requestFocus()
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Terminal Composable: termlib が提供するCompose対応ターミナル描画
            // weight(1f) で残り全領域を使用し、下段バーに押し上げられない
            Terminal(
                terminalEmulator = bridge.terminalEmulator,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                typeface = fontResult.typeface,
                initialFontSize = fontSize.sp,
                keyboardEnabled = true,
                showSoftKeyboard = showSoftwareKeyboard,
                focusRequester = termFocusRequester,
                modifierManager = bridge.keyHandler,
                onTerminalTap = {
                    showSoftwareKeyboard = !showSoftwareKeyboard
                },
                onImeVisibilityChanged = { visible ->
                    showSoftwareKeyboard = visible
                }
            )

            // 変更理由: 上段バー - Ctrl/Esc/矢印キー等の特殊キーバー。
            // ConsoleScreenではオーバーレイとして配置されているが、
            // SessionScreenではColumnに固定配置することでターミナル領域が
            // 正しくリサイズされ、ナビゲーションバーとの干渉を防ぐ。
            // TERMINAL_KEYBOARD_HEIGHT_DP = 48dp
            TerminalKeyboard(
                bridge = bridge,
                onInteraction = {},  // SessionScreenでは自動非表示タイマーなし
                onHideIme = { showSoftwareKeyboard = false },
                onShowIme = { showSoftwareKeyboard = true },
                // 変更理由: 日本語入力のためFloatingTextInputDialogを接続。
                onOpenTextInput = { showTextInputDialog = true },
                imeVisible = showSoftwareKeyboard,
                modifier = Modifier.fillMaxWidth()
            )

            // 変更理由: 下段バー - プロファイルタブ + ショートカット (2段構成)。
            // SHORTCUT_BAR_HEIGHT_DP = PROFILE_ROW_HEIGHT_DP(48) + SHORTCUT_ROW_HEIGHT_DP(56) + 1 = 105dp
            ShortcutBar(
                customShortcuts = customShortcuts,
                selectedProfileId = selectedProfileId,
                onProfileChange = onProfileChange,
                onShortcutClick = onShortcutClick,
                profileOrder = profileOrder
            )
        }

        // 変更理由: 日本語入力(IME)のフル機能を提供するフローティングダイアログ。
        // ConsoleScreen.kt のパターンに従い、TerminalKeyboardの✏️ボタンから起動する。
        if (showTextInputDialog) {
            FloatingTextInputDialog(
                bridge = bridge,
                initialText = "",
                onDismiss = {
                    showTextInputDialog = false
                    termFocusRequester.requestFocus()
                }
            )
        }

        // 認証プロンプト（パスワード入力、ホスト鍵確認等）のオーバーレイ。
        // 変更理由: TerminalKeyboard(48dp) + ShortcutBar(105dp) の合計高さ分だけ
        // 下から浮かせることで、両バーに隠れずプロンプトが表示される。
        val promptState by bridge.promptManager.promptState.collectAsState()

        InlinePrompt(
            promptRequest = promptState,
            onResponse = { response ->
                bridge.promptManager.respond(response)
            },
            onCancel = {
                bridge.promptManager.cancelPrompt()
            },
            onDismissed = {
                termFocusRequester.requestFocus()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = (SHORTCUT_BAR_HEIGHT_DP + TERMINAL_KEYBOARD_HEIGHT_DP).dp)
        )
    }
}

/**
 * 非意図的切断時のリトライ画面。
 *
 * 変更理由: 意図的切断はSessionScreenで自動ホーム遷移するため、
 * この画面は非意図的切断（exit, ネットワーク断等）の自動リトライ専用に簡略化。
 * カウントダウン完了で自動再接続、ユーザはキャンセルしてホームに戻ることも可能。
 *
 * @param onReconnect 再接続コールバック
 * @param onBack ホーム画面に戻るコールバック
 */
@Composable
private fun DisconnectedContent(
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var countdown by remember { mutableIntStateOf(AUTO_RETRY_SECONDS) }
    var autoRetryActive by remember { mutableStateOf(true) }

    LaunchedEffect(autoRetryActive) {
        if (autoRetryActive) {
            countdown = AUTO_RETRY_SECONDS
            while (countdown > 0) {
                delay(1000L)
                countdown--
            }
            onReconnect()
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "接続が切断されました",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (autoRetryActive) {
            Text(
                text = "${countdown}秒後に自動的に再接続します...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onReconnect) {
                Text("今すぐ再接続")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                autoRetryActive = false
                onBack()
            }) {
                Text("キャンセルして戻る")
            }
        } else {
            Button(onClick = onReconnect) {
                Text("再接続")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) {
                Text("ホーム画面に戻る")
            }
        }
    }
}

/**
 * エラー表示。リトライボタンと戻るボタンを提供。
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "接続エラー",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("リトライ")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text("戻る")
        }
    }
}
