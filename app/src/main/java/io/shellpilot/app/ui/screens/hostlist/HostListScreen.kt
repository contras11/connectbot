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

package io.shellpilot.app.ui.screens.hostlist

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import io.shellpilot.app.R
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.ui.LocalTerminalManager
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.DisconnectAllDialog
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.ShortcutCustomizationDialog
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme
import io.shellpilot.app.util.IconStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNavigateToConsole: (Host) -> Unit,
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToHelp: () -> Unit,
    modifier: Modifier = Modifier,
    makingShortcut: Boolean = false,
    onSelectShortcut: (Host, String?, IconStyle) -> Unit = { _, _, _ -> },
    viewModel: HostListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current

    LaunchedEffect(terminalManager) {
        terminalManager?.let { viewModel.setTerminalManager(it) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // File picker for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && uiState.exportedJson != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(uiState.exportedJson!!.toByteArray())
                    }
                    val exportResult = uiState.exportResult
                    if (exportResult != null) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.export_hosts_success,
                                exportResult.hostCount,
                                exportResult.profileCount
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_hosts_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                viewModel.clearExportedJson()
            }
        } else {
            viewModel.clearExportedJson()
        }
    }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                    if (jsonString != null) {
                        viewModel.importHosts(jsonString)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_hosts_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Show errors as Toast notifications
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Handle export result - launch file picker when JSON is ready
    LaunchedEffect(uiState.exportedJson) {
        if (uiState.exportedJson != null) {
            exportLauncher.launch(context.getString(R.string.export_hosts_filename))
        }
    }

    // Handle import result
    LaunchedEffect(uiState.importResult) {
        uiState.importResult?.let { result ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.import_hosts_success,
                    result.hostsImported,
                    result.hostsSkipped,
                    result.profilesImported,
                    result.profilesSkipped
                ),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearImportResult()
        }
    }

    var shortcutHost by remember { mutableStateOf<Host?>(null) }

    if (shortcutHost != null) {
        ShortcutCustomizationDialog(
            host = shortcutHost!!,
            onDismiss = { shortcutHost = null },
            onConfirm = { color, iconStyle ->
                onSelectShortcut(shortcutHost!!, color, iconStyle)
                shortcutHost = null
            }
        )
    }

    HostListScreenContent(
        uiState = uiState,
        makingShortcut = makingShortcut,
        onNavigateToConsole = onNavigateToConsole,
        onSelectShortcut = { host -> shortcutHost = host },
        onNavigateToEditHost = onNavigateToEditHost,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPubkeys = onNavigateToPubkeys,
        onNavigateToPortForwards = onNavigateToPortForwards,
        onNavigateToProfiles = onNavigateToProfiles,
        onNavigateToHelp = onNavigateToHelp,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onDeleteHost = viewModel::deleteHost,
        onDuplicateHost = viewModel::duplicateHost,
        onForgetHostKeys = viewModel::forgetHostKeys,
        onDisconnectHost = viewModel::disconnectHost,
        onDisconnectAll = viewModel::disconnectAll,
        onExportHosts = viewModel::exportHosts,
        onImportHosts = { importLauncher.launch(arrayOf("application/json")) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreenContent(
    uiState: HostListUiState,
    makingShortcut: Boolean = false,
    onNavigateToConsole: (Host) -> Unit,
    onSelectShortcut: (Host) -> Unit = {},
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onDeleteHost: (Host) -> Unit,
    onDuplicateHost: (Host) -> Unit,
    onForgetHostKeys: (Host) -> Unit,
    onDisconnectHost: (Host) -> Unit,
    onDisconnectAll: () -> Unit,
    onExportHosts: () -> Unit = {},
    onImportHosts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDisconnectAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
        }
    }

    val connectedCount = uiState.connectionStates.values.count { it == ConnectionState.CONNECTED }

    ShellPilotScaffold(
        title = "ShellPilot",
        subtitle = "SSH + AI CLI workspace",
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (!makingShortcut) {
                IconButton(onClick = { /* 表示フィルタは後続実装用。モックに合わせて入口を先に揃える。 */ }) {
                    Icon(Icons.Default.Search, contentDescription = "ホストを検索")
                }
                IconButton(onClick = onToggleSortOrder) {
                    Icon(Icons.Default.FilterList, contentDescription = "ホストを並び替え")
                }
                IconButton(onClick = { onNavigateToEditHost(null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hostpref_add_host))
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (uiState.sortedByColor) {
                                        R.string.list_menu_sortname
                                    } else {
                                        R.string.list_menu_sortcolor
                                    }
                                )
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleSortOrder()
                        }
                    )
                    // 変更理由: 「設定」はBottomNavigationBarで遷移可能なため
                    // 三点メニューから削除。補助的機能のみ残す。
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_list_title)) },
                        onClick = {
                            showMenu = false
                            onNavigateToProfiles()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_menu_pubkeys)) },
                        onClick = {
                            showMenu = false
                            onNavigateToPubkeys()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_menu_export_hosts)) },
                        onClick = {
                            showMenu = false
                            onExportHosts()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_menu_import_hosts)) },
                        onClick = {
                            showMenu = false
                            onImportHosts()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_menu_disconnect)) },
                        onClick = {
                            showMenu = false
                            showDisconnectAllDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.title_help)) },
                        onClick = {
                            showMenu = false
                            onNavigateToHelp()
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.hosts.isEmpty() -> {
                    EmptyCommandCenterCard(
                        onAddHost = { onNavigateToEditHost(null) },
                        onImportHosts = onImportHosts,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            // 変更理由: 追加操作はTopBarへ移し、カード上にFABを重ねない。
                            // BottomNavigationBarと端末ナビゲーション分の余白だけを確保する。
                            bottom = 92.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(label = "すべて ${uiState.hosts.size}")
                                StatusChip(label = "グループ")
                                StatusChip(label = "タグ")
                            }
                        }
                        items(
                            items = uiState.hosts,
                            key = { it.id }
                        ) { host ->
                            HostListItem(
                                host = host,
                                connectionState = uiState.connectionStates[host.id] ?: ConnectionState.UNKNOWN,
                                onClick = {
                                    if (makingShortcut) {
                                        onSelectShortcut(host)
                                    } else {
                                        onNavigateToConsole(host)
                                    }
                                },
                                onEdit = { onNavigateToEditHost(host) },
                                onPortForwards = { onNavigateToPortForwards(host) },
                                onDuplicate = { onDuplicateHost(host) },
                                onForgetHostKeys = { onForgetHostKeys(host) },
                                onDisconnect = { onDisconnectHost(host) },
                                onDelete = { onDeleteHost(host) },
                                makingShortcut = makingShortcut
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisconnectAllDialog) {
        DisconnectAllDialog(
            onDismiss = { showDisconnectAllDialog = false },
            onConfirm = {
                showDisconnectAllDialog = false
                onDisconnectAll()
            }
        )
    }
}

@Composable
private fun HostListItem(
    host: Host,
    connectionState: ConnectionState,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPortForwards: () -> Unit,
    onDuplicate: () -> Unit,
    onForgetHostKeys: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    makingShortcut: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showForgetHostKeysDialog by remember { mutableStateOf(false) }

    // 変更理由: ホスト識別色の原色表示を避けるため、一覧では細い補助線だけに使う。
    // 接続状態はchipと小さなdotへ分離し、色の意味を混同しないようにする。
    val hostIndicatorColor = hostAccentIndicatorColor(host.color)
    val statusColor = connectionStatusColor(connectionState)

    CommandSurfaceCard(
        modifier = modifier,
        onClick = onClick,
        accent = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(
                        color = hostIndicatorColor,
                        shape = RoundedCornerShape(999.dp)
                    )
            )

            Box(modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (host.protocol) {
                            "ssh" -> Icons.Default.Computer
                            "telnet" -> Icons.Default.Computer
                            else -> Icons.Default.Link
                        },
                        contentDescription = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.image_description_connected)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.image_description_disconnected)
                            ConnectionState.UNKNOWN -> null
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .padding(1.8.dp)
                        .background(
                            color = statusColor,
                            shape = CircleShape
                        )
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = host.nickname.ifBlank { host.hostname.ifBlank { host.protocol } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = host.displayEndpoint(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (!makingShortcut) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "接続",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "編集",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onPortForwards, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "転送",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_host_options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_host_edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_host_portforwards)) },
                            onClick = {
                                showMenu = false
                                onPortForwards()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Link, null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_host_duplicate)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, null)
                            }
                        )
                        if (host.protocol == "ssh") {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_host_forget_keys)) },
                                onClick = {
                                    showMenu = false
                                    showForgetHostKeysDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_host_disconnect)) },
                            onClick = {
                                showMenu = false
                                showDisconnectDialog = true
                            },
                            enabled = connectionState == ConnectionState.CONNECTED,
                            leadingIcon = {
                                Icon(Icons.Default.LinkOff, null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_host_delete)) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null)
                            }
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(label = host.protocol.uppercase())
            StatusChip(
                label = when (connectionState) {
                    ConnectionState.CONNECTED -> "接続中"
                    ConnectionState.DISCONNECTED -> "オフライン"
                    ConnectionState.UNKNOWN -> "待機"
                },
                accent = when (connectionState) {
                    ConnectionState.CONNECTED -> statusColor
                    ConnectionState.DISCONNECTED -> statusColor
                    ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (host.protocol == "ssh" && host.useKeys) {
                StatusChip(label = "鍵")
            }
            if (host.profileId != null) {
                StatusChip(label = "プロファイル ${host.profileId}")
            }
            if (host.jumpHostId != null) {
                StatusChip(label = "踏み台")
            }
        }

    }

    if (showDeleteDialog) {
        HostDeleteDialog(
            host = host,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }

    if (showDisconnectDialog) {
        HostDisconnectDialog(
            host = host,
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                showDisconnectDialog = false
                onDisconnect()
            }
        )
    }

    if (showForgetHostKeysDialog) {
        ForgetHostKeysDialog(
            host = host,
            onDismiss = { showForgetHostKeysDialog = false },
            onConfirm = {
                showForgetHostKeysDialog = false
                onForgetHostKeys()
            }
        )
    }
}

