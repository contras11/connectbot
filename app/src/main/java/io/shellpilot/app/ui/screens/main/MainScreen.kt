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

package io.shellpilot.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.ui.LocalTerminalManager
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.screens.hostlist.HostListScreen
import io.shellpilot.app.ui.screens.settings.SettingsScreen
import io.shellpilot.app.ui.screens.shortcutlist.ShortcutListScreen
import io.shellpilot.app.util.IconStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ホーム画面のメインコンテナ。
 *
 * 変更理由: ホスト一覧・ショートカット管理・設定の3画面へ
 * 即座にアクセスできるBottomNavigationBarを新設。
 * Material3 NavigationBar を使用し、タブ切替でコンテンツを表示する。
 *
 * レイアウト構造:
 *   Scaffold {
 *     bottomBar: NavigationBar (ホスト / ショートカット / 設定)
 *     content: 選択タブに応じた画面を表示
 *   }
 *
 * 各タブの画面は既存のScreenコンポーザブルをそのまま使用する。
 * 内部Scaffoldとの入れ子になるが、外側Scaffoldは bottomBar のみ、
 * 内側Scaffoldは topBar のみを担当するため干渉しない。
 *
 * @param onNavigateToConsole ホスト接続時のコールバック (Session画面へ遷移)
 * @param onNavigateToEditHost ホスト編集画面への遷移
 * @param onNavigateToPubkeys 公開鍵一覧画面への遷移
 * @param onNavigateToPortForwards ポートフォワード画面への遷移
 * @param onNavigateToProfiles プロファイル一覧画面への遷移
 * @param onNavigateToHelp ヘルプ画面への遷移
 * @param makingShortcut ショートカット作成モード
 * @param onSelectShortcut ショートカット選択コールバック
 */
@Composable
fun MainScreen(
    onNavigateToConsole: (Host) -> Unit,
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToColors: () -> Unit,
    onNavigateToHelp: () -> Unit,
    modifier: Modifier = Modifier,
    makingShortcut: Boolean = false,
    onSelectShortcut: (Host, String?, IconStyle) -> Unit = { _, _, _ -> }
) {
    // 変更理由: rememberSaveable で画面回転時もタブ位置を保持する
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            // 変更理由: ショートカット作成モード時はナビバーを非表示にする
            // (ホスト選択のみが必要なため)
            if (!makingShortcut) {
                MainNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        // 変更理由: 外側Scaffoldの innerPadding (NavigationBar分の bottom padding) を
        // Box に適用することで、内側の各画面がNavigationBarに重ならない。
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> HostListScreen(
                    onNavigateToConsole = onNavigateToConsole,
                    onNavigateToEditHost = onNavigateToEditHost,
                    // 変更理由: メニューの「設定」をタブ切替に変更
                    onNavigateToSettings = { selectedTab = 4 },
                    onNavigateToPubkeys = onNavigateToPubkeys,
                    onNavigateToPortForwards = onNavigateToPortForwards,
                    onNavigateToProfiles = onNavigateToProfiles,
                    onNavigateToHelp = onNavigateToHelp,
                    makingShortcut = makingShortcut,
                    onSelectShortcut = onSelectShortcut
                )

                1 -> SessionOverviewTab(
                    onNavigateToHosts = { selectedTab = 0 }
                )

                2 -> ShortcutListScreen(
                    // 変更理由: タブ表示ではArrowBackアイコンを非表示にする
                    onNavigateBack = { selectedTab = 0 },
                    showNavigationIcon = false
                )

                3 -> ToolsHubTab(
                    onNavigateToPubkeys = onNavigateToPubkeys,
                    onNavigateToProfiles = onNavigateToProfiles,
                    onNavigateToColors = onNavigateToColors,
                    onNavigateToHelp = onNavigateToHelp,
                    onNavigateToHosts = { selectedTab = 0 }
                )

                4 -> SettingsScreen(
                    // 変更理由: タブ表示ではArrowBackアイコンを非表示にする
                    onNavigateBack = { selectedTab = 0 },
                    onNavigateToShortcuts = { selectedTab = 2 },
                    showNavigationIcon = false
                )
            }
        }
    }
}

