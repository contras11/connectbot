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

package io.shellpilot.app.ui.components

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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.session.CliCommandRegistry

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
 * @param compact ソフトキーボード表示中など、縦方向の圧迫を避けるために行高を抑える
 * @param modifier Modifier
 */
@Composable
fun ShortcutBar(
    customShortcuts: List<Shortcut>,
    selectedProfileId: String?,
    onProfileChange: (String?) -> Unit,
    onShortcutClick: (Shortcut) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    // 変更理由: プロファイルタブの表示順序をユーザ設定から受け取る。
    // nullの場合はデフォルト順序(カスタム + CliCommandRegistryカテゴリ順)を使用。
    profileOrder: List<String?> = emptyList(),
    hiddenProfileIds: Set<String> = emptySet()
) {
    // 変更理由: profileOrderが指定されていればその順序でタブを構築。
    // 空の場合はデフォルト順序（カスタム + CliCommandRegistry全カテゴリ）。
    val profiles = if (profileOrder.isNotEmpty()) {
        val ordered = profileOrder.mapNotNull { id ->
            if (id == null) {
                ProfileTab(id = null, label = "カスタム")
            } else {
                CliCommandRegistry.findCategory(id)?.let {
                    ProfileTab(id = it.id, label = sessionProfileLabel(it.id, it.displayName))
                }
            }
        }
        // 変更理由: アプリ更新で制御キーなど新カテゴリが増えた場合も、
        // 既存ユーザの保存済みタブ順序を壊さず末尾へ補完する。
        val knownIds = ordered.map { it.id }.toSet()
        ordered + CliCommandRegistry.categories
            .filter { it.id !in knownIds }
            .map { ProfileTab(id = it.id, label = sessionProfileLabel(it.id, it.displayName)) }
    } else {
        buildList {
            add(ProfileTab(id = null, label = "カスタム"))
            CliCommandRegistry.categories.forEach {
                add(ProfileTab(id = it.id, label = sessionProfileLabel(it.id, it.displayName)))
            }
        }
    }
    val profileRowHeight = if (compact) 32.dp else PROFILE_ROW_HEIGHT_DP.dp
    val shortcutRowHeight = if (compact) 34.dp else SHORTCUT_ROW_HEIGHT_DP.dp
    val headerRowHeight = if (compact) 26.dp else COMMAND_PANEL_HEADER_HEIGHT_DP.dp
    val rowHorizontalPadding = if (compact) 5.dp else 6.dp
    val rowSpacing = if (compact) 3.dp else 4.dp
    var commandsExpanded by rememberSaveable { mutableStateOf(true) }

    val visibleProfiles = profiles.filter { profile ->
        profile.id == null || profile.id !in hiddenProfileIds
    }
    val effectiveSelectedProfileId = if (visibleProfiles.any { it.id == selectedProfileId }) {
        selectedProfileId
    } else {
        visibleProfiles.firstOrNull()?.id
    }

    LaunchedEffect(effectiveSelectedProfileId, selectedProfileId) {
        if (effectiveSelectedProfileId != selectedProfileId) {
            onProfileChange(effectiveSelectedProfileId)
        }
    }

    // 変更理由: 選択プロファイルに応じて表示するショートカットを切り替え
    val displayedShortcuts = if (effectiveSelectedProfileId == null) {
        customShortcuts
    } else {
        CliCommandRegistry.findCategory(effectiveSelectedProfileId)?.commands ?: emptyList()
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
            // 変更理由: ImageGen参照モックに合わせ、Claude/Codexコマンド群は
            // 端末操作を邪魔しない折りたたみ可能なパネルとして扱う。
            Surface(
                onClick = { commandsExpanded = !commandsExpanded },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerRowHeight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Claude / Codex コマンド",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (commandsExpanded) "展開中 ︿" else "折りたたみ中 ﹀",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (!commandsExpanded) {
                return@Column
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))

            // 段1: プロファイルタブ (FilterChip) - 横スクロール可能
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .height(profileRowHeight)
                    .padding(horizontal = rowHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleProfiles.forEach { profile ->
                    FilterChip(
                        selected = effectiveSelectedProfileId == profile.id,
                        onClick = { onProfileChange(profile.id) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = effectiveSelectedProfileId == profile.id,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                            selectedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        label = {
                            Text(
                                text = profile.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))

            // 段2: ショートカットチップ一覧 - 横スクロール可能
            if (displayedShortcuts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .height(shortcutRowHeight)
                        .padding(horizontal = rowHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayedShortcuts.forEach { shortcut ->
                        CommandChipButton(
                            label = shortcut.label,
                            onClick = { onShortcutClick(shortcut) },
                            emphasized = effectiveSelectedProfileId == "control"
                        )
                    }
                }
            } else {
                // 変更理由: ショートカット未登録時の案内表示
                Row(
                    modifier = Modifier
                        .height(shortcutRowHeight)
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

private fun sessionProfileLabel(id: String, displayName: String): String = when (id) {
    "control" -> "制御"
    "general" -> "汎用"
    "claude_code" -> "Claude"
    "codex" -> "Codex"
    else -> displayName
}

/**
 * プロファイルタブ行の高さ (dp)。
 * 変更理由: 制御キー列と同時表示しても画面を圧迫しないよう、
 * ターミナル専用UIでは44dpへ圧縮する。
 */
private const val PROFILE_ROW_HEIGHT_DP = 34

/**
 * ショートカットチップ行の高さ (dp)。
 * 変更理由: ソフトキーボード表示時でもClaude/Codexチップを残すため、
 * 参照モックに合わせた低めの行高へ調整する。
 */
private const val SHORTCUT_ROW_HEIGHT_DP = 38

private const val COMMAND_PANEL_HEADER_HEIGHT_DP = 28

/**
 * ShortcutBarのコンテンツ領域の高さ (dp)。
 * navigationBarsのinsets分は含まない（ScaffoldまたはColumn内で別途処理）。
 * SessionScreenのInlinePromptボトムパディング計算に使用する。
 * 変更理由: 32 + 40 + 44 + 2(divider) = 118dp
 */
const val SHORTCUT_BAR_HEIGHT_DP = COMMAND_PANEL_HEADER_HEIGHT_DP + PROFILE_ROW_HEIGHT_DP + SHORTCUT_ROW_HEIGHT_DP + 2
