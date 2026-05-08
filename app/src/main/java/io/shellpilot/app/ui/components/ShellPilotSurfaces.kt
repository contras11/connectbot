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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val CommandCardShape = RoundedCornerShape(8.dp)

/**
 * ShellPilot共通の画面土台。
 *
 * 変更理由: 旧来の単色Preference画面に戻らないよう、背景・余白・top barの
 * 基本トーンを1か所へ集約する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellPilotScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            ShellPilotTopBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellPilotTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier.height(48.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        title = {
            // 変更理由: ImageGenモックの細い単一行TopBarへ揃え、画面本文側の
            // カードが情報階層を担うようにする。
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = navigationIcon,
        actions = actions
    )
}

@Composable
fun CommandSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
    val contentPadding = 6.dp
    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = CommandCardShape,
            colors = colors,
            border = BorderStroke(1.dp, borderColor)
        ) {
            // 変更理由: ImageGen参照ボードの1px境界方針に合わせ、
            // 汎用カードは強い色枠を使わず中立な面で階層を作る。
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier.fillMaxWidth(),
            shape = CommandCardShape,
            colors = colors,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (containerColor == MaterialTheme.colorScheme.surface) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            containerColor
        },
        contentColor = accent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun CommandChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
fun ShellPilotSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
fun ShellPilotIconTile(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.86f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * モック全体で使う中央状態カード。
 *
 * 変更理由: 認証、起動、空状態、エラー復旧の見た目を
 * 追加ImageGenモックのコンパクトな状態パネルへ揃える。
 */
@Composable
fun ShellPilotStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Terminal,
    chips: @Composable RowScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        CommandSurfaceCard(
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            accent = MaterialTheme.colorScheme.outlineVariant
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = chips)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

/**
 * モック準拠の共通ダイアログ。
 *
 * 変更理由: 標準AlertDialog由来の大きな余白と旧Material感を避け、確認・入力・ログ表示を
 * ShellPilotのcompact terminal cockpitへ統一する。
 */
@Composable
fun ShellPilotActionDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    destructiveConfirm: Boolean = false,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = properties
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.86f))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                content()
                if (dismissLabel != null || confirmLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        if (dismissLabel != null) {
                            androidx.compose.material3.TextButton(onClick = onDismiss) {
                                Text(dismissLabel)
                            }
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            androidx.compose.material3.TextButton(
                                onClick = onConfirm,
                                enabled = confirmEnabled
                            ) {
                                Text(
                                    text = confirmLabel,
                                    color = if (destructiveConfirm) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
