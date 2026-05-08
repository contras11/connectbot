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

package io.shellpilot.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude Code / Codex の公式テンプレート定義を検証する。
 *
 * 変更理由: 外部CLIの更新で古いスラッシュコマンドが残ると、
 * セッション中のワンタップ操作が実用に耐えなくなるため。
 */
class CliCommandRegistryTest {

    @Test
    fun officialTemplateKeys_areUnique() {
        val keys = CliCommandRegistry.categories
            .flatMap { it.commands }
            .mapNotNull { it.templateKey }

        assertEquals("templateKey should be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun claudeCodeCommands_includeCurrentCliAndSlashCommands() {
        val commands = categoryCommands("claude_code")

        assertTrue(commands.any { it.command == "claude auth status --text\n" })
        assertTrue(commands.any { it.command == "claude install stable\n" })
        assertTrue(commands.any { it.command == "/config\n" })
        assertTrue(commands.any { it.command == "/statusline\n" })
        assertTrue(commands.any { it.command == "/plan " })
        assertTrue(commands.any { it.command == "/tasks\n" })
        assertTrue(commands.any { it.command == "/ultrareview " })
        assertFalse(commands.any { it.command == "/pr_comments\n" })
        assertFalse(commands.any { it.command == "/todos\n" })
        assertFalse(commands.any { it.command == "/vim\n" })
    }

    @Test
    fun codexCommands_includeCurrentCliAndSlashCommands() {
        val commands = categoryCommands("codex")

        assertTrue(commands.any { it.command == "codex app\n" })
        assertTrue(commands.any { it.command == "codex apply\n" })
        assertTrue(commands.any { it.command == "codex cloud\n" })
        assertTrue(commands.any { it.command == "/agent\n" })
        assertTrue(commands.any { it.command == "/apps\n" })
        assertTrue(commands.any { it.command == "/plugins\n" })
        assertTrue(commands.any { it.command == "/plan " })
        assertTrue(commands.any { it.command == "/statusline\n" })
        assertTrue(commands.any { it.command == "/keymap\n" })
        assertFalse(commands.any { it.command == "codex -q \"" })
    }

    @Test
    fun gitTemplates_prioritizeReviewAndAvoidDestructiveDefaults() {
        val commands = categoryCommands("git").map { it.command }

        assertTrue(commands.contains("git status -sb\n"))
        assertTrue(commands.contains("git diff --stat\n"))
        assertTrue(commands.contains("git fetch --prune\n"))
        assertTrue(commands.contains("git add -p\n"))
        assertFalse(commands.contains("git add .\n"))
        assertFalse(commands.contains("git pull\n"))
        assertFalse(commands.contains("git push --force\n"))
        assertFalse(commands.contains("git reset --hard\n"))
        assertFalse(commands.contains("git clean -fdx\n"))
    }

    @Test
    fun dockerTemplates_useSafeInspectionByDefault() {
        val commands = categoryCommands("docker").map { it.command }

        assertTrue(commands.contains("docker image ls\n"))
        assertTrue(commands.contains("docker stats --no-stream\n"))
        assertTrue(commands.contains("docker compose config\n"))
        assertTrue(commands.contains("docker compose stats --no-stream\n"))
        assertFalse(commands.contains("docker system prune -a --volumes\n"))
        assertFalse(commands.contains("docker compose down -v\n"))
        assertFalse(commands.any { it == "docker compose down\n" })
        assertFalse(categoryCommands("docker").any { it.templateKey == "docker:compose-down" })
    }

    @Test
    fun cloudflareTemplates_useProjectLocalWranglerAndAvoidDeletes() {
        val commands = categoryCommands("cloudflare").map { it.command }

        assertTrue(commands.contains("npx wrangler whoami\n"))
        assertTrue(commands.contains("npx wrangler versions list\n"))
        assertTrue(commands.contains("npx wrangler kv namespace list\n"))
        assertTrue(commands.contains("cloudflared tunnel list\n"))
        assertFalse(commands.any { it == "wrangler deploy\n" })
        assertFalse(commands.any { it.contains(" tunnel delete") })
        assertFalse(commands.any { it.contains(" key delete") })
    }

    private fun categoryCommands(id: String) =
        CliCommandRegistry.findCategory(id)?.commands.orEmpty()
}
