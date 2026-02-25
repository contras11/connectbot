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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
 * 変更理由 (グルーピング対応): フラットなリスト表示から
 * グローバル/ホスト別 → カテゴリ別 の2段階グルーピング表示に変更。
 * Shortcut.category フィールドを使ってカテゴリ別にまとめる。
 *
 * 表示構造:
 *   ▼ グローバル
 *     ▼ Claude Code
 *       - claude
 *       - /help
 *     ▼ Git
 *       - git status
 *     ▼ 未分類
 *       - Ctrl+D
 *   ▼ ホスト ID: 1
 *     ▼ Docker
 *       - docker ps
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    // 変更理由: MainScreenのタブとして使用する際はナビゲーションアイコンを非表示にする。
    // SessionScreenからの単独遷移時はtrueでArrowBackを表示する。
    showNavigationIcon: Boolean = true,
    viewModel: ShortcutListViewModel = hiltViewModel()
) {
    val shortcuts by viewModel.shortcuts.collectAsState()
    val profileOrder by viewModel.profileOrder.collectAsState()
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // 変更理由: ホストグループとカテゴリグループの折り畳み状態を管理する。
    // キー: "host:${hostId}" または "cat:${hostId}:${categoryId}"
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // 変更理由: グローバル（hostId=null）とホスト別でグルーピングし、
    // さらにcategoryでサブグルーピングする。
    // グローバルは "GLOBAL" キーで、ホスト別は hostId の文字列でグルーピング。
    val grouped: Map<String, Map<String, List<Shortcut>>> = remember(shortcuts) {
        val hostGroups = shortcuts.groupBy { it.hostId?.toString() ?: "GLOBAL" }
        hostGroups.mapValues { (_, hostShortcuts) ->
            hostShortcuts.groupBy { it.category ?: "UNCATEGORIZED" }
        }
    }

    // グローバルグループをリストの先頭に固定し、ホスト別グループを後ろに並べる
    val sortedHostKeys = remember(grouped) {
        grouped.keys.sortedWith(compareBy { if (it == "GLOBAL") "" else it })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ショートカット設定") },
                navigationIcon = {
                    // 変更理由: タブ表示時はArrowBackを非表示にする
                    if (showNavigationIcon) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "戻る"
                            )
                        }
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
            // 変更理由: グルーピング表示。LazyColumnにホストグループ→カテゴリグループを
            // 折り畳み可能なExpandableセクションで表示する。
            // プロファイルタブ順序セクションをリスト先頭に追加。
            LazyColumn(
                modifier = Modifier.padding(innerPadding)
            ) {
                // 変更理由: プロファイルタブの表示順序を設定するセクション。
                // ShortcutBarのプロファイルタブ(カスタム/Claude Code/Git等)の
                // 表示順をユーザが上下ボタンで変更できるようにする。
                item(key = "profile_order_header") {
                    val profileExpanded = expandedGroups["profile_order"] ?: false
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    expandedGroups["profile_order"] = !profileExpanded
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (profileExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (profileExpanded) "折り畳む" else "展開する",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "プロファイルタブ順序",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${profileOrder.size}件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (expandedGroups["profile_order"] == true) {
                    items(
                        items = profileOrder,
                        key = { "profile_${it ?: "custom"}" }
                    ) { profileId ->
                        val profileLabel = if (profileId == null) {
                            "カスタム"
                        } else {
                            CliCommandRegistry.findCategory(profileId)?.displayName
                                ?: profileId
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = profileLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { viewModel.moveProfileUp(profileId) }
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "上へ移動",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveProfileDown(profileId) }
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下へ移動",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp
                        )
                    }
                }

                // ショートカット一覧セクション
                sortedHostKeys.forEach { hostKey ->
                    val categoryMap = grouped[hostKey] ?: return@forEach
                    val hostGroupExpanded = expandedGroups[hostKey] ?: true

                    // ホストグループヘッダー (グローバル or ホスト別)
                    item(key = "host_header_$hostKey") {
                        HostGroupHeader(
                            hostKey = hostKey,
                            shortcutCount = categoryMap.values.sumOf { it.size },
                            expanded = hostGroupExpanded,
                            onClick = {
                                expandedGroups[hostKey] = !hostGroupExpanded
                            }
                        )
                    }

                    if (hostGroupExpanded) {
                        // カテゴリ別サブグループ
                        val sortedCategoryKeys = categoryMap.keys.sortedWith(
                            compareBy { if (it == "UNCATEGORIZED") "zzz" else it }
                        )
                        sortedCategoryKeys.forEach { categoryKey ->
                            val categoryShortcuts = categoryMap[categoryKey] ?: return@forEach
                            val catGroupKey = "cat:$hostKey:$categoryKey"
                            val catExpanded = expandedGroups[catGroupKey] ?: true

                            item(key = "cat_header_${hostKey}_$categoryKey") {
                                CategoryGroupHeader(
                                    categoryKey = categoryKey,
                                    shortcutCount = categoryShortcuts.size,
                                    expanded = catExpanded,
                                    onClick = {
                                        expandedGroups[catGroupKey] = !catExpanded
                                    }
                                )
                            }

                            if (catExpanded) {
                                items(
                                    items = categoryShortcuts.sortedBy { it.order },
                                    key = { it.id }
                                ) { shortcut ->
                                    // 変更理由: 上下移動ボタンを追加し
                                    // ショートカットの表示順を変更可能にする
                                    ShortcutListItem(
                                        shortcut = shortcut,
                                        onClick = { editingShortcut = shortcut },
                                        onDelete = { viewModel.delete(shortcut.id) },
                                        onMoveUp = { viewModel.moveUp(shortcut) },
                                        onMoveDown = { viewModel.moveDown(shortcut) }
                                    )
                                }
                            }
                        }
                    }
                }

                // FAB分の余白
                item { Spacer(modifier = Modifier.height(88.dp)) }
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
            // 変更理由: label単独ではClaude CodeとCodexで"/help"等が衝突するため
            // (label, category)ペアで重複判定に変更。異なるカテゴリなら同名ラベルも追加可。
            existingPairs = shortcuts.map { it.label to it.category }.toSet(),
            onDismiss = { showImportDialog = false },
            onImport = { category ->
                viewModel.importCategory(category.id)
                showImportDialog = false
            }
        )
    }
}

