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

package io.shellpilot.app.ui.screens.eula

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun EulaScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShellPilotScaffold(
        title = "ライセンス",
        subtitle = "ConnectBot attribution / Apache License 2.0",
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
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CommandSurfaceCard {
                    Text(
                        text = "ShellPilot",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "ShellPilotはConnectBotをベースにしたSSHクライアントです。フォーク元への帰属とライセンスを維持します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(label = "Apache 2.0")
                        StatusChip(label = "ConnectBotベース")
                    }
                }
            }

            item {
                CommandSurfaceCard {
                    Text(
                        text = "Apache License 2.0",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = """
                        ConnectBot: simple, powerful, open-source SSH client for Android

                        Copyright © Kenny Root

                        Licensed under the Apache License, Version 2.0 (the "License");
                        you may not use this file except in compliance with the License.
                        You may obtain a copy of the License at

                            http://www.apache.org/licenses/LICENSE-2.0

                        Unless required by applicable law or agreed to in writing, software
                        distributed under the License is distributed on an "AS IS" BASIS,
                        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                        See the License for the specific language governing permissions and
                        limitations under the License.
                    """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun EulaScreenPreview() {
    ShellPilotTheme {
        EulaScreen(
            onNavigateBack = {}
        )
    }
}