@Composable
private fun CommandCenterHeader(
    hostCount: Int,
    connectedCount: Int,
    keyReadyCount: Int,
    onImportHosts: () -> Unit,
    onNavigateToPubkeys: () -> Unit
) {
    CommandSurfaceCard(accent = MaterialTheme.colorScheme.primary) {
        Text(
            text = "コマンドセンター",
            // 変更理由: 参照モックの業務アプリ密度に合わせ、
            // カード内見出しが画面タイトルより強くならないよう調整する。
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "SSHホストとAI CLIの作業導線をここから開始します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "ホスト $hostCount")
            StatusChip(label = "接続中 $connectedCount")
            StatusChip(label = "鍵 $keyReadyCount")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onImportHosts) {
                Text("JSONからインポート")
            }
            TextButton(onClick = onNavigateToPubkeys) {
                Text("公開鍵")
            }
        }
    }
}

@Composable
private fun EmptyCommandCenterCard(
    onAddHost: () -> Unit,
    onImportHosts: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandSurfaceCard(modifier = modifier, accent = MaterialTheme.colorScheme.secondary) {
        Text(
            text = "ShellPilotワークスペースを準備",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "最初のSSHホストを追加するか、既存のJSONバックアップから接続先を取り込みます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onAddHost) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.hostpref_add_host))
        }
        TextButton(onClick = onImportHosts) {
            Text("JSONからインポート")
        }
    }
}

