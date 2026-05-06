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

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.shellpilot.app.R
import io.shellpilot.app.ui.components.ShellPilotStatePanel
import io.shellpilot.app.ui.components.StatusChip
import timber.log.Timber

@Composable
fun AuthenticationScreen(
    onAuthenticationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRetryButton by remember { mutableStateOf(false) }

    val promptAuth = remember(context) {
        {
            val activity = context as? FragmentActivity
            if (activity != null) {
                showRetryButton = false
                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            Timber.d("Authentication succeeded")
                            onAuthenticationSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            Timber.d("Authentication error: $errorCode $errString")
                            showRetryButton = true
                        }

                        override fun onAuthenticationFailed() {
                            Timber.d("Authentication failed")
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.auth_prompt_title))
                    .setSubtitle(activity.getString(R.string.auth_prompt_subtitle))
                    .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                    .build()

                prompt.authenticate(promptInfo)
            } else {
                Timber.e("Context is not a FragmentActivity, cannot show BiometricPrompt")
                showRetryButton = true
            }
        }
    }

    LaunchedEffect(Unit) {
        promptAuth()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ShellPilotStatePanel(
            title = stringResource(R.string.auth_screen_title),
            body = "端末と鍵を保護しています。",
            icon = Icons.Default.Lock,
            chips = {
                StatusChip(label = "生体認証")
                StatusChip(label = "端末認証")
            },
            actions = {
                if (showRetryButton) {
                    Button(onClick = { promptAuth() }) {
                        Text(stringResource(R.string.auth_screen_unlock_button))
                    }
                }
            }
        )
    }
}