@Composable
private fun SessionOverviewTab(
    onNavigateToHosts: () -> Unit
) {
    val terminalManager = LocalTerminalManager.current
    val bridges by terminalManager?.bridgesFlow?.collectAsState(initial = emptyList())
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptyList<io.shellpilot.app.service.TerminalBridge>())
        }
    val disconnected by terminalManager?.disconnectedFlow?.collectAsState(initial = emptyList())
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptyList<Host>())
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CommandSurfaceCard {
                Text(
                    text = "セッション",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "接続中の端末を確認し、必要な場合はホスト一覧から再接続します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = "接続中 ${bridges.size}")
                    StatusChip(label = "切断 ${disconnected.size}")
                }
            }
        }
        if (bridges.isEmpty() && disconnected.isEmpty()) {
            item {
                CommandSurfaceCard {
                    Text(
                        text = "アクティブなセッションはありません",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ホストカードの接続ボタンからSSH、Telnet、Localセッションを開始できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateToHosts) {
                        Text("ホストを開く")
                    }
                }
            }
        }
        items(bridges, key = { it.host.id }) { bridge ->
            CommandSurfaceCard {
                Text(
                    text = bridge.host.nickname,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = bridge.host.getUri().toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = if (bridge.isDisconnected) "切断済み" else "接続中")
                    StatusChip(label = bridge.host.protocol.uppercase())
                }
            }
        }
        if (disconnected.isNotEmpty()) {
            item {
                Text(
                    text = "最近切断したセッション",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(disconnected, key = { it.id }) { host ->
                CommandSurfaceCard {
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(host.nickname, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = host.getUri().toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusChip(label = "切断")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHubTab(
    onNavigateToPubkeys: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToColors: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToHosts: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CommandSurfaceCard {
                Text(
                    text = "ツール",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "鍵、転送、プロファイル、配色、ヘルプを作業単位で管理します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = "SSH")
                    StatusChip(label = "AI CLI")
                    StatusChip(label = "JSON")
                }
            }
        }
        item {
            ToolHubCard(
                icon = Icons.Default.Key,
                title = "公開鍵",
                summary = "生成、インポート、読み込み状態を管理します。",
                onClick = onNavigateToPubkeys
            )
        }
        item {
            ToolHubCard(
                icon = Icons.Default.Link,
                title = "ポート転送",
                summary = "対象ホストのカードにある転送ボタンから設定します。",
                onClick = onNavigateToHosts
            )
        }
        item {
            ToolHubCard(
                icon = Icons.Default.Terminal,
                title = "プロファイル",
                summary = "フォント、文字サイズ、端末エミュレーションを調整します。",
                onClick = onNavigateToProfiles
            )
        }
        item {
            ToolHubCard(
                icon = Icons.Default.Palette,
                title = "カラースキーム",
                summary = "ANSIパレットと端末プレビューを確認します。",
                onClick = onNavigateToColors
            )
        }
        item {
            ToolHubCard(
                icon = Icons.Default.MoreVert,
                title = "ヘルプとログ",
                summary = "ヒント、キーボードショートカット、ログ表示を開きます。",
                onClick = onNavigateToHelp
            )
        }
    }
}

@Composable
private fun ToolHubCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    CommandSurfaceCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 5タブのナビゲーションバー。
 *
 * 変更理由: ImageGenの全画面モックに合わせ、ホスト・セッション・
 * ショートカット・ツール・設定を常時行き来できる構成にする。
 */
@Composable
private fun MainNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        modifier = Modifier.height(58.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NavigationBarItem(
            icon = { MainNavIcon(Icons.Default.Computer) },
            label = { MainNavLabel("ホスト") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = itemColors
        )
        NavigationBarItem(
            icon = { MainNavIcon(Icons.Default.Terminal) },
            label = { MainNavLabel("セッション") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            colors = itemColors
        )
        NavigationBarItem(
            icon = { MainNavIcon(Icons.Default.Terminal) },
            label = { MainNavLabel("ショートカット") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            colors = itemColors
        )
        NavigationBarItem(
            icon = { MainNavIcon(Icons.Default.MoreVert) },
            label = { MainNavLabel("ツール") },
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            colors = itemColors
        )
        NavigationBarItem(
            icon = { MainNavIcon(Icons.Default.Settings) },
            label = { MainNavLabel("設定") },
            selected = selectedTab == 4,
            onClick = { onTabSelected(4) },
            colors = itemColors
        )
    }
}

@Composable
private fun MainNavIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun MainNavLabel(label: String) {
    Text(
        text = label,
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}
