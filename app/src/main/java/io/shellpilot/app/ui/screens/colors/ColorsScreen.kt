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

package io.shellpilot.app.ui.screens.colors

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import io.shellpilot.app.R
import io.shellpilot.app.data.entity.ColorScheme
import io.shellpilot.app.ui.ScreenPreviews
import io.shellpilot.app.ui.common.getLocalizedColorSchemeDescription
import io.shellpilot.app.ui.common.getLocalizedColorSchemeName
import io.shellpilot.app.ui.components.CommandSurfaceCard
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.ui.components.ShellPilotScaffold
import io.shellpilot.app.ui.components.StatusChip
import io.shellpilot.app.ui.theme.ShellPilotTheme

/**
 * Screen for managing color schemes (create, duplicate, delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPaletteEditor: (Long) -> Unit = {},
    viewModel: ColorSchemeManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()

    var exportingSchemeId by remember { mutableLongStateOf(-1L) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val schemeJson = repository.exportScheme(exportingSchemeId)
                    context.contentResolver.openOutputStream(fileUri)?.use { output ->
                        output.write(schemeJson.toJson().toByteArray())
                    }
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.message_export_success,
                            schemeJson.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.error_export_failed,
                            e.message
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val jsonString =
                        context.contentResolver.openInputStream(fileUri)?.use { input ->
                            input.bufferedReader().readText()
                        } ?: return@launch

                    val schemeId =
                        repository.importScheme(jsonString, allowOverwrite = false)
                    val schemes = repository.getAllSchemes()
                    val importedScheme = schemes.find { it.id == schemeId }

                    viewModel.refresh()

                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.message_import_success,
                            importedScheme?.name ?: "scheme"
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: org.json.JSONException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_invalid_json),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.error_import_failed,
                            e.message
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    ColorsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToPaletteEditor = onNavigateToPaletteEditor,
        onExportScheme = { schemeId ->
            exportingSchemeId = schemeId
            scope.launch {
                try {
                    val schemes = repository.getAllSchemes()
                    val scheme = schemes.find { it.id == schemeId }
                    val fileName = "${scheme?.name?.replace(" ", "_") ?: "scheme"}.json"
                    exportLauncher.launch(fileName)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.error_export_failed,
                            e.message
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        },
        onImportScheme = {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        },
        onShowNewSchemeDialog = viewModel::showNewSchemeDialog,
        onClearError = viewModel::clearError,
        onSelectScheme = viewModel::selectScheme,
        onShowDeleteDialog = viewModel::showDeleteDialog,
        onCreateNewScheme = viewModel::createNewScheme,
        onHideNewSchemeDialog = viewModel::hideNewSchemeDialog,
        onDeleteScheme = viewModel::deleteScheme,
        onHideDeleteDialog = viewModel::hideDeleteDialog
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreenContent(
    uiState: SchemeManagerUiState,
    onNavigateBack: () -> Unit,
    onNavigateToPaletteEditor: (Long) -> Unit,
    onExportScheme: (Long) -> Unit,
    onImportScheme: () -> Unit,
    onShowNewSchemeDialog: () -> Unit,
    onClearError: () -> Unit,
    onSelectScheme: (Long) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onCreateNewScheme: (String, String, Long) -> Unit,
    onHideNewSchemeDialog: () -> Unit,
    onDeleteScheme: (Long) -> Unit,
    onHideDeleteDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnClearError by rememberUpdatedState(onClearError)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
            currentOnClearError()
        }
    }

    ShellPilotScaffold(
        title = stringResource(R.string.title_scheme_manager),
        subtitle = "ターミナル配色とパレット",
        snackbarHost = { SnackbarHost(snackbarHostState) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_navigate_up)
                )
            }
        },
        actions = {
            IconButton(onClick = onShowNewSchemeDialog) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.button_new_scheme)
                )
            }
            IconButton(onClick = onImportScheme) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = stringResource(R.string.button_import_scheme)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.schemes.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_custom_schemes),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                    text = "配色を管理",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "ターミナルのプレビューとANSIパレットを確認しながら、作業環境に合う配色を選びます。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(label = "スキーム ${uiState.schemes.size}")
                                    StatusChip(label = "ANSI 16色")
                                }
                            }
                        }
                        items(uiState.schemes) { scheme ->
                            SchemeItem(
                                scheme = scheme,
                                palette = uiState.schemePalettes[scheme.id],
                                onClick = { onNavigateToPaletteEditor(scheme.id) },
                                onExport = { onExportScheme(scheme.id) },
                                onDelete = {
                                    if (!scheme.isBuiltIn) {
                                        onSelectScheme(scheme.id)
                                        onShowDeleteDialog()
                                    }
                                },
                                onDuplicate = {
                                    onSelectScheme(scheme.id)
                                    onShowNewSchemeDialog()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showNewSchemeDialog) {
        val selectedScheme = uiState.selectedSchemeId?.let { id ->
            uiState.schemes.find { it.id == id }
        }
        NewSchemeDialog(
            availableSchemes = uiState.schemes,
            preselectedSchemeId = uiState.selectedSchemeId,
            suggestedName = selectedScheme?.let {
                stringResource(R.string.scheme_copy_name, getLocalizedColorSchemeName(it))
            },
            error = uiState.dialogError,
            onConfirm = { name, description, baseSchemeId ->
                onCreateNewScheme(name, description, baseSchemeId)
            },
            onDismiss = onHideNewSchemeDialog
        )
    }

    if (uiState.showDeleteDialog && uiState.selectedSchemeId != null) {
        val scheme = uiState.schemes.find { it.id == uiState.selectedSchemeId }
        if (scheme != null) {
            DeleteSchemeDialog(
                schemeName = scheme.name,
                error = uiState.dialogError,
                onConfirm = { onDeleteScheme(scheme.id) },
                onDismiss = onHideDeleteDialog
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun ColorsScreenEmptyPreview() {
    ShellPilotTheme {
        ColorsScreenContent(
            uiState = SchemeManagerUiState(
                schemes = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onNavigateToPaletteEditor = {},
            onExportScheme = {},
            onImportScheme = {},
            onShowNewSchemeDialog = {},
            onClearError = {},
            onSelectScheme = {},
            onShowDeleteDialog = {},
            onCreateNewScheme = { _, _, _ -> },
            onHideNewSchemeDialog = {},
            onDeleteScheme = {},
            onHideDeleteDialog = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun ColorsScreenLoadingPreview() {
    ShellPilotTheme {
        ColorsScreenContent(
            uiState = SchemeManagerUiState(
                schemes = emptyList(),
                isLoading = true
            ),
            onNavigateBack = {},
            onNavigateToPaletteEditor = {},
            onExportScheme = {},
            onImportScheme = {},
            onShowNewSchemeDialog = {},
            onClearError = {},
            onSelectScheme = {},
            onShowDeleteDialog = {},
            onCreateNewScheme = { _, _, _ -> },
            onHideNewSchemeDialog = {},
            onDeleteScheme = {},
            onHideDeleteDialog = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun ColorsScreenPopulatedPreview() {
    ShellPilotTheme {
        ColorsScreenContent(
            uiState = SchemeManagerUiState(
                schemes = listOf(
                    ColorScheme(
                        id = 1,
                        name = "Solarized Dark",
                        description = "暗い背景向けの定番配色",
                        isBuiltIn = true
                    ),
                    ColorScheme(
                        id = 2,
                        name = "Monokai",
                        description = "鮮やかなターミナル配色",
                        isBuiltIn = true
                    ),
                    ColorScheme(
                        id = 3,
                        name = "自分用テーマ",
                        description = "作業環境向けの調整",
                        isBuiltIn = false
                    )
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onNavigateToPaletteEditor = {},
            onExportScheme = {},
            onImportScheme = {},
            onShowNewSchemeDialog = {},
            onClearError = {},
            onSelectScheme = {},
            onShowDeleteDialog = {},
            onCreateNewScheme = { _, _, _ -> },
            onHideNewSchemeDialog = {},
            onDeleteScheme = {},
            onHideDeleteDialog = {}
        )
    }
}

/**
 * Individual scheme item in the list.
 */
