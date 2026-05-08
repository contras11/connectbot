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

package io.shellpilot.app.ui.screens.hints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.shellpilot.app.R
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme

data class Hint(
    val title: String,
    val description: String
)

private val hints = listOf(
    Hint(
        title = "クイック接続",
        description = "ホスト追加画面で user@example.com:22 のように入力すると、接続先をすばやく作成できます。"
    ),
    Hint(
        title = "音量キー",
        description = "設定で有効にすると、音量キーをターミナル操作の補助キーとして使えます。"
    ),
    Hint(
        title = "スクロールバック",
        description = "ターミナル履歴はスワイプで戻れます。保持する行数は設定またはプロファイルで調整できます。"
    ),
    Hint(
        title = "複数セッション",
        description = "ホスト一覧から複数の接続を開始し、セッション画面で作業中の端末を確認します。"
    ),
    Hint(
        title = "コピーと貼り付け",
        description = "端末の長押しやメニューからコピー/貼り付けを使えます。CLIの出力確認に便利です。"
    ),
    Hint(
        title = "ポート転送",
        description = "ホストカードの転送ボタンから、Local / Remote / Dynamic の転送ルールを管理できます。"
    ),
    Hint(
        title = "公開鍵",
        description = "ツールの公開鍵画面で鍵の生成、インポート、ロード状態の確認を行えます。"
    ),
    Hint(
        title = "カラースキーム",
        description = "プロファイルとカラースキームを組み合わせて、作業環境ごとの端末表示を切り替えます。"
    ),
    Hint(
        title = "画面スリープ防止",
        description = "長時間のセッションでは、設定の画面スリープ防止を有効にすると作業が中断されにくくなります。"
    ),
    Hint(
        title = "すべて切断",
        description = "ホスト一覧のメニューから、アクティブな接続をまとめて切断できます。"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HintsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShellPilotScaffold(
        title = stringResource(R.string.hints),
        subtitle = "接続・端末操作・AI CLI作業のコツ",
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
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                CommandSurfaceCard {
                    Text(
                        text = "ShellPilotを使いこなす",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "SSH端末とClaude Code / Codexの操作でよく使う導線をまとめています。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusChip(label = "日本語ヒント")
                }
            }

            items(hints) { hint ->
                CommandSurfaceCard {
                    Text(
                        text = hint.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = hint.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HintsScreenPreview() {
    ShellPilotTheme {
        HintsScreen(
            onNavigateBack = {}
        )
    }
}
