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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.connectbot.R
import org.connectbot.data.entity.Shortcut
import org.connectbot.service.TerminalBridge
import org.connectbot.session.CliCommandRegistry
import org.connectbot.session.SessionController
import org.connectbot.terminal.Terminal
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.components.InlinePrompt
import org.connectbot.ui.components.SHORTCUT_BAR_HEIGHT_DP
import org.connectbot.ui.components.ShortcutBar
import org.connectbot.util.rememberTerminalTypefaceResultFromStoredValue

/** 非意図的切断時の自動リトライまでの秒数 */
private const val AUTO_RETRY_SECONDS = 5

/**
 * SessionController経由でSSH接続を管理する画面。
 *
 * 変更理由: 既存ConsoleScreenの複雑さ（848行）を避け、
 * SessionControllerパターンによる最小限のターミナル表示画面を新設。
 *
 * 機能拡充:
 * - ShortcutBar を画面下部に2段構成で追加（プロファイルタブ＋ショートカット）。
 * - Disconnected状態で意図的切断は選択肢表示、非意図的切断は自動リトライ。
 * - プロファイル別ショートカット切替をサポート。
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
                    // ターミナル表示 + ショートカットバー + 認証プロンプト
                    TerminalContent(
                        bridge = state.bridge,
                        customShortcuts = shortcuts,
                        selectedProfileId = selectedProfileId,
                        onProfileChange = { viewModel.setProfile(it) },
                        onShortcutClick = { shortcut ->
                            viewModel.executeShortcut(shortcut)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
                    // 変更理由: 意図的切断は選択肢表示、非意図的は自動リトライ
                    DisconnectedContent(
                        isIntentional = isIntentionalDisconnect,
                        onReconnect = { viewModel.reconnect() },
                        onBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.Center)
                    )
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

    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
 * ターミナル表示 + ショートカットバー + 認証プロンプト。
 *
 * レイアウト構造:
 *   Column {
 *     Terminal (weight=1f, 残り全領域)
 *     ShortcutBar (2段構成: プロファイルタブ + ショートカット)
 *   }
 *   InlinePrompt (BottomCenter overlay)
 *
 * 変更理由: ショートカットバーを2段構成に拡張し、
 * プロファイル別のショートカット切替を追加。
 */
@Composable
private fun TerminalContent(
    bridge: TerminalBridge,
    customShortcuts: List<Shortcut>,
    selectedProfileId: String?,
    onProfileChange: (String?) -> Unit,
    onShortcutClick: (Shortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    val termFocusRequester = remember { FocusRequester() }
    var showSoftwareKeyboard by remember { mutableStateOf(true) }

    val fontResult = rememberTerminalTypefaceResultFromStoredValue(bridge.fontFamily)
    val fontSize by bridge.fontSizeFlow.collectAsState()

    LaunchedEffect(Unit) {
        termFocusRequester.requestFocus()
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Terminal Composable: termlib が提供するCompose対応ターミナル描画
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

            // 変更理由: 2段構成ショートカットバー (プロファイルタブ + ショートカット)
            ShortcutBar(
                customShortcuts = customShortcuts,
                selectedProfileId = selectedProfileId,
                onProfileChange = onProfileChange,
                onShortcutClick = onShortcutClick
            )
        }

        // 認証プロンプト（パスワード入力、ホスト鍵確認等）
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
                .padding(bottom = SHORTCUT_BAR_HEIGHT_DP.dp)
        )
    }
}

/**
 * 切断済み画面。
 *
 * 変更理由:
 * - 意図的切断（「切断」ボタン）の場合: 再接続/戻るの選択肢を表示。
 * - 非意図的切断（exit, ネットワーク断等）の場合: カウントダウン付きで
 *   自動リトライを行う。ユーザはカウントダウン中にキャンセルして
 *   ホーム画面に戻ることもできる。
 *
 * @param isIntentional 意図的切断かどうか
 * @param onReconnect 再接続コールバック
 * @param onBack ホーム画面に戻るコールバック
 */
@Composable
private fun DisconnectedContent(
    isIntentional: Boolean,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 変更理由: 非意図的切断時はカウントダウン後に自動リトライ
    var countdown by remember { mutableIntStateOf(AUTO_RETRY_SECONDS) }
    var autoRetryActive by remember { mutableStateOf(!isIntentional) }

    // カウントダウンタイマー
    LaunchedEffect(autoRetryActive) {
        if (autoRetryActive) {
            countdown = AUTO_RETRY_SECONDS
            while (countdown > 0) {
                delay(1000L)
                countdown--
            }
            // カウントダウン完了 → 自動リトライ
            onReconnect()
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isIntentional) "接続を終了しました" else "接続が切断されました",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!isIntentional && autoRetryActive) {
            // 非意図的切断: カウントダウン表示
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
            // 意図的切断: 選択肢表示
            Text(
                text = "SSHセッションが切断されました。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
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
