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

package io.shellpilot.app.ui.screens.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.shellpilot.app.R
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.ShellPilotIconTile
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme

@Composable
fun ContactScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    ShellPilotScaffold(
        title = stringResource(R.string.title_contact),
        subtitle = "サポート・GitHub・フォーク元情報",
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
                        text = stringResource(R.string.help_section_contact),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ログや再現手順を添えて送ると、問題を確認しやすくなります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(label = "GitHub")
                        StatusChip(label = "サポート")
                    }
                }
            }

            item {
                ContactLinkItem(
                    icon = Icons.Default.Language,
                    label = stringResource(R.string.help_website),
                    url = stringResource(R.string.help_website_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(R.string.help_github),
                    url = stringResource(R.string.help_github_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    icon = Icons.Default.BugReport,
                    label = stringResource(R.string.help_report_bug),
                    url = stringResource(R.string.help_report_bug_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.help_section_donate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item {
                CommandSurfaceCard {
                    Text(
                        text = "ConnectBot attribution",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "以下はフォーク元ConnectBotの継続開発を支援する外部リンクです。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ContactLinkItem(
                    icon = Icons.Default.Coffee,
                    label = stringResource(R.string.help_donate_github),
                    url = stringResource(R.string.help_donate_github_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    icon = Icons.Default.Coffee,
                    label = stringResource(R.string.help_donate_coffee),
                    url = stringResource(R.string.help_donate_coffee_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }
        }
    }
}

@Composable
private fun ContactLinkItem(
    icon: ImageVector,
    label: String,
    url: String,
    onClick: (String) -> Unit
) {
    CommandSurfaceCard(
        modifier = Modifier.clickable { onClick(url) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShellPilotIconTile(icon = icon, contentDescription = null)
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun ContactScreenPreview() {
    ShellPilotTheme {
        ContactScreen(
            onNavigateBack = {}
        )
    }
}
