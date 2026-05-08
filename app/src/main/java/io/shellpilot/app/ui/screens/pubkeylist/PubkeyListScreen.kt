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

package io.shellpilot.app.ui.screens.pubkeylist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.shellpilot.app.R
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.ui.LocalTerminalManager
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.components.rememberBiometricPromptState
import io.shellpilot.app.ui.theme.ShellPilotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PubkeyListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val terminalManager = LocalTerminalManager.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Set TerminalManager in ViewModel
    LaunchedEffect(terminalManager) {
        viewModel.terminalManager = terminalManager
    }

    // Show snackbar for errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // File picker for importing keys
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importKeyFromUri(it) }
    }

    // File saver for exporting private keys
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportKeyToUri(uri)
        } else {
            viewModel.cancelExport()
        }
    }

    // File saver for exporting public keys
    val publicKeyFileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportPublicKeyToUri(uri)
        } else {
            viewModel.cancelPublicKeyExport()
        }
    }

    // Trigger file saver when private key export is requested
    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let {
            fileSaverLauncher.launch(viewModel.getExportFilename())
        }
    }

    // Trigger file saver when public key export is requested
    LaunchedEffect(uiState.pendingPublicKeyExport) {
        uiState.pendingPublicKeyExport?.let {
            publicKeyFileSaverLauncher.launch(viewModel.getPublicKeyExportFilename())
        }
    }

    // Biometric prompt for unlocking biometric keys
    // Returns null if FragmentActivity context is not available
    val biometricPromptState = rememberBiometricPromptState(
        onSuccess = { _ ->
            // Load the biometric key after successful authentication
            uiState.biometricKeyToUnlock?.let { pubkey ->
                viewModel.loadBiometricKey(pubkey)
            }
        },
        onError = { errorCode, errString ->
            // Show error in snackbar (unless user cancelled)
            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
                viewModel.onBiometricError(errString.toString())
            } else {
                viewModel.cancelBiometricAuth()
            }
        },
        onFailed = {
            // Don't do anything on failed attempt - user can retry
        }
    )

    // Trigger biometric prompt when needed
    LaunchedEffect(uiState.biometricKeyToUnlock, biometricPromptState) {
        uiState.biometricKeyToUnlock?.let { pubkey ->
            if (biometricPromptState != null) {
                biometricPromptState.authenticate(
                    title = resources.getString(R.string.pubkey_biometric_prompt_title),
                    subtitle = resources.getString(R.string.pubkey_biometric_prompt_subtitle, pubkey.nickname),
                    negativeButtonText = resources.getString(android.R.string.cancel)
                )
            } else {
                // Biometric not available in this context, show error
                viewModel.onBiometricError(resources.getString(R.string.pubkey_biometric_not_available))
            }
        }
    }

    // Password dialog for importing encrypted keys
    val pendingImport = uiState.pendingImport
    if (pendingImport != null) {
        ImportPasswordDialog(
            keyType = pendingImport.keyType,
            nickname = pendingImport.nickname,
            onDismiss = { viewModel.cancelImport() },
            onImport = { decryptPassword, encrypt, encryptPassword ->
                viewModel.completeImportWithPassword(decryptPassword, encrypt, encryptPassword)
            }
        )
    }

    PubkeyListScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToGenerate = onNavigateToGenerate,
        onNavigateToEdit = onNavigateToEdit,
        onDeletePubkey = viewModel::deletePubkey,
        onToggleKeyLoad = viewModel::toggleKeyLoaded,
        onCopyPublicKey = viewModel::copyPublicKey,
        onCopyPrivateKeyOpenSSH = { pubkey, onPasswordRequired ->
            viewModel.copyPrivateKeyOpenSSH(pubkey, onPasswordRequired)
        },
        onCopyPrivateKeyPem = { pubkey, onPasswordRequired ->
            viewModel.copyPrivateKeyPem(pubkey, onPasswordRequired)
        },
        onCopyPrivateKeyWithPassphrase = { pubkey, onPasswordRequired, onExportPassphraseRequest ->
            viewModel.copyPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequest)
        },
        onExportPublicKey = viewModel::requestExportPublicKey,
        onExportPrivateKeyOpenSSH = { pubkey, onPasswordRequired ->
            viewModel.requestExportPrivateKeyOpenSSH(pubkey, onPasswordRequired)
        },
        onExportPrivateKeyPem = { pubkey, onPasswordRequired ->
            viewModel.requestExportPrivateKeyPem(pubkey, onPasswordRequired)
        },
        onExportPrivateKeyWithPassphrase = { pubkey, onPasswordRequired, onExportPassphraseRequest ->
            viewModel.requestExportPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequest)
        },
        onToggleBackup = viewModel::updateBackupPermission,
        onImportKey = {
            filePickerLauncher.launch(arrayOf("*/*"))
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreenContent(
    uiState: PubkeyListUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    onDeletePubkey: (Pubkey) -> Unit,
    onToggleKeyLoad: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: (Pubkey) -> Unit,
    onCopyPrivateKeyOpenSSH: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyPem: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyWithPassphrase: (Pubkey, (Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPublicKey: (Pubkey) -> Unit,
    onExportPrivateKeyOpenSSH: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyPem: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyWithPassphrase: (Pubkey, (Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onToggleBackup: (Pubkey, Boolean) -> Unit,
    onImportKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShellPilotScaffold(
        title = stringResource(R.string.title_pubkey_list),
        subtitle = "認証キー・暗号化・ロード状態",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
        actions = {
            IconButton(onClick = onNavigateToGenerate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pubkey_generate))
            }
            IconButton(onClick = onImportKey) {
                Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.pubkey_import_existing))
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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

                uiState.pubkeys.isEmpty() -> {
                    CommandSurfaceCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        accent = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = stringResource(R.string.empty_pubkeys_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.empty_pubkeys_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(label = stringResource(R.string.pubkey_generate))
                            StatusChip(label = stringResource(R.string.pubkey_import_existing_short))
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            CommandSurfaceCard(accent = MaterialTheme.colorScheme.outlineVariant) {
                                Text(
                                    text = "公開鍵を管理",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "生成・インポート・ロード状態を確認し、SSH認証で使う鍵を切り替えます。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(label = "鍵 ${uiState.pubkeys.size}")
                                    StatusChip(label = "ロード中 ${uiState.loadedKeyNicknames.size}")
                                }
                            }
                        }
                        items(
                            items = uiState.pubkeys,
                            key = { it.id }
                        ) { pubkey ->
                            PubkeyListItem(
                                pubkey = pubkey,
                                isLoaded = uiState.loadedKeyNicknames.contains(pubkey.nickname),
                                onDelete = { onDeletePubkey(pubkey) },
                                onToggleLoad = { onToggleKeyLoad(pubkey, it) },
                                onCopyPublicKey = { onCopyPublicKey(pubkey) },
                                onCopyPrivateKeyOpenSSH = { onPasswordRequired ->
                                    onCopyPrivateKeyOpenSSH(pubkey, onPasswordRequired)
                                },
                                onCopyPrivateKeyPem = { onPasswordRequired ->
                                    onCopyPrivateKeyPem(pubkey, onPasswordRequired)
                                },
                                onCopyPrivateKeyWithPassphrase = { onPasswordRequired, onExportPassphraseRequest ->
                                    onCopyPrivateKeyWithPassphrase(pubkey, onPasswordRequired, onExportPassphraseRequest)
                                },
                                onExportPublicKey = { onExportPublicKey(pubkey) },
                                onExportPrivateKeyOpenSSH = { onPasswordRequired ->
                                    onExportPrivateKeyOpenSSH(pubkey, onPasswordRequired)
                                },
                                onExportPrivateKeyPem = { onPasswordRequired ->
                                    onExportPrivateKeyPem(pubkey, onPasswordRequired)
                                },
                                onExportPrivateKeyWithPassphrase = { onPasswordRequired, onExportPassphraseRequest ->
                                    onExportPrivateKeyWithPassphrase(pubkey, onPasswordRequired, onExportPassphraseRequest)
                                },
                                onToggleBackup = { allowBackup -> onToggleBackup(pubkey, allowBackup) },
                                onEdit = { onNavigateToEdit(pubkey) },
                                onClick = { onNavigateToEdit(pubkey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PubkeyListItem(
    pubkey: Pubkey,
    isLoaded: Boolean,
    onDelete: () -> Unit,
    onToggleLoad: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: () -> Unit,
    onCopyPrivateKeyOpenSSH: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyPem: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyWithPassphrase: ((Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPublicKey: () -> Unit,
    onExportPrivateKeyOpenSSH: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyPem: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyWithPassphrase: ((Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onToggleBackup: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var exportPassphraseCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val canBackupPrivateKey = !pubkey.isBiometric && pubkey.privateKey != null

    CommandSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accent = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                pubkey.isBiometric -> Icons.Outlined.Fingerprint
                pubkey.encrypted -> Icons.Outlined.Lock
                else -> Icons.Outlined.LockOpen
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isLoaded) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = when {
                            pubkey.isBiometric -> stringResource(R.string.pubkey_biometric_description_icon)
                            pubkey.encrypted -> stringResource(R.string.pubkey_encrypted_description)
                            else -> stringResource(R.string.pubkey_not_encrypted_description)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = pubkey.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(label = stringResource(R.string.pubkey_type_label, pubkey.type))
                    StatusChip(
                        label = if (isLoaded) {
                            stringResource(R.string.pubkey_loaded)
                        } else {
                            stringResource(R.string.pubkey_not_loaded)
                        }
                    )
                }
            }

            TextButton(
                onClick = {
                    // 変更理由: カードタップで鍵のロード状態が変わると誤操作に見えるため、
                    // ロード/解除は明示的な操作ボタンへ分離する。
                    onToggleLoad { _, callback ->
                        passwordCallback = callback
                        showPasswordDialog = true
                    }
                }
            ) {
                Text(if (isLoaded) "解除" else "ロード")
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        "「${pubkey.nickname}」のその他の操作"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Edit key
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.list_pubkey_edit))
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )

                    // Copy public key
                    val isImported = pubkey.type == "IMPORTED"
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_copy_public)) },
                        onClick = {
                            showMenu = false
                            onCopyPublicKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !isImported
                    )

                    // Export public key to file
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_export_public)) },
                        onClick = {
                            showMenu = false
                            onExportPublicKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, null)
                        },
                        enabled = !isImported
                    )

                    // Copy private key in OpenSSH format (not available for Keystore keys)
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isImported) {
                                        R.string.pubkey_copy_private
                                    } else {
                                        R.string.pubkey_copy_private_openssh
                                    }
                                )
                            )
                        },
                        onClick = {
                            showMenu = false
                            onCopyPrivateKeyOpenSSH { _, callback ->
                                passwordCallback = callback
                                showPasswordDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !pubkey.isBiometric
                    )

                    // Copy private key in PEM format (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_copy_private_pem)) },
                            onClick = {
                                showMenu = false
                                onCopyPrivateKeyPem { _, callback ->
                                    passwordCallback = callback
                                    showPasswordDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, null)
                            },
                            enabled = !pubkey.isBiometric
                        )
                    }

                    // Copy private key encrypted (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_copy_private_encrypted)) },
                            onClick = {
                                showMenu = false
                                onCopyPrivateKeyWithPassphrase(
                                    { _, callback ->
                                        passwordCallback = callback
                                        showPasswordDialog = true
                                    },
                                    { _, callback ->
                                        exportPassphraseCallback = callback
                                        showExportPassphraseDialog = true
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null)
                            },
                            enabled = !pubkey.isBiometric
                        )
                    }

                    // Export private key to file in OpenSSH format
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isImported) {
                                        R.string.pubkey_export_private
                                    } else {
                                        R.string.pubkey_export_private_openssh
                                    }
                                )
                            )
                        },
                        onClick = {
                            showMenu = false
                            onExportPrivateKeyOpenSSH { _, callback ->
                                passwordCallback = callback
                                showPasswordDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, null)
                        },
                        enabled = !pubkey.isBiometric
                    )

                    // Export private key to file in PEM format (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_export_private_pem)) },
                            onClick = {
                                showMenu = false
                                onExportPrivateKeyPem { _, callback ->
                                    passwordCallback = callback
                                    showPasswordDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, null)
                            },
                            enabled = !pubkey.isBiometric
                        )
                    }

                    // Export private key to file with encryption (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_export_private_encrypted)) },
                            onClick = {
                                showMenu = false
                                onExportPrivateKeyWithPassphrase(
                                    { _, callback ->
                                        passwordCallback = callback
                                        showPasswordDialog = true
                                    },
                                    { _, callback ->
                                        exportPassphraseCallback = callback
                                        showExportPassphraseDialog = true
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null)
                            },
                            enabled = !pubkey.isBiometric
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (pubkey.allowBackup) {
                                        R.string.pubkey_backup_exclude
                                    } else {
                                        R.string.pubkey_backup_include
                                    }
                                )
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleBackup(!pubkey.allowBackup)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, null)
                        },
                        enabled = canBackupPrivateKey
                    )

                    // Delete
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_delete)) },
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                label = if (isLoaded) "読み込み済み" else "保存済み",
                accent = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusChip(
                label = if (pubkey.encrypted) "暗号化済み" else "未暗号化",
                accent = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pubkey.isBiometric) {
                StatusChip(label = "生体認証")
            }
            StatusChip(
                label = when {
                    !canBackupPrivateKey -> stringResource(R.string.pubkey_backup_unavailable)
                    pubkey.allowBackup -> stringResource(R.string.pubkey_backup_allowed)
                    else -> stringResource(R.string.pubkey_backup_disabled)
                },
                accent = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Password dialog for unlocking key
    if (showPasswordDialog && passwordCallback != null) {
        PubkeyPasswordDialog(
            pubkey = pubkey,
            onDismiss = {
                showPasswordDialog = false
                passwordCallback = null
            },
            onProvidePassword = { password ->
                passwordCallback?.invoke(password)
                showPasswordDialog = false
                passwordCallback = null
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        PubkeyDeleteDialog(
            pubkey = pubkey,
            onDismiss = {
                showDeleteDialog = false
            },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }

    // Export passphrase dialog
    if (showExportPassphraseDialog && exportPassphraseCallback != null) {
        ExportPassphraseDialog(
            onDismiss = {
                showExportPassphraseDialog = false
                exportPassphraseCallback = null
            },
            onProvidePassphrase = { passphrase ->
                exportPassphraseCallback?.invoke(passphrase)
                showExportPassphraseDialog = false
                exportPassphraseCallback = null
            }
        )
    }
}

@Composable
private fun PubkeyPasswordDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onProvidePassword: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_unlock),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.pubkey_unlock),
        onConfirm = { onProvidePassword(password) },
        dismissLabel = stringResource(R.string.button_cancel)
    ) {
        Column {
            Text(
                text = stringResource(R.string.pubkey_unlock_message, pubkey.nickname),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.prompt_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun PubkeyDeleteDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_delete),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.delete_pos),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.delete_neg),
        destructiveConfirm = true
    ) {
        Text(stringResource(R.string.delete_message, pubkey.nickname))
    }
}