/**
 * ホストグループのセクションヘッダー。
 * 変更理由: グルーピング表示のためのExpandable見出しを追加。
 */
@Composable
private fun HostGroupHeader(
    hostKey: String,
    shortcutCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val label = if (hostKey == "GLOBAL") "グローバル" else "ホスト ID: $hostKey"
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "折り畳む" else "展開する",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${shortcutCount}件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * カテゴリグループのサブセクションヘッダー。
 * 変更理由: カテゴリ別の折り畳み可能なサブヘッダーを追加。
 */
@Composable
private fun CategoryGroupHeader(
    categoryKey: String,
    shortcutCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val label = when (categoryKey) {
        "UNCATEGORIZED" -> "未分類"
        else -> CliCommandRegistry.findCategory(categoryKey)?.displayName ?: categoryKey
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "折り畳む" else "展開する",
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${shortcutCount}件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

/**
 * ショートカットの1行表示。
 * ラベル、コマンドプレビュー、上下移動ボタン、削除ボタンを表示する。
 *
 * 変更理由: 上下移動ボタンを追加し、ショートカットの表示順を
 * ユーザが自由に変更できるようにする。orderフィールドがスワップされ
 * JSON永続化後も順序が保持される。SSH接続後のShortcutBarにも反映。
 */
@Composable
private fun ShortcutListItem(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
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
            Text(
                text = commandPreview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            // 変更理由: 上下移動ボタンを追加して並び替えを可能にする
            Row {
                IconButton(onClick = onMoveUp) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "上へ移動",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveDown) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "下へ移動",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        modifier = Modifier
            .padding(start = 16.dp)
            .clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

/**
 * カテゴリ別テンプレートインポートダイアログ。
 *
 * 変更理由: Claude Code / Codex / Git / Docker 等の既知コマンドを
 * カテゴリ別に一括インポートできる機能を提供する。
 *
 * 変更理由: label単独の重複判定から(label, category)ペアに変更。
 * Claude CodeとCodexが同名コマンド("/help"等)を持つ場合でも
 * カテゴリが異なればインポート可能になる。
 */
@Composable
private fun ImportCategoryDialog(
    existingPairs: Set<Pair<String, String?>>,
    onDismiss: () -> Unit,
    onImport: (CliCommandRegistry.ToolCategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("テンプレートからインポート") },
        text = {
            LazyColumn {
                items(CliCommandRegistry.categories) { category ->
                    val newCount = category.commands.count { (it.label to category.id) !in existingPairs }
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
 * ラベル、コマンド、ホストID（空ならグローバル）、カテゴリを入力可能。
 *
 * 変更理由: categoryフィールドの入力フォームを追加。
 * CliCommandRegistryのカテゴリIDを直接入力する形式とする。
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
    // 変更理由: カテゴリ入力フィールドを追加
    var categoryText by remember { mutableStateOf(shortcut?.category ?: "") }

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
                Spacer(modifier = Modifier.height(8.dp))
                // 変更理由: カテゴリ入力フィールドを追加
                // ShortcutListScreenのグルーピング表示に使用される
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("カテゴリ (空欄=未分類)") },
                    placeholder = { Text("例: git, docker, claude_code") },
                    supportingText = {
                        Text(
                            text = "使用可能: " + CliCommandRegistry.categories.joinToString { it.id },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
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
                            // 変更理由: 空文字列はnullとして扱い未分類グループに表示
                            val category = categoryText.trim().ifBlank { null }
                            val newShortcut = Shortcut(
                                id = shortcut?.id ?: java.util.UUID.randomUUID().toString(),
                                label = label.trim(),
                                command = command,
                                hostId = hostId,
                                category = category,
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
