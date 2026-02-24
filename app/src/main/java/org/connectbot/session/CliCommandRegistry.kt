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

package org.connectbot.session

import org.connectbot.data.entity.Shortcut

/**
 * CLI ツールのスラッシュコマンド / よく使うコマンドの静的レジストリ。
 *
 * 変更理由: Claude Code / Codex / 汎用コマンドの既知コマンドを
 * カテゴリ別に管理し、ShortcutListScreenからの一括インポートと
 * 動的検出後の自動追加に利用する。
 *
 * 各ツールのコマンド一覧は公式ドキュメントに基づく。
 * 末尾の \n は自動実行を意味する。
 */
object CliCommandRegistry {

    /**
     * ツールカテゴリ定義。
     * @param id 内部識別子
     * @param displayName UI表示名
     * @param probeBinary 検出用バイナリ名 (nullならプローブ不要)
     * @param commands このカテゴリに属するコマンド群
     */
    data class ToolCategory(
        val id: String,
        val displayName: String,
        val probeBinary: String?,
        val commands: List<Shortcut>
    )

    /** 全カテゴリ一覧 */
    val categories: List<ToolCategory> = listOf(
        generalCommands(),
        gitCommands(),
        dockerCommands(),
        claudeCodeCommands(),
        codexCommands()
    )

    /** カテゴリIDで検索 */
    fun findCategory(id: String): ToolCategory? = categories.find { it.id == id }

    /** プローブ対象のバイナリ名一覧 (検出対象ツール) */
    val probableBinaries: List<String>
        get() = categories.mapNotNull { it.probeBinary }

    // --- カテゴリ定義 ---

    private fun generalCommands() = ToolCategory(
        id = "general",
        displayName = "汎用コマンド",
        probeBinary = null,
        commands = listOf(
            Shortcut(label = "ls -la", command = "ls -la\n", order = 0),
            Shortcut(label = "pwd", command = "pwd\n", order = 1),
            Shortcut(label = "clear", command = "clear\n", order = 2),
            Shortcut(label = "top", command = "top\n", order = 3),
            Shortcut(label = "df -h", command = "df -h\n", order = 4),
            Shortcut(label = "free -h", command = "free -h\n", order = 5),
            Shortcut(label = "whoami", command = "whoami\n", order = 6),
            Shortcut(label = "uptime", command = "uptime\n", order = 7),
            Shortcut(label = "history", command = "history | tail -20\n", order = 8),
            Shortcut(label = "tail log", command = "tail -f /var/log/syslog\n", order = 9),
            Shortcut(label = "Ctrl+C", command = "\u0003", order = 10),
            Shortcut(label = "Ctrl+D", command = "\u0004", order = 11),
            Shortcut(label = "Ctrl+Z", command = "\u001A", order = 12)
        )
    )

    private fun gitCommands() = ToolCategory(
        id = "git",
        displayName = "Git",
        probeBinary = "git",
        commands = listOf(
            Shortcut(label = "git status", command = "git status\n", order = 0),
            Shortcut(label = "git diff", command = "git diff\n", order = 1),
            Shortcut(label = "git log", command = "git log --oneline -10\n", order = 2),
            Shortcut(label = "git pull", command = "git pull\n", order = 3),
            Shortcut(label = "git push", command = "git push\n", order = 4),
            Shortcut(label = "git branch", command = "git branch -a\n", order = 5),
            Shortcut(label = "git stash", command = "git stash\n", order = 6),
            Shortcut(label = "git stash pop", command = "git stash pop\n", order = 7),
            Shortcut(label = "git add .", command = "git add .\n", order = 8),
            Shortcut(label = "git commit", command = "git commit -m \"", order = 9)
        )
    )

    private fun dockerCommands() = ToolCategory(
        id = "docker",
        displayName = "Docker",
        probeBinary = "docker",
        commands = listOf(
            Shortcut(label = "docker ps", command = "docker ps\n", order = 0),
            Shortcut(label = "docker ps -a", command = "docker ps -a\n", order = 1),
            Shortcut(label = "docker images", command = "docker images\n", order = 2),
            Shortcut(label = "docker logs", command = "docker logs --tail 50 -f ", order = 3),
            Shortcut(label = "docker compose up", command = "docker compose up -d\n", order = 4),
            Shortcut(label = "docker compose down", command = "docker compose down\n", order = 5),
            Shortcut(label = "docker stats", command = "docker stats\n", order = 6)
        )
    )

    /**
     * Claude Code (claude) のスラッシュコマンド。
     * https://docs.anthropic.com/en/docs/claude-code
     * セッション内で `/command` 形式で入力して使用する。
     */
    private fun claudeCodeCommands() = ToolCategory(
        id = "claude_code",
        displayName = "Claude Code",
        probeBinary = "claude",
        commands = listOf(
            // 起動・セッション管理
            Shortcut(label = "claude", command = "claude\n", order = 0),
            Shortcut(label = "claude --resume", command = "claude --resume\n", order = 1),
            Shortcut(label = "claude --continue", command = "claude --continue\n", order = 2),
            // スラッシュコマンド (claude セッション内)
            Shortcut(label = "/help", command = "/help\n", order = 10),
            Shortcut(label = "/init", command = "/init\n", order = 11),
            Shortcut(label = "/compact", command = "/compact\n", order = 12),
            Shortcut(label = "/clear", command = "/clear\n", order = 13),
            Shortcut(label = "/cost", command = "/cost\n", order = 14),
            Shortcut(label = "/doctor", command = "/doctor\n", order = 15),
            Shortcut(label = "/config", command = "/config\n", order = 16),
            Shortcut(label = "/review", command = "/review\n", order = 17),
            Shortcut(label = "/bug", command = "/bug\n", order = 18),
            Shortcut(label = "/login", command = "/login\n", order = 19),
            Shortcut(label = "/logout", command = "/logout\n", order = 20),
            Shortcut(label = "/status", command = "/status\n", order = 21),
            Shortcut(label = "/permissions", command = "/permissions\n", order = 22),
            Shortcut(label = "/vim", command = "/vim\n", order = 23),
            // ワンライナー
            Shortcut(label = "claude -p", command = "claude -p \"", order = 30),
            Shortcut(label = "claude commit", command = "claude -p \"create a commit for the current changes with a descriptive message\"\n", order = 31)
        )
    )

    /**
     * OpenAI Codex CLI のコマンド。
     * セッション内で入力して使用する。
     */
    private fun codexCommands() = ToolCategory(
        id = "codex",
        displayName = "Codex (OpenAI)",
        probeBinary = "codex",
        commands = listOf(
            Shortcut(label = "codex", command = "codex\n", order = 0),
            Shortcut(label = "codex -q", command = "codex -q \"", order = 1),
            Shortcut(label = "codex --full-auto", command = "codex --approval-mode full-auto \"", order = 2),
            Shortcut(label = "/help", command = "/help\n", order = 10),
            Shortcut(label = "/diff", command = "/diff\n", order = 11),
            Shortcut(label = "/undo", command = "/undo\n", order = 12),
            Shortcut(label = "/clear", command = "/clear\n", order = 13)
        )
    )
}