@Composable
private fun ImportPasswordDialog(
    keyType: String,
    nickname: String,
    onDismiss: () -> Unit,
    onImport: (decryptPassword: String, encrypt: Boolean, encryptPassword: String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var encryptKey by remember { mutableStateOf(true) }
    var reusePassword by remember { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val canImport = password.isNotEmpty() && (
        !encryptKey ||
            reusePassword ||
            (newPassword.isNotEmpty() && newPassword == confirmPassword)
        )

    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_import_encrypted_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.pubkey_import_button),
        confirmEnabled = canImport,
        onConfirm = {
            val encryptPassword = when {
                !encryptKey -> null
                reusePassword -> password
                else -> newPassword
            }
            onImport(password, encryptKey, encryptPassword)
        },
        dismissLabel = stringResource(R.string.button_cancel)
    ) {
        Column {
            Text(
                text = stringResource(R.string.pubkey_import_encrypted_message, nickname, keyType),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.prompt_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { encryptKey = !encryptKey }
            ) {
                Checkbox(
                    checked = encryptKey,
                    onCheckedChange = { encryptKey = it }
                )
                Text(stringResource(R.string.pubkey_import_encrypt_key))
            }

            if (encryptKey) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reusePassword = !reusePassword }
                ) {
                    Checkbox(
                        checked = reusePassword,
                        onCheckedChange = { reusePassword = it }
                    )
                    Text(stringResource(R.string.pubkey_import_reuse_password))
                }

                if (!reusePassword) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.pubkey_import_new_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.pubkey_import_confirm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportPassphraseDialog(
    onDismiss: () -> Unit,
    onProvidePassphrase: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_export_set_passphrase),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_ok),
        onConfirm = {
            if (passphrase == confirmPassphrase && passphrase.isNotEmpty()) {
                onProvidePassphrase(passphrase)
            } else {
                showError = true
            }
        },
        dismissLabel = stringResource(R.string.button_cancel)
    ) {
        Column {
            Text(
                text = stringResource(R.string.pubkey_export_passphrase_message),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = {
                    passphrase = it
                    showError = false
                },
                label = { Text(stringResource(R.string.prompt_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = confirmPassphrase,
                onValueChange = {
                    confirmPassphrase = it
                    showError = false
                },
                label = { Text(stringResource(R.string.pubkey_export_confirm_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                isError = showError
            )
            if (showError) {
                Text(
                    text = stringResource(R.string.pubkey_export_passphrase_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenEmptyPreview() {
    ShellPilotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoad = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyWithPassphrase = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyWithPassphrase = { _, _, _ -> },
            onToggleBackup = { _, _ -> },
            onImportKey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenLoadingPreview() {
    ShellPilotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = true
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoad = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyWithPassphrase = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyWithPassphrase = { _, _, _ -> },
            onToggleBackup = { _, _ -> },
            onImportKey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenPopulatedPreview() {
    ShellPilotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = listOf(
                    Pubkey(
                        id = 1,
                        nickname = "work-laptop",
                        type = "RSA",
                        encrypted = true,
                        startup = true,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0)
                    ),
                    Pubkey(
                        id = 2,
                        nickname = "home-server",
                        type = "Ed25519",
                        encrypted = false,
                        startup = false,
                        confirmation = true,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0)
                    ),
                    Pubkey(
                        id = 3,
                        nickname = "github-key",
                        type = "ECDSA",
                        encrypted = true,
                        startup = false,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0)
                    )
                ),
                isLoading = false,
                loadedKeyNicknames = setOf("home-server")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoad = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyWithPassphrase = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyWithPassphrase = { _, _, _ -> },
            onToggleBackup = { _, _ -> },
            onImportKey = {}
        )
    }
}
