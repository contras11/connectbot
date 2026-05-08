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

package io.shellpilot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.shellpilot.app.ui.MainActivity
import io.shellpilot.app.util.TestUriBuilder
import io.shellpilot.app.util.waitForBridgeByNickname
import io.shellpilot.app.util.waitUntilServiceBound
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
    }

    @Test
    fun mainActivity_handlesDisconnectAction() {
        val disconnectIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.DISCONNECT_ACTION
        }

        ActivityScenario.launch<MainActivity>(disconnectIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                assertNotNull("TerminalManager should be bound before disconnect action is handled", state.terminalManager)
            }
        }
    }

    @Test
    fun shortcut_localUri_launchesCorrectly() {
        val uri = TestUriBuilder.local("LocalShortcut")
        val shortcutIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        ActivityScenario.launch<MainActivity>(shortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager
                val bridge = runBlocking {
                    manager.waitForBridgeByNickname("LocalShortcut")
                }

                assertNotNull("Shortcut should create connection", bridge)
                assertTrue("Shortcut host should be temporary", bridge.host.id < 0)
            }
        }
    }

    @Test
    fun createShortcut_usesModernShortcutInfoCompat() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        val createShortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(createShortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    activity.waitUntilServiceBound()
                }
                assertTrue("ACTION_CREATE_SHORTCUT should enter shortcut creation mode", activity.makingShortcut)
            }
        }
    }
}
