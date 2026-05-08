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

package io.shellpilot.app.ui.screens.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.shellpilot.app.BuildConfig
import io.shellpilot.app.R
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHints: () -> Unit,
    onNavigateToEula: () -> Unit,
    onNavigateToContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeyboardShortcuts by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }

    ShellPilotScaffold(
        title = stringResource(R.string.title_help),
        subtitle = "サポート・ログ・フォーク元情報",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_navigate_up)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CommandSurfaceCard(accent = MaterialTheme.colorScheme.primary) {
                    // 変更理由: フォーク元を明示するためアプリ名とフォーク表記を変更
                    Text(
                        text = "ShellPilot",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(label = "AI CLI対応")
                        StatusChip(label = "SSHクライアント")
                    }
                    Text(
                        text = "ConnectBotをベースに、Claude Code / Codex などのCLI作業を扱いやすくしたSSHワークスペースです。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} / ConnectBotベース",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                HelpActionCard(
                    title = stringResource(R.string.hints),
                    summary = "接続・認証・ターミナル操作のヒント",
                    onClick = onNavigateToHints
                )
            }

            item {
                HelpActionCard(
                    title = stringResource(R.string.keyboard_shortcuts),
                    summary = "ターミナル操作に使う主要キーを確認",
                    onClick = { showKeyboardShortcuts = true }
                )
            }

            item {
                HelpActionCard(
                    title = stringResource(R.string.view_logs),
                    summary = "接続調査やバグ報告に使うログを表示・コピー",
                    onClick = { showLogViewer = true }
                )
            }

            item {
                HelpActionCard(
                    title = stringResource(R.string.title_contact),
                    summary = "不具合報告やサポート導線",
                    onClick = onNavigateToContact
                )
            }

            item {
                HelpActionCard(
                    title = stringResource(R.string.terms_and_conditions),
                    summary = "ライセンスと利用条件",
                    onClick = onNavigateToEula
                )
            }

            item {
                CommandSurfaceCard(accent = MaterialTheme.colorScheme.secondary) {
                    Text(
                        text = stringResource(R.string.help_section_about),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.app_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 変更理由: フォーク元の情報を明示
                    Text(
                        text = "ShellPilotはConnectBotをベースに、Kotlin/Compose化と" +
                            "Claude Code / Codex向けのCLI操作導線を追加したSSHクライアントです。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "フォーク元: ${stringResource(R.string.app_copyright)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showKeyboardShortcuts) {
        KeyboardShortcutsDialog(
            onDismiss = { showKeyboardShortcuts = false }
        )
    }

    if (showLogViewer) {
        LogViewerDialog(
            onDismiss = { showLogViewer = false }
        )
    }
}

@Composable
private fun HelpActionCard(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    CommandSurfaceCard(onClick = onClick, accent = MaterialTheme.colorScheme.primary) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.keyboard_shortcuts),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.button_close)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShortcutRow(
                shortcut = stringResource(R.string.paste_shortcut),
                description = stringResource(R.string.console_menu_paste)
            )
            ShortcutRow(
                shortcut = stringResource(R.string.increase_font_shortcut),
                description = stringResource(R.string.increase_font_size)
            )
            ShortcutRow(
                shortcut = stringResource(R.string.decrease_font_shortcut),
                description = stringResource(R.string.decrease_font_size)
            )
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = shortcut,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LogViewerDialog(
    onDismiss: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val logs = uiState.logs

    LaunchedEffect(Unit) {
        viewModel.loadLogs()
    }

    ShellPilotActionDialog(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.86f),
        title = stringResource(R.string.logs_title),
        subtitle = stringResource(R.string.logs_bug_report_info),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.copy_logs),
        onConfirm = {
            copyLogsToClipboard(context, logs)
        },
        dismissLabel = stringResource(R.string.button_close)
    ) {
        val scrollState = rememberScrollState()
        Text(
            text = logs.ifEmpty { stringResource(R.string.no_logs_available) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        )
    }
}

private fun copyLogsToClipboard(context: Context, logs: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(context.getString(R.string.logs_title), logs)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
}

@ScreenPreviews
@Composable
private fun HelpScreenPreview() {
    ShellPilotTheme {
        HelpScreen(
            onNavigateBack = {},
            onNavigateToHints = {},
            onNavigateToEula = {},
            onNavigateToContact = {}
        )
    }
}

@Preview
@Composable
private fun KeyboardShortcutsDialogPreview() {
    ShellPilotTheme {
        KeyboardShortcutsDialog(
            onDismiss = {}
        )
    }
}