@Composable
private fun SchemeItem(
    scheme: ColorScheme,
    palette: IntArray?,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val schemeName = getLocalizedColorSchemeName(scheme)
    CommandSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accent = if (scheme.isBuiltIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = schemeName,
                    style = MaterialTheme.typography.titleMedium
                )
                val localizedDescription = getLocalizedColorSchemeDescription(scheme)
                if (localizedDescription.isNotEmpty()) {
                    Text(
                        text = localizedDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                palette?.let {
                    AnsiSwatchRow(
                        palette = it,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                StatusChip(
                    label = if (scheme.isBuiltIn) {
                        stringResource(R.string.label_built_in_scheme)
                    } else {
                        stringResource(R.string.label_custom_scheme)
                    },
                    accent = if (scheme.isBuiltIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            Box {
                var showMenu by remember { mutableStateOf(false) }

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "「$schemeName」のその他の操作"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.button_export_scheme)) },
                        onClick = {
                            showMenu = false
                            onExport()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.button_duplicate_scheme)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null
                            )
                        }
                    )

                    if (!scheme.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.button_delete_scheme)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnsiSwatchRow(
    palette: IntArray,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        palette.take(16).forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(Color(color), RoundedCornerShape(2.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Dialog for creating a new color scheme.
 */
@Composable
private fun NewSchemeDialog(
    availableSchemes: List<ColorScheme>,
    error: String?,
    onConfirm: (name: String, description: String, baseSchemeId: Long) -> Unit,
    onDismiss: () -> Unit,
    preselectedSchemeId: Long? = null,
    suggestedName: String? = null
) {
    var name by remember { mutableStateOf(suggestedName ?: "") }
    var description by remember { mutableStateOf("") }
    var selectedBaseSchemeId by remember {
        mutableLongStateOf(preselectedSchemeId ?: -1L)
    }

    ShellPilotActionDialog(
        title = stringResource(R.string.dialog_title_new_scheme),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_confirm),
        onConfirm = { onConfirm(name, description, selectedBaseSchemeId) },
        dismissLabel = stringResource(R.string.button_cancel)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_scheme_name)) },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.label_scheme_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.label_base_scheme),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Column {
                availableSchemes.take(5).forEach { scheme ->
                    CommandSurfaceCard(
                        onClick = { selectedBaseSchemeId = scheme.id }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedBaseSchemeId == scheme.id,
                                onClick = { selectedBaseSchemeId = scheme.id }
                            )
                            Text(
                                text = getLocalizedColorSchemeName(scheme),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Dialog for confirming scheme deletion.
 */
@Composable
private fun DeleteSchemeDialog(
    schemeName: String,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ShellPilotActionDialog(
        title = stringResource(R.string.dialog_title_delete_scheme),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.button_delete_scheme),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.button_cancel),
        destructiveConfirm = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dialog_message_delete_scheme, schemeName))
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
