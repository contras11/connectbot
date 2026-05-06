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

package io.shellpilot.app.ui.screens.generatepubkey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.shellpilot.app.R
import io.shellpilot.app.ui.components.EntropyGatherer
import io.shellpilot.app.ui.components.ShellPilotActionDialog

@Composable
fun EntropyGatherDialog(
    onEntropyGathered: (ByteArray?) -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }

    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_gather_entropy),
        onDismiss = {
            onEntropyGathered(null)
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.pubkey_touch_hint))

            Text(
                text = stringResource(R.string.pubkey_touch_prompt, progress),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            EntropyGatherer(
                onEntropyGathered = { entropy ->
                    onEntropyGathered(entropy)
                },
                onProgressUpdated = { newProgress ->
                    progress = newProgress
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun GeneratingKeyDialog() {
    ShellPilotActionDialog(
        title = stringResource(R.string.pubkey_generating),
        onDismiss = { /* 生成中は閉じない */ }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
