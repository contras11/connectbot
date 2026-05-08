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

package io.shellpilot.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.shellpilot.app.R
import io.shellpilot.app.ui.components.ShellPilotStatePanel
import io.shellpilot.app.ui.components.StatusChip

/**
 * Loading screen shown during app initialization while migration check
 * and service binding are in progress.
 */
@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ShellPilotStatePanel(
            title = "起動準備中",
            body = stringResource(R.string.loading_message),
            icon = Icons.Default.Download,
            chips = {
                StatusChip(label = "コアサービス")
                StatusChip(label = "鍵ストア")
            },
            actions = {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
