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

package org.connectbot.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.connectbot.data.entity.Host
import org.connectbot.ui.screens.hostlist.HostListScreen
import org.connectbot.ui.screens.settings.SettingsScreen
import org.connectbot.ui.screens.shortcutlist.ShortcutListScreen
import org.connectbot.util.IconStyle

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
                    onNavigateToSettings = { selectedTab = 2 },
                    onNavigateToPubkeys = onNavigateToPubkeys,
                    onNavigateToPortForwards = onNavigateToPortForwards,
                    onNavigateToProfiles = onNavigateToProfiles,
                    onNavigateToHelp = onNavigateToHelp,
                    makingShortcut = makingShortcut,
                    onSelectShortcut = onSelectShortcut
                )

                1 -> ShortcutListScreen(
                    // 変更理由: タブ表示ではArrowBackアイコンを非表示にする
                    onNavigateBack = { selectedTab = 0 },
                    showNavigationIcon = false
                )

                2 -> SettingsScreen(
                    // 変更理由: タブ表示ではArrowBackアイコンを非表示にする
                    onNavigateBack = { selectedTab = 0 },
                    onNavigateToShortcuts = { selectedTab = 1 },
                    showNavigationIcon = false
                )
            }
        }
    }
}

/**
 * 3タブのナビゲーションバー。
 *
 * 変更理由: Material3 NavigationBar を使用し、
 * ホスト一覧・ショートカット管理・設定の3タブを提供する。
 */
@Composable
private fun MainNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Computer, contentDescription = null) },
            label = { Text("ホスト") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
            label = { Text("ショートカット") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("設定") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) }
        )
    }
}
