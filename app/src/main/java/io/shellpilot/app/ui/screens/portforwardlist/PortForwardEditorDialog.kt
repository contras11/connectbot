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

package io.shellpilot.app.ui.screens.portforwardlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.shellpilot.app.R
import io.shellpilot.app.ui.components.ShellPilotActionDialog
import io.shellpilot.app.util.HostConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardEditorDialog(
    onDismiss: () -> Unit,
    onSave: (nickname: String, type: String, sourcePort: String, destination: String) -> Unit,
    initialNickname: String = "",
    initialType: String = HostConstants.PORTFORWARD_LOCAL,
    initialSourcePort: String = "",
    initialDestination: String = "",
    isEditing: Boolean = false
) {
    var nickname by remember { mutableStateOf(initialNickname) }
    var sourcePort by remember { mutableStateOf(initialSourcePort) }
    var destination by remember { mutableStateOf(initialDestination) }

    // Map initial type string to index
    val initialTypeIndex = when (initialType) {
        HostConstants.PORTFORWARD_LOCAL -> 0
        HostConstants.PORTFORWARD_REMOTE -> 1
        HostConstants.PORTFORWARD_DYNAMIC5 -> 2
        else -> 0
    }
    var typeIndex by remember { mutableIntStateOf(initialTypeIndex) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val portForwardTypes = stringArrayResource(R.array.list_portforward_types)

    // Map type index to database type string
    val typeString = when (typeIndex) {
        0 -> HostConstants.PORTFORWARD_LOCAL
        1 -> HostConstants.PORTFORWARD_REMOTE
        2 -> HostConstants.PORTFORWARD_DYNAMIC5
        else -> HostConstants.PORTFORWARD_LOCAL
    }

    // Dynamic SOCKS proxy doesn't need destination
    val needsDestination = typeIndex != 2

    // 変更理由: 空入力を既定値で補完して保存せず、ユーザーが明示した値だけを保存する。
    val sourcePortValue = sourcePort.trim().toIntOrNull()
    val isSourcePortValid = sourcePortValue != null && sourcePortValue in 1..65535

    val isDestinationValid = if (needsDestination) {
        val dest = destination.trim()
        // Basic validation: should contain a colon and have non-empty parts
        val parts = dest.split(":")
        val destPort = parts.getOrNull(1)?.toIntOrNull()
        parts.size == 2 && parts[0].isNotBlank() && destPort != null && destPort in 1..65535
    } else {
        true // Destination not needed for dynamic SOCKS
    }

    val canSave = sourcePort.isNotBlank() && isSourcePortValid &&
        (!needsDestination || destination.isNotBlank() && isDestinationValid)

    ShellPilotActionDialog(
        modifier = Modifier.fillMaxWidth(0.96f),
        title = stringResource(R.string.portforward_edit),
        subtitle = portForwardTypes[typeIndex],
        onDismiss = onDismiss,
        confirmLabel = stringResource(if (isEditing) R.string.portforward_save else R.string.portforward_pos),
        confirmEnabled = canSave,
        onConfirm = {
            onSave(nickname.trim(), typeString, sourcePort.trim(), if (needsDestination) destination.trim() else "")
        },
        dismissLabel = stringResource(R.string.delete_neg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(R.string.prompt_nickname)) },
                placeholder = { Text(stringResource(R.string.portforward_nickname_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = portForwardTypes[typeIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.prompt_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        portForwardTypes.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    typeIndex = index
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = sourcePort,
                    onValueChange = { sourcePort = it },
                    label = { Text(stringResource(R.string.prompt_source_port)) },
                    placeholder = { Text(stringResource(R.string.portforward_source_port_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = sourcePort.isBlank() || !isSourcePortValid,
                    supportingText = {
                        Text(
                            if (sourcePort.isBlank()) {
                                "待受ポートを入力してください"
                            } else if (!isSourcePortValid) {
                                stringResource(R.string.portforward_port_range_error)
                            } else {
                                "1〜65535"
                            }
                        )
                    }
                )

            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text(stringResource(R.string.prompt_destination)) },
                placeholder = { Text(stringResource(R.string.portforward_destination_placeholder)) },
                enabled = needsDestination,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = needsDestination && (destination.isBlank() || !isDestinationValid),
                supportingText = if (needsDestination) {
                    {
                        Text(
                            if (destination.isBlank()) {
                                "転送先を host:port 形式で入力してください"
                            } else if (!isDestinationValid) {
                                stringResource(R.string.portforward_destination_format_error)
                            } else {
                                "例: localhost:80"
                            }
                        )
                    }
                } else {
                    { Text("Dynamic転送では転送先を使用しません") }
                }
            )
        }
    }
}
