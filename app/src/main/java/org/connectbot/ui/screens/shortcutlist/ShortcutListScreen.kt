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

package org.connectbot.ui.screens.shortcutlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.data.entity.Shortcut
import org.connectbot.session.CliCommandRegistry

/**
 * ショートカット一覧・編集画面。
 *
 * 変更理由: ショートカットの追加・編集・削除を行うUI画面が
 * 存在しなかったため新設。Settings画面およびSessionScreen TopBarから
 * ナビゲーションで到達する。
 *
 * 機能:
 * - 一覧表示（ラベル + コマンドプレビュー + スコープ表示）
 * - FABで新規追加
 * - タップで編集ダイアログ
 * - アイコンで削除
 * - カテゴリ別一括インポート (Claude Code, Codex, Git, Docker 等)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShortcutListViewModel = hiltViewModel()
) {
    val shortcuts by viewModel.shortcuts.collectAsState()
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ショートカット設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                actions = {
                    // 変更理由: カテゴリ別一括インポート機能へのアクセスを追加
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "テンプレートからインポート"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (shortcuts.isEmpty()) {
            // 空状態の表示
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp)
            ) {
                Text(
                    text = "ショートカットがありません",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "右下の＋ボタンから個別追加、または右上のインポートボタンから" +
                        "テンプレートを一括追加できます。\n\n" +
                        "コマンド内で {{hostname}} などのプレースホルダが使えます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding)
            ) {
                itemsIndexed(
                    items = shortcuts,
                    key = { _, shortcut -> shortcut.id }
                ) { _, shortcut ->
                    ShortcutListItem(
                        shortcut = shortcut,
                        onClick = { editingShortcut = shortcut },
                        onDelete = { viewModel.delete(shortcut.id) }
                    )
                }
            }
        }
    }

    // 新規追加ダイアログ
    if (showAddDialog) {
        ShortcutEditDialog(
            title = "ショートカットを追加",
            shortcut = null,
            onDismiss = { showAddDialog = false },
            onSave = { shortcut ->
                viewModel.save(shortcut)
                showAddDialog = false
            }
        )
    }

    // 編集ダイアログ
    editingShortcut?.let { shortcut ->
        ShortcutEditDialog(
            title = "ショートカットを編集",
            shortcut = shortcut,
            onDismiss = { editingShortcut = null },
            onSave = { updated ->
                viewModel.save(updated)
                editingShortcut = null
            }
        )
    }

    // カテゴリ別インポートダイアログ
    if (showImportDialog) {
        ImportCategoryDialog(
            existingLabels = shortcuts.map { it.label }.toSet(),
            onDismiss = { showImportDialog = false },
            onImport = { category ->
                viewModel.importCategory(category.id)
                showImportDialog = false
            }
        )
    }
}

/**
 * ショートカットの1行表示。
 * ラベル、コマンドプレビュー、スコープ（グローバル/ホスト固有）を表示する。
 */
@Composable
private fun ShortcutListItem(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = if (shortcut.hostId == null) "グローバル" else "ホスト固有 (ID: ${shortcut.hostId})"
    // コマンドの改行・制御文字を視覚的に表示
    val commandPreview = shortcut.command
        .replace("\n", "\\n")
        .replace("\u0003", "^C")
        .replace("\u0004", "^D")
        .replace("\u001A", "^Z")
        .take(60)

    ListItem(
        headlineContent = {
            Text(
                text = shortcut.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = commandPreview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = scope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

/**
 * カテゴリ別テンプレートインポートダイアログ。
 *
 * 変更理由: Claude Code / Codex / Git / Docker 等の既知コマンドを
 * カテゴリ別に一括インポートできる機能を提供する。
 */
@Composable
private fun ImportCategoryDialog(
    existingLabels: Set<String>,
    onDismiss: () -> Unit,
    onImport: (CliCommandRegistry.ToolCategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("テンプレートからインポート") },
        text = {
            LazyColumn {
                items(CliCommandRegistry.categories) { category ->
                    val newCount = category.commands.count { it.label !in existingLabels }
                    ListItem(
                        headlineContent = { Text(category.displayName) },
                        supportingContent = {
                            Text(
                                "${category.commands.size}件のコマンド" +
                                    if (newCount < category.commands.size) " (新規${newCount}件)" else ""
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { onImport(category) },
                                enabled = newCount > 0
                            ) {
                                Text(if (newCount > 0) "追加" else "追加済み")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

/**
 * ショートカットの追加・編集ダイアログ。
 * ラベル、コマンド、ホストID（空ならグローバル）を入力可能。
 */
@Composable
private fun ShortcutEditDialog(
    title: String,
    shortcut: Shortcut?,
    onDismiss: () -> Unit,
    onSave: (Shortcut) -> Unit
) {
    var label by remember { mutableStateOf(shortcut?.label ?: "") }
    var command by remember { mutableStateOf(shortcut?.command ?: "") }
    var hostIdText by remember {
        mutableStateOf(shortcut?.hostId?.toString() ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("ラベル") },
                    placeholder = { Text("例: ls") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("コマンド") },
                    placeholder = { Text("例: ls -la\\n") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "末尾に \\n を付けると自動実行されます\n" +
                        "プレースホルダ: {{hostname}}, {{username}}, {{port}} 等",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hostIdText,
                    onValueChange = { hostIdText = it },
                    label = { Text("ホストID (空欄=グローバル)") },
                    placeholder = { Text("空欄でグローバル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && command.isNotBlank()) {
                            val hostId = hostIdText.toLongOrNull()
                            val newShortcut = Shortcut(
                                id = shortcut?.id ?: java.util.UUID.randomUUID().toString(),
                                label = label.trim(),
                                command = command,
                                hostId = hostId,
                                order = shortcut?.order ?: 0
                            )
                            onSave(newShortcut)
                        }
                    },
                    enabled = label.isNotBlank() && command.isNotBlank()
                ) {
                    Text("保存")
                }
            }
        }
    )
}
