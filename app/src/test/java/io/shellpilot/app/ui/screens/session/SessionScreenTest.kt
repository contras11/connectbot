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

package io.shellpilot.app.ui.screens.session

import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionScreenTest {

    @Test
    fun buildComposerTemplateCommands_UsesSavedShortcutsForSelectedCategory() {
        val host = Host(id = 10L, nickname = "prod", hostname = "prod.example.com", port = 22)
        val shortcuts = listOf(
            Shortcut(
                id = "deleted-registry-replacement-is-not-present",
                label = "custom status",
                command = "/status --verbose\n",
                category = "codex",
                order = 2
            ),
            Shortcut(
                id = "other-category",
                label = "git status",
                command = "git status -sb\n",
                category = "git",
                order = 1
            )
        )

        val commands = buildComposerTemplateCommands(shortcuts, "codex", host)

        assertEquals(listOf("/status --verbose"), commands)
    }

    @Test
    fun buildComposerTemplateCommands_ExpandsSavedHostPlaceholders() {
        val host = Host(id = 10L, nickname = "prod", hostname = "prod.example.com", port = 2222)
        val shortcuts = listOf(
            Shortcut(
                id = "custom-hostname",
                label = "echo host",
                command = "echo {{hostname}}:{{port}}\n",
                category = null,
                order = 0
            )
        )

        val commands = buildComposerTemplateCommands(shortcuts, null, host)

        assertEquals(listOf("echo prod.example.com:2222"), commands)
    }
}
