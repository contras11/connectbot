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

package io.shellpilot.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.shellpilot.app.BuildConfig
import io.shellpilot.app.R
import io.shellpilot.app.ui.ObservePermissionOnResume
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.FontDownloadProgressDialog
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme
import io.shellpilot.app.util.NotificationPermissionHelper
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToShortcuts: () -> Unit = {},
    onNavigateToPubkeys: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToColors: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    // 変更理由: MainScreenのタブとして使用する際はナビゲーションアイコンを非表示にする。
    showNavigationIcon: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settingsOpenFailedMessage = stringResource(R.string.settings_open_failed)

    // Permission launcher for notification permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Check the actual permission status instead of relying on the launcher result.
        // If user went to settings and granted permission, the result will be false but
        // the actual permission may be granted.
        val actuallyGranted = NotificationPermissionHelper.isNotificationPermissionGranted(context)
        viewModel.onNotificationPermissionResult(actuallyGranted)
    }

    // Listen for permission request events
    LaunchedEffect(Unit) {
        viewModel.requestNotificationPermission.collect {
            if (NotificationPermissionHelper.isNotificationPermissionGranted(context)) {
                // Permission already granted
                viewModel.onNotificationPermissionResult(true)
            } else {
                // Request permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // No permission needed on older versions
                    viewModel.onNotificationPermissionResult(true)
                }
            }
        }
    }

    // State for showing permission denied dialog
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    // Listen for permission denied dialog events
    LaunchedEffect(Unit) {
        viewModel.showPermissionDeniedDialog.collect {
            showPermissionDeniedDialog = true
        }
    }

    // 変更理由: resume時の確認は受動チェックとして扱い、ユーザー操作なしに
    // CONNECTION_PERSIST をfalseへ書き戻さない。
    ObservePermissionOnResume { isGranted ->
        viewModel.onNotificationPermissionPassiveCheck(isGranted)
    }

    // Show permission denied dialog if needed
    if (showPermissionDeniedDialog) {
        NotificationPermissionDeniedDialog(
            onDismiss = {
                showPermissionDeniedDialog = false
            },
            onOpenSettings = {
                showPermissionDeniedDialog = false
                // Open app settings so user can grant notification permission
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                // 変更理由: 制限端末でOS設定画面を開けない場合も設定画面自体は落とさない。
                runCatching {
                    context.startActivity(intent)
                }.onFailure {
                    Toast.makeText(context, settingsOpenFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    SettingsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToShortcuts = onNavigateToShortcuts,
        onNavigateToPubkeys = onNavigateToPubkeys,
        onNavigateToProfiles = onNavigateToProfiles,
        onNavigateToColors = onNavigateToColors,
        onNavigateToHelp = onNavigateToHelp,
        showNavigationIcon = showNavigationIcon,
        onAuthOnLaunchChange = viewModel::updateAuthOnLaunch,
        onMemkeysChange = viewModel::updateMemkeys,
        onConnPersistChange = viewModel::updateConnPersist,
        onWifilockChange = viewModel::updateWifilock,
        onBackupkeysChange = viewModel::updateBackupkeys,
        onScrollbackChange = viewModel::updateScrollback,
        onAddCustomTerminalType = viewModel::addCustomTerminalType,
        onRemoveCustomTerminalType = viewModel::removeCustomTerminalType,
        onAddCustomFont = viewModel::addCustomFont,
        onRemoveCustomFont = viewModel::removeCustomFont,
        onClearFontError = viewModel::clearFontValidationError,
        onImportLocalFont = viewModel::importLocalFont,
        onDeleteLocalFont = viewModel::deleteLocalFont,
        onClearImportError = viewModel::clearFontImportError,
        onDefaultProfileChange = viewModel::updateDefaultProfile,
        onLanguageChange = viewModel::updateLanguage,
        onFullscreenChange = viewModel::updateFullscreen,
        onTitleBarHideChange = viewModel::updateTitleBarHide,
        onVolumeFontChange = viewModel::updateVolumeFont,
        onAlwaysVisibleChange = viewModel::updateAlwaysVisible,
        onShiftFkeysChange = viewModel::updateShiftFkeys,
        onCtrlFkeysChange = viewModel::updateCtrlFkeys,
        onStickyModifiersChange = viewModel::updateStickyModifiers,
        onKeyModeChange = viewModel::updateKeyMode,
        onCameraChange = viewModel::updateCamera,
        onBumpyArrowsChange = viewModel::updateBumpyArrows,
        onBellChange = viewModel::updateBell,
        onBellVolumeChange = viewModel::updateBellVolume,
        onBellVibrateChange = viewModel::updateBellVibrate,
        onBellNotificationChange = viewModel::updateBellNotification,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onAuthOnLaunchChange: (Boolean) -> Unit,
    onMemkeysChange: (Boolean) -> Unit,
    onConnPersistChange: (Boolean) -> Unit,
    onWifilockChange: (Boolean) -> Unit,
    onBackupkeysChange: (Boolean) -> Unit,
    onScrollbackChange: (String) -> Unit,
    onAddCustomTerminalType: (String) -> Unit,
    onRemoveCustomTerminalType: (String) -> Unit,
    onAddCustomFont: (String) -> Unit,
    onRemoveCustomFont: (String) -> Unit,
    onClearFontError: () -> Unit,
    onImportLocalFont: (Uri, String) -> Unit,
    onDeleteLocalFont: (String) -> Unit,
    onClearImportError: () -> Unit,
    onDefaultProfileChange: (Long) -> Unit,
    onLanguageChange: (String) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onTitleBarHideChange: (Boolean) -> Unit,
    onVolumeFontChange: (Boolean) -> Unit,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onShiftFkeysChange: (Boolean) -> Unit,
    onCtrlFkeysChange: (Boolean) -> Unit,
    onStickyModifiersChange: (String) -> Unit,
    onKeyModeChange: (String) -> Unit,
    onCameraChange: (String) -> Unit,
    onBumpyArrowsChange: (Boolean) -> Unit,
    onBellChange: (Boolean) -> Unit,
    onBellVolumeChange: (Float) -> Unit,
    onBellVibrateChange: (Boolean) -> Unit,
    onBellNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToShortcuts: () -> Unit = {},
    onNavigateToPubkeys: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToColors: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    // 変更理由: MainScreenのタブ表示時はArrowBackアイコンを非表示にする
    showNavigationIcon: Boolean = true
) {
    // 変更理由: ボタンに見えるカテゴリカードを実際の詳細画面切替に接続し、
    // モック由来の押せない装飾カードを残さない。
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedSettingsCategoryFromSavedName(selectedCategoryName)

    ShellPilotScaffold(
        title = selectedCategory?.title ?: stringResource(R.string.title_settings),
        subtitle = selectedCategory?.summary ?: "接続・ターミナル・AIショートカット",
        navigationIcon = {
            // 変更理由: タブ表示時でもカテゴリ詳細からは設定トップへ戻れるようにする。
            if (showNavigationIcon || selectedCategory != null) {
                IconButton(
                    onClick = {
                        if (selectedCategory != null) {
                            selectedCategoryName = null
                        } else {
                            onNavigateBack()
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (selectedCategory != null) "設定カテゴリに戻る" else "戻る"
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp
            )
        ) {
            if (selectedCategory == null) {
                item {
                    CommandSurfaceCard(accent = MaterialTheme.colorScheme.secondary) {
                        Text(
                            text = "ShellPilot設定",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "必要なカテゴリを選ぶと、該当する設定だけを編集できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    PreferenceCategory(title = "設定カテゴリ")
                }
                SettingsCategory.entries.forEach { category ->
                    item(key = category.name) {
                        SettingsCategoryCard(
                            icon = category.icon,
                            title = category.title,
                            summary = category.summary,
                            onClick = { selectedCategoryName = category.name }
                        )
                    }
                }
            }

            if (selectedCategory == SettingsCategory.CONNECTION) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.CONNECTION)
                }
                if (uiState.canAuthenticate) {
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.pref_auth_on_launch_title),
                            summary = stringResource(R.string.pref_auth_on_launch_summary),
                            checked = uiState.authOnLaunch,
                            onCheckedChange = onAuthOnLaunchChange
                        )
                    }
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_memkeys_title),
                        summary = stringResource(R.string.pref_memkeys_summary),
                        checked = uiState.memkeys,
                        onCheckedChange = onMemkeysChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_conn_persist_title),
                        summary = stringResource(R.string.pref_conn_persist_summary),
                        checked = uiState.connPersist,
                        onCheckedChange = onConnPersistChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_wifilock_title),
                        summary = stringResource(R.string.pref_wifilock_summary),
                        checked = uiState.wifilock,
                        onCheckedChange = onWifilockChange
                    )
                }
            }

            if (selectedCategory == SettingsCategory.TERMINAL) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.TERMINAL)
                }
                item {
                    TextPreference(
                        title = stringResource(R.string.pref_scrollback_title),
                        summary = stringResource(R.string.pref_scrollback_summary),
                        value = uiState.scrollback,
                        onValueChange = onScrollbackChange
                    )
                }
                item {
                    AddCustomTerminalTypePreference(
                        customTerminalTypes = uiState.customTerminalTypes,
                        onAddTerminalType = onAddCustomTerminalType,
                        onRemoveTerminalType = onRemoveCustomTerminalType
                    )
                }
                if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
                    item {
                        AddCustomFontPreference(
                            customFonts = uiState.customFonts,
                            validationInProgress = uiState.fontValidationInProgress,
                            validationError = uiState.fontValidationError,
                            onAddFont = onAddCustomFont,
                            onRemoveFont = onRemoveCustomFont,
                            onClearError = onClearFontError
                        )
                    }
                }
                item {
                    LocalFontPreference(
                        localFonts = uiState.localFonts,
                        importInProgress = uiState.fontImportInProgress,
                        importError = uiState.fontImportError,
                        onImportFont = onImportLocalFont,
                        onDeleteFont = onDeleteLocalFont,
                        onClearError = onClearImportError
                    )
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Terminal,
                        title = "端末プロファイルを編集",
                        summary = "実際の端末フォント、文字サイズ、配色はプロファイルで適用します。",
                        onClick = onNavigateToProfiles
                    )
                }
                item {
                    val selectedProfile = if (uiState.defaultProfileId == 0L) {
                        null
                    } else {
                        uiState.availableProfiles.find { it.id == uiState.defaultProfileId }
                    }
                    val noneLabel = stringResource(R.string.pref_default_profile_none)
                    val defaultProfileLabel = stringResource(R.string.profile_default_name)
                    fun profileDisplayName(name: String): String = if (name == "Default") defaultProfileLabel else name
                    val profileEntries = listOf(noneLabel to "0") +
                        uiState.availableProfiles.map { profileDisplayName(it.name) to it.id.toString() }
                    ListPreference(
                        title = stringResource(R.string.pref_default_profile_title),
                        summary = selectedProfile?.let { profileDisplayName(it.name) } ?: noneLabel,
                        value = uiState.defaultProfileId.toString(),
                        entries = profileEntries,
                        onValueChange = { onDefaultProfileChange(it.toLong()) }
                    )
                }
            }

            if (selectedCategory == SettingsCategory.SHORTCUTS) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.SHORTCUTS)
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Keyboard,
                        title = "ショートカット設定を開く",
                        summary = "Claude Code / Codex の表示タブ、コマンド、公式テンプレートを管理します。",
                        onClick = onNavigateToShortcuts
                    )
                }
            }

            if (selectedCategory == SettingsCategory.DISPLAY) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.DISPLAY)
                }
                item {
                    val context = LocalContext.current
                    val systemDefaultLabel = stringResource(R.string.pref_language_system_default)
                    val languageEntries = remember {
                        listOf("" to systemDefaultLabel) + buildAvailableLanguageList(context)
                    }
                    val currentLabel = if (uiState.language.isEmpty()) {
                        systemDefaultLabel
                    } else {
                        languageEntries.find { it.first == uiState.language }?.second
                            ?: uiState.language
                    }
                    ListPreference(
                        title = stringResource(R.string.pref_language_title),
                        summary = currentLabel,
                        value = uiState.language,
                        entries = languageEntries.map { (tag, label) -> label to tag },
                        onValueChange = onLanguageChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_fullscreen_title),
                        summary = stringResource(R.string.pref_fullscreen_summary),
                        checked = uiState.fullscreen,
                        onCheckedChange = onFullscreenChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_titlebarhide_title),
                        summary = stringResource(R.string.pref_titlebarhide_summary),
                        checked = uiState.titlebarhide,
                        onCheckedChange = onTitleBarHideChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_volumefont_title),
                        summary = stringResource(R.string.pref_volumefont_summary),
                        checked = uiState.volumefont,
                        onCheckedChange = onVolumeFontChange
                    )
                }
                item {
                    PreferenceCategory(title = stringResource(R.string.pref_keyboard_category))
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_alwaysvisible_title),
                        summary = stringResource(R.string.pref_alwaysvisible_summary),
                        checked = uiState.alwaysvisible,
                        onCheckedChange = onAlwaysVisibleChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_shiftfkeys_title),
                        summary = stringResource(R.string.pref_shiftfkeys_summary),
                        checked = uiState.shiftfkeys,
                        onCheckedChange = onShiftFkeysChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_ctrlfkeys_title),
                        summary = stringResource(R.string.pref_ctrlfkeys_summary),
                        checked = uiState.ctrlfkeys,
                        onCheckedChange = onCtrlFkeysChange
                    )
                }
                item {
                    ListPreference(
                        title = stringResource(R.string.pref_stickymodifiers_title),
                        summary = when (uiState.stickymodifiers) {
                            "no" -> stringResource(R.string.no)
                            "alt" -> stringResource(R.string.only_alt)
                            "yes" -> stringResource(R.string.yes)
                            else -> uiState.stickymodifiers
                        },
                        value = uiState.stickymodifiers,
                        entries = listOf(
                            stringResource(R.string.no) to "no",
                            stringResource(R.string.only_alt) to "alt",
                            stringResource(R.string.yes) to "yes"
                        ),
                        onValueChange = onStickyModifiersChange
                    )
                }
                item {
                    ListPreference(
                        title = stringResource(R.string.pref_keymode_title),
                        summary = when (uiState.keymode) {
                            "Use right-side keys" -> stringResource(R.string.list_keymode_right)
                            "Use left-side keys" -> stringResource(R.string.list_keymode_left)
                            "none" -> stringResource(R.string.list_keymode_none)
                            else -> uiState.keymode
                        },
                        value = uiState.keymode,
                        entries = listOf(
                            stringResource(R.string.list_keymode_right) to "Use right-side keys",
                            stringResource(R.string.list_keymode_left) to "Use left-side keys",
                            stringResource(R.string.list_keymode_none) to "none"
                        ),
                        onValueChange = onKeyModeChange
                    )
                }
                item {
                    val cameraSummary = when (uiState.camera) {
                        "Ctrl+A then Space" -> stringResource(R.string.list_camera_ctrlaspace_description)
                        "Ctrl+A" -> stringResource(R.string.list_camera_ctrla_description)
                        "Esc" -> stringResource(R.string.list_camera_esc_description)
                        "Esc+A" -> stringResource(R.string.list_camera_esc_a_description)
                        "None" -> stringResource(R.string.list_camera_none_description)
                        "text_input" -> stringResource(R.string.list_camera_text_input_description)
                        else -> uiState.camera
                    }
                    ListPreference(
                        title = stringResource(R.string.pref_camera_title),
                        summary = cameraSummary,
                        value = uiState.camera,
                        entries = listOf(
                            stringResource(R.string.list_camera_ctrlaspace) to "Ctrl+A then Space",
                            stringResource(R.string.list_camera_ctrla) to "Ctrl+A",
                            stringResource(R.string.list_camera_esc) to "Esc",
                            stringResource(R.string.list_camera_esc_a) to "Esc+A",
                            stringResource(R.string.list_camera_none) to "None",
                            stringResource(R.string.list_camera_text_input) to "text_input"
                        ),
                        onValueChange = onCameraChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_bumpyarrows_title),
                        summary = stringResource(R.string.pref_bumpyarrows_summary),
                        checked = uiState.bumpyarrows,
                        onCheckedChange = onBumpyArrowsChange
                    )
                }
            }

            if (selectedCategory == SettingsCategory.NOTIFICATIONS) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.NOTIFICATIONS)
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_bell_title),
                        summary = stringResource(R.string.pref_bell_summary),
                        checked = uiState.bell,
                        onCheckedChange = onBellChange
                    )
                }
                item {
                    SliderPreference(
                        title = stringResource(R.string.pref_bell_volume_title),
                        value = uiState.bellVolume,
                        onValueChange = onBellVolumeChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_bell_vibrate_title),
                        summary = stringResource(R.string.pref_bell_vibrate_summary),
                        checked = uiState.bellVibrate,
                        onCheckedChange = onBellVibrateChange
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_bell_notification_title),
                        summary = stringResource(R.string.pref_bell_notification_summary),
                        checked = uiState.bellNotification,
                        onCheckedChange = onBellNotificationChange
                    )
                }
            }

            if (selectedCategory == SettingsCategory.DATA) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.DATA)
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.pref_backupkeys_title),
                        summary = stringResource(R.string.pref_backupkeys_summary),
                        checked = uiState.backupkeys,
                        onCheckedChange = onBackupkeysChange
                    )
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Key,
                        title = "公開鍵を管理",
                        summary = "鍵の生成、インポート、読み込み状態を確認します。",
                        onClick = onNavigateToPubkeys
                    )
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Terminal,
                        title = "プロファイルを管理",
                        summary = "フォント、文字サイズ、端末エミュレーションを編集します。",
                        onClick = onNavigateToProfiles
                    )
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Palette,
                        title = "カラースキームを管理",
                        summary = "ANSIパレット、端末プレビュー、色選択を開きます。",
                        onClick = onNavigateToColors
                    )
                }
            }

            if (selectedCategory == SettingsCategory.ABOUT) {
                item {
                    SettingsDetailIntro(category = SettingsCategory.ABOUT)
                }
                item {
                    SettingActionCard(
                        icon = Icons.Default.Info,
                        title = "ヘルプとログを開く",
                        summary = "ヒント、ログ、問い合わせ、ライセンスを確認します。",
                        onClick = onNavigateToHelp
                    )
                }
                item {
                    CommandSurfaceCard {
                        Text("バージョン", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (uiState.fontDownloadInProgress) {
        FontDownloadProgressDialog()
    }
}

private fun buildAvailableLanguageList(context: android.content.Context): List<Pair<String, String>> {
    val localeTags = mutableListOf<String>()
    val parser = context.resources.getXml(R.xml._generated_res_locale_config)
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
            val tag = parser.getAttributeValue(
                "http://schemas.android.com/apk/res/android",
                "name"
            )
            if (tag != null) {
                localeTags.add(tag)
            }
        }
    }
    parser.close()
    return localeTags
        .map { tag ->
            val locale = Locale.forLanguageTag(tag)
            val canonicalTag = locale.toLanguageTag()
            val nativeName = locale.getDisplayName(locale)
                .replaceFirstChar { it.titlecase(locale) }
            canonicalTag to nativeName
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

internal fun selectedSettingsCategoryFromSavedName(name: String?): SettingsCategory? {
    // 変更理由: 古いsaved stateに存在しないenum名が残っていてもクラッシュせず、
    // 設定トップへ戻して通常操作を続けられるようにする。
    return name?.let { runCatching { SettingsCategory.valueOf(it) }.getOrNull() }
}

internal enum class SettingsCategory {
    CONNECTION,
    TERMINAL,
    SHORTCUTS,
    DISPLAY,
    NOTIFICATIONS,
    DATA,
    ABOUT
}

private val SettingsCategory.title: String
    get() = when (this) {
        SettingsCategory.CONNECTION -> "接続"
        SettingsCategory.TERMINAL -> "ターミナル"
        SettingsCategory.SHORTCUTS -> "AIショートカット"
        SettingsCategory.DISPLAY -> "表示とキー"
        SettingsCategory.NOTIFICATIONS -> "通知"
        SettingsCategory.DATA -> "データ管理"
        SettingsCategory.ABOUT -> "アプリ情報"
    }

private val SettingsCategory.summary: String
    get() = when (this) {
        SettingsCategory.CONNECTION -> "認証、接続維持、Wi-Fi"
        SettingsCategory.TERMINAL -> "スクロールバック、TERM、フォント、プロファイル"
        SettingsCategory.SHORTCUTS -> "Claude Code / Codex コマンド、表示タブ"
        SettingsCategory.DISPLAY -> "言語、全画面、タイトルバー、補助キー"
        SettingsCategory.NOTIFICATIONS -> "ベル、振動、バックグラウンド通知"
        SettingsCategory.DATA -> "秘密鍵バックアップ、公開鍵、プロファイル、配色"
        SettingsCategory.ABOUT -> "ヘルプ、ログ、ライセンス、フォーク元情報"
    }

private val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.CONNECTION -> Icons.Default.Link
        SettingsCategory.TERMINAL -> Icons.Default.Terminal
        SettingsCategory.SHORTCUTS -> Icons.Default.Keyboard
        SettingsCategory.DISPLAY -> Icons.Default.Palette
        SettingsCategory.NOTIFICATIONS -> Icons.Default.Notifications
        SettingsCategory.DATA -> Icons.Default.Storage
        SettingsCategory.ABOUT -> Icons.Default.Info
    }

@Composable
private fun SettingsDetailIntro(
    category: SettingsCategory,
    modifier: Modifier = Modifier
) {
    CommandSurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    category.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandSurfaceCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingActionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCategoryCard(
        icon = icon,
        title = title,
        summary = summary,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CommandSurfaceCard(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        accent = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun TextPreference(
    title: String,
    summary: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    CommandSurfaceCard(
        modifier = modifier,
        onClick = { showDialog = true },
        accent = MaterialTheme.colorScheme.primary
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        TextPreferenceDialog(
            title = title,
            value = value,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TextPreferenceDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(value) }

    ShellPilotActionDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_ok),
        onConfirm = { onConfirm(textValue) },
        dismissLabel = stringResource(R.string.delete_neg)
    ) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ListPreference(
    title: String,
    summary: String,
    value: String,
    entries: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = entries.firstOrNull { it.second == value }?.first
        ?: value.ifBlank { "未設定" }

    CommandSurfaceCard(
        modifier = modifier,
        onClick = { showDialog = true },
        accent = MaterialTheme.colorScheme.primary
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusChip(label = selectedLabel)
    }

    if (showDialog) {
        ListPreferenceDialog(
            title = title,
            value = value,
            entries = entries,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ListPreferenceDialog(
    title: String,
    value: String,
    entries: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    ShellPilotActionDialog(
        title = title,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.delete_neg)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEach { (label, entryValue) ->
                CommandSurfaceCard(onClick = { onConfirm(entryValue) }) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ListPreferenceWithCustom(
    title: String,
    summary: String,
    value: String,
    entries: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    customLabel: String = "カスタム..."
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = entries.firstOrNull { it.second == value }?.first
        ?: value.ifBlank { "未設定" }

    CommandSurfaceCard(
        modifier = modifier,
        onClick = { showDialog = true },
        accent = MaterialTheme.colorScheme.primary
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusChip(label = selectedLabel)
    }

    if (showDialog) {
        ListPreferenceWithCustomDialog(
            title = title,
            value = value,
            entries = entries,
            customLabel = customLabel,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ListPreferenceWithCustomDialog(
    title: String,
    value: String,
    entries: List<Pair<String, String>>,
    customLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customValue by remember { mutableStateOf(value) }

    if (showCustomInput) {
        ShellPilotActionDialog(
            title = title,
            onDismiss = {
                showCustomInput = false
                onDismiss()
            },
            confirmLabel = stringResource(R.string.button_ok),
            confirmEnabled = customValue.isNotBlank(),
            onConfirm = {
                if (customValue.isNotBlank()) {
                    onConfirm(customValue)
                }
            },
            dismissLabel = stringResource(R.string.button_cancel)
        ) {
            OutlinedTextField(
                value = customValue,
                onValueChange = { customValue = it },
                label = { Text(stringResource(R.string.dialog_custom_value_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        ShellPilotActionDialog(
            title = title,
            onDismiss = onDismiss,
            dismissLabel = stringResource(R.string.delete_neg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEach { (label, entryValue) ->
                    CommandSurfaceCard(onClick = { onConfirm(entryValue) }) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                }
                CommandSurfaceCard(onClick = {
                    customValue = value
                    showCustomInput = true
                }) {
                    Text(
                        text = customLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    CommandSurfaceCard(modifier = modifier, accent = MaterialTheme.colorScheme.tertiary) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddCustomTerminalTypePreference(
    customTerminalTypes: List<String>,
    onAddTerminalType: (String) -> Unit,
    onRemoveTerminalType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTerminalType by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        CommandSurfaceCard(onClick = { showAddDialog = true }) {
            Text(stringResource(R.string.pref_customterminal_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.pref_customterminal_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Show existing custom terminal types with remove option
        customTerminalTypes.forEach { terminalType ->
            CommandSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(terminalType, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveTerminalType(terminalType) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ShellPilotActionDialog(
            title = stringResource(R.string.dialog_customterminal_title),
            onDismiss = {
                showAddDialog = false
                newTerminalType = ""
            },
            confirmLabel = stringResource(R.string.button_add),
            confirmEnabled = newTerminalType.isNotBlank(),
            onConfirm = {
                if (newTerminalType.isNotBlank()) {
                    onAddTerminalType(newTerminalType.trim())
                    showAddDialog = false
                    newTerminalType = ""
                }
            },
            dismissLabel = stringResource(R.string.delete_neg)
        ) {
            OutlinedTextField(
                value = newTerminalType,
                onValueChange = { newTerminalType = it },
                label = { Text(stringResource(R.string.dialog_customterminal_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomFontPreference(
    customFonts: List<String>,
    validationInProgress: Boolean,
    validationError: String?,
    onAddFont: (String) -> Unit,
    onRemoveFont: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newFontName by remember { mutableStateOf("") }

    // Show error snackbar if there's an error
    LaunchedEffect(validationError) {
        if (validationError != null) {
            // Error is shown in dialog, will be cleared when dialog closes
        }
    }

    Column(modifier = modifier) {
        CommandSurfaceCard(onClick = { showAddDialog = true }) {
            Text(stringResource(R.string.pref_customfont_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.pref_customfont_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Show existing custom fonts with remove option
        customFonts.forEach { fontName ->
            CommandSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(fontName, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveFont(fontName) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ShellPilotActionDialog(
            title = stringResource(R.string.dialog_customfont_title),
            onDismiss = {
                if (!validationInProgress) {
                    showAddDialog = false
                    newFontName = ""
                    onClearError()
                }
            },
            confirmLabel = stringResource(R.string.button_add),
            confirmEnabled = newFontName.isNotBlank() && !validationInProgress,
            onConfirm = {
                if (newFontName.isNotBlank()) {
                    onAddFont(newFontName.trim())
                }
            },
            dismissLabel = stringResource(R.string.button_cancel)
        ) {
            OutlinedTextField(
                value = newFontName,
                onValueChange = {
                    newFontName = it
                    onClearError()
                },
                label = { Text(stringResource(R.string.dialog_customfont_hint)) },
                singleLine = true,
                enabled = !validationInProgress,
                isError = validationError != null,
                supportingText = if (validationError != null) {
                    { Text(validationError, color = MaterialTheme.colorScheme.error) }
                } else if (validationInProgress) {
                    { Text(stringResource(R.string.font_validating)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Close dialog when font is successfully added
    LaunchedEffect(customFonts.size) {
        if (showAddDialog && !validationInProgress && validationError == null && newFontName.isNotBlank()) {
            // Font was added successfully
            showAddDialog = false
            newFontName = ""
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalFontPreference(
    localFonts: List<Pair<String, String>>,
    importInProgress: Boolean,
    importError: String?,
    onImportFont: (Uri, String) -> Unit,
    onDeleteFont: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var fontDisplayName by remember { mutableStateOf("") }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            fontDisplayName = ""
            showNameDialog = true
        }
    }

    Column(modifier = modifier) {
        CommandSurfaceCard(
            onClick = if (importInProgress) {
                null
            } else {
                {
                    fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf"))
                }
            }
        ) {
            Text(stringResource(R.string.pref_localfont_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (importInProgress) {
                    stringResource(R.string.font_importing)
                } else {
                    stringResource(R.string.pref_localfont_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Show existing local fonts with delete option
        localFonts.forEach { (displayName, fileName) ->
            CommandSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(displayName, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDeleteFont(fileName) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                }
            }
        }
    }

    // Dialog to get display name for imported font
    if (showNameDialog) {
        ShellPilotActionDialog(
            title = stringResource(R.string.dialog_localfont_title),
            onDismiss = {
                if (!importInProgress) {
                    showNameDialog = false
                    pendingUri = null
                    fontDisplayName = ""
                    onClearError()
                }
            },
            confirmLabel = stringResource(R.string.button_import),
            confirmEnabled = fontDisplayName.isNotBlank() && !importInProgress,
            onConfirm = {
                pendingUri?.let { uri ->
                    if (fontDisplayName.isNotBlank()) {
                        onImportFont(uri, fontDisplayName.trim())
                    }
                }
            },
            dismissLabel = stringResource(R.string.button_cancel)
        ) {
            OutlinedTextField(
                value = fontDisplayName,
                onValueChange = {
                    fontDisplayName = it
                    onClearError()
                },
                label = { Text(stringResource(R.string.dialog_localfont_hint)) },
                singleLine = true,
                enabled = !importInProgress,
                isError = importError != null,
                supportingText = if (importError != null) {
                    { Text(importError, color = MaterialTheme.colorScheme.error) }
                } else if (importInProgress) {
                    { Text(stringResource(R.string.font_importing)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Close dialog when font is successfully imported
    LaunchedEffect(localFonts.size) {
        if (showNameDialog && !importInProgress && importError == null && fontDisplayName.isNotBlank()) {
            showNameDialog = false
            pendingUri = null
            fontDisplayName = ""
        }
    }
}

@ScreenPreviews
@Composable
private fun SettingsScreenPreview() {
    ShellPilotTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                authOnLaunch = false,
                canAuthenticate = true,
                memkeys = true,
                connPersist = true,
                wifilock = false,
                backupkeys = true,
                scrollback = "500",
                rotation = "Default",
                titlebarhide = false,
                fullscreen = true,
                pgupdngesture = true,
                volumefont = true,
                keepalive = true,
                alwaysvisible = true,
                shiftfkeys = false,
                ctrlfkeys = false,
                stickymodifiers = "yes",
                keymode = "Use right-side keys",
                camera = "Ctrl+A then Space",
                bumpyarrows = true,
                bell = true,
                bellVolume = 0.75f,
                bellVibrate = true,
                bellNotification = false,
                fontFamily = "JETBRAINS_MONO",
                customFonts = listOf("Cascadia Code", "Hack"),
                customTerminalTypes = listOf("rxvt-unicode", "tmux-256color"),
                localFonts = listOf("My Custom Font" to "my_custom_font.ttf"),
                fontValidationInProgress = false,
                fontValidationError = null,
                fontImportInProgress = false,
                fontImportError = null
            ),
            onNavigateBack = {},
            onAuthOnLaunchChange = {},
            onMemkeysChange = {},
            onConnPersistChange = {},
            onWifilockChange = {},
            onBackupkeysChange = {},
            onScrollbackChange = {},
            onAddCustomTerminalType = {},
            onRemoveCustomTerminalType = {},
            onAddCustomFont = {},
            onRemoveCustomFont = {},
            onClearFontError = {},
            onImportLocalFont = { _, _ -> },
            onDeleteLocalFont = {},
            onClearImportError = {},
            onDefaultProfileChange = {},
            onLanguageChange = {},
            onFullscreenChange = {},
            onTitleBarHideChange = {},
            onVolumeFontChange = {},
            onAlwaysVisibleChange = {},
            onShiftFkeysChange = {},
            onCtrlFkeysChange = {},
            onStickyModifiersChange = {},
            onKeyModeChange = {},
            onCameraChange = {},
            onBumpyArrowsChange = {},
            onBellChange = {},
            onBellVolumeChange = {},
            onBellVibrateChange = {},
            onBellNotificationChange = {}
        )
    }
}

@Composable
private fun NotificationPermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.notification_permission_denied_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.open_settings),
        onConfirm = onOpenSettings,
        dismissLabel = stringResource(R.string.button_cancel)
    ) {
        Text(stringResource(R.string.notification_permission_denied_message))
    }
}