private fun Host.displayEndpoint(): String {
    return when (protocol) {
        "local" -> "local://${nickname.ifBlank { "device" }}"
        "ssh" -> {
            val userPart = if (username.isNotBlank()) "$username@" else ""
            "$userPart$hostname:$port"
        }
        else -> "$protocol://$hostname:$port"
    }
}

@Composable
private fun hostAccentIndicatorColor(colorString: String?): Color {
    // 変更理由: ImageGen参照ボードの中立アイコン方針に合わせ、
    // 保存済みhost.colorの色相は残しつつ、一覧ではニュートラルに強く寄せる。
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val source = parseColor(colorString)
    return lerp(neutral, source, 0.18f).copy(alpha = 0.56f)
}

@Composable
private fun connectionStatusColor(connectionState: ConnectionState): Color {
    return when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4F7C5C)
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
        ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
}

@Composable
private fun HostDeleteDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.list_host_delete),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_yes),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.button_no),
        destructiveConfirm = true
    ) {
        Text(stringResource(R.string.delete_host_confirm, host.nickname))
    }
}

@Composable
private fun HostDisconnectDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.list_host_disconnect),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_yes),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.button_no),
        destructiveConfirm = true
    ) {
        Text(stringResource(R.string.disconnect_host_alert, host.nickname))
    }
}

@Composable
private fun ForgetHostKeysDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.list_host_forget_keys),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_yes),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.button_no),
        destructiveConfirm = true
    ) {
        Text(stringResource(R.string.forget_host_keys_confirm, host.nickname))
    }
}

@Composable
private fun parseColor(colorString: String?): Color {
    if (colorString.isNullOrBlank()) {
        return MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        val colorInt = colorString.toColorInt()
        return Color(colorInt)
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenEmptyPreview() {
    ShellPilotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = false
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenLoadingPreview() {
    ShellPilotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = true
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenErrorPreview() {
    ShellPilotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = false,
                error = "Failed to load hosts from database"
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenPopulatedPreview() {
    ShellPilotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = listOf(
                    Host(
                        id = 1,
                        nickname = "Production Server",
                        protocol = "ssh",
                        username = "root",
                        hostname = "prod.example.com",
                        port = 22,
                        color = "#4CAF50"
                    ),
                    Host(
                        id = 2,
                        nickname = "Development",
                        protocol = "ssh",
                        username = "developer",
                        hostname = "dev.example.com",
                        port = 2222,
                        color = "#2196F3"
                    ),
                    Host(
                        id = 3,
                        nickname = "Local VM",
                        protocol = "ssh",
                        username = "admin",
                        hostname = "192.168.1.100",
                        port = 22,
                        color = "#FF9800"
                    )
                ),
                connectionStates = mapOf(
                    1L to ConnectionState.CONNECTED,
                    2L to ConnectionState.DISCONNECTED,
                    3L to ConnectionState.UNKNOWN
                ),
                isLoading = false
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
            onDisconnectAll = {}
        )
    }
}
