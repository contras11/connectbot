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

package org.connectbot.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.connectbot.data.entity.Shortcut
import org.connectbot.session.CliCommandRegistry

/**
 * ターミナル画面下部に表示する2段構成ショートカットバー。
 *
 * 変更理由: プロファイル別ショートカット切替とコマンドの
 * クイック実行UIを提供するため、1段から2段レイアウトに拡張。
 *
 * 段1: プロファイルタブ (カスタム, 汎用, Git, Docker, Cloudflare, Claude Code, Codex)
 * 段2: 選択中プロファイルのショートカットチップ一覧
 *
 * WindowInsets対応:
 * windowInsetsPadding(WindowInsets.navigationBars) をSurface外ではなく
 * Column内に適用することで、Surfaceの背景色がナビゲーションバー領域まで
 * 拡張される（edge-to-edge対応）。コンテンツ行のみパディング対象とする。
 * Scaffoldがnavigationバーinsetsをconsumeしている場合は実質0pxとなり
 * 二重適用を防ぐ。
 *
 * @param customShortcuts ShortcutRepositoryから取得したユーザのカスタムショートカット
 * @param selectedProfileId 選択中のプロファイルID (null = カスタム)
 * @param onProfileChange プロファイルタブ切替時のコールバック
 * @param onShortcutClick ショートカットがタップされた時のコールバック
 * @param modifier Modifier
 */
@Composable
fun ShortcutBar(
    customShortcuts: List<Shortcut>,
    selectedProfileId: String?,
    onProfileChange: (String?) -> Unit,
    onShortcutClick: (Shortcut) -> Unit,
    modifier: Modifier = Modifier,
    // 変更理由: プロファイルタブの表示順序をユーザ設定から受け取る。
    // nullの場合はデフォルト順序(カスタム + CliCommandRegistryカテゴリ順)を使用。
    profileOrder: List<String?> = emptyList()
) {
    // 変更理由: profileOrderが指定されていればその順序でタブを構築。
    // 空の場合はデフォルト順序（カスタム + CliCommandRegistry全カテゴリ）。
    val profiles = if (profileOrder.isNotEmpty()) {
        profileOrder.mapNotNull { id ->
            if (id == null) {
                ProfileTab(id = null, label = "カスタム")
            } else {
                CliCommandRegistry.findCategory(id)?.let {
                    ProfileTab(id = it.id, label = it.displayName)
                }
            }
        }
    } else {
        buildList {
            add(ProfileTab(id = null, label = "カスタム"))
            CliCommandRegistry.categories.forEach {
                add(ProfileTab(id = it.id, label = it.displayName))
            }
        }
    }

    // 変更理由: 選択プロファイルに応じて表示するショートカットを切り替え
    val displayedShortcuts = if (selectedProfileId == null) {
        customShortcuts
    } else {
        CliCommandRegistry.findCategory(selectedProfileId)?.commands ?: emptyList()
    }

    // 変更理由: Surfaceにwindow insets paddingを置くと背景色がナビゲーション
    // バー領域まで届かない（edge-to-edge不完全）。
    // Surfaceはfillmaxwidthのみとし、navigationBars insetsはColumn内に適用する。
    // ScaffoldがconsumeWindowInsetsしている場合はColumnのpaddingが実質0になり
    // 二重パディングは発生しない。
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        // 変更理由: navigationBars insetsをColumn内に適用することで
        // Surfaceの背景はナビバー領域まで塗りつぶしつつ、
        // コンテンツはナビバー上部に収まる（edge-to-edge正対応）
        Column(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // 段1: プロファイルタブ (FilterChip) - 横スクロール可能
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .height(PROFILE_ROW_HEIGHT_DP.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    FilterChip(
                        selected = selectedProfileId == profile.id,
                        onClick = { onProfileChange(profile.id) },
                        label = {
                            Text(
                                text = profile.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // 段2: ショートカットチップ一覧 - 横スクロール可能
            if (displayedShortcuts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .height(SHORTCUT_ROW_HEIGHT_DP.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayedShortcuts.forEach { shortcut ->
                        SuggestionChip(
                            onClick = { onShortcutClick(shortcut) },
                            label = {
                                Text(
                                    text = shortcut.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            } else {
                // 変更理由: ショートカット未登録時の案内表示
                Row(
                    modifier = Modifier
                        .height(SHORTCUT_ROW_HEIGHT_DP.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ショートカットなし",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * プロファイルタブのデータ。
 * @param id CliCommandRegistryのカテゴリID (nullはカスタム)
 * @param label 表示ラベル
 */
private data class ProfileTab(val id: String?, val label: String)

/**
 * プロファイルタブ行の高さ (dp)。
 * 変更理由: Material3の最小タッチターゲット48dpを満たすよう40→48に拡大。
 */
private const val PROFILE_ROW_HEIGHT_DP = 48

/**
 * ショートカットチップ行の高さ (dp)。
 * 変更理由: 親指操作での誤タップ防止のため44→56に拡大。
 */
private const val SHORTCUT_ROW_HEIGHT_DP = 56

/**
 * ShortcutBarのコンテンツ領域の高さ (dp)。
 * navigationBarsのinsets分は含まない（ScaffoldまたはColumn内で別途処理）。
 * SessionScreenのInlinePromptボトムパディング計算に使用する。
 * 変更理由: 48 + 56 + 1(divider) = 105dp
 */
const val SHORTCUT_BAR_HEIGHT_DP = PROFILE_ROW_HEIGHT_DP + SHORTCUT_ROW_HEIGHT_DP + 1
