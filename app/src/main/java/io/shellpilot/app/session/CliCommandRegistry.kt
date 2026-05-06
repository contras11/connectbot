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

import io.shellpilot.app.data.entity.Shortcut

/**
 * CLI ツールのスラッシュコマンド / よく使うコマンドの静的レジストリ。
 *
 * 変更理由: Claude Code / Codex / 汎用コマンドの既知コマンドを
 * カテゴリ別に管理し、ShortcutListScreenからの一括インポートと
 * 動的検出後の自動追加に利用する。
 *
 * 各ツールのコマンド一覧は公式ドキュメントに基づく。
 * 末尾の \n は自動実行を意味する。
 *
 * カテゴリ一覧: control / general / claude_code / codex / git / docker / cloudflare
 * 変更理由: Cloudflareカテゴリを追加 (wrangler + cloudflared コマンド群)
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
        controlCommands(),
        generalCommands(),
        claudeCodeCommands(),
        codexCommands(),
        gitCommands(),
        dockerCommands(),
        // 変更理由: Cloudflare Workers / Tunnelコマンドのカテゴリを追加
        cloudflareCommands()
    )

    /** カテゴリIDで検索 */
    fun findCategory(id: String): ToolCategory? = categories.find { it.id == id }

    /** プローブ対象のバイナリ名一覧 (検出対象ツール) */
    val probableBinaries: List<String>
        get() = categories.mapNotNull { it.probeBinary }

    // --- カテゴリ定義 ---

    private fun shortcut(
        category: String,
        key: String,
        label: String,
        command: String,
        order: Int
    ) = Shortcut(
        label = label,
        command = command,
        category = category,
        templateKey = "$category:$key",
        order = order
    )

    private fun controlCommands() = ToolCategory(
        id = "control",
        displayName = "制御キー",
        probeBinary = null,
        commands = listOf(
            shortcut("control", "ctrl-c", "Ctrl+C", "\u0003", 0),
            shortcut("control", "ctrl-d", "Ctrl+D", "\u0004", 1),
            shortcut("control", "ctrl-z", "Ctrl+Z", "\u001A", 2),
            shortcut("control", "esc", "Esc", "\u001B", 3),
            shortcut("control", "ctrl-l", "Ctrl+L", "\u000C", 4)
        )
    )

    private fun generalCommands() = ToolCategory(
        id = "general",
        displayName = "汎用コマンド",
        probeBinary = null,
        commands = listOf(
            shortcut("general", "ls-la", "ls -la", "ls -la\n", 0),
            shortcut("general", "pwd", "pwd", "pwd\n", 1),
            shortcut("general", "clear", "clear", "clear\n", 2),
            shortcut("general", "top", "top", "top\n", 3),
            shortcut("general", "df-h", "df -h", "df -h\n", 4),
            shortcut("general", "free-h", "free -h", "free -h\n", 5),
            shortcut("general", "whoami", "whoami", "whoami\n", 6),
            shortcut("general", "uptime", "uptime", "uptime\n", 7),
            shortcut("general", "history-tail", "history", "history | tail -20\n", 8),
            shortcut("general", "tail-syslog", "tail log", "tail -f /var/log/syslog\n", 9)
        )
    )

    private fun gitCommands() = ToolCategory(
        id = "git",
        displayName = "Git",
        probeBinary = "git",
        commands = listOf(
            shortcut("git", "status", "git status", "git status\n", 0),
            shortcut("git", "diff", "git diff", "git diff\n", 1),
            shortcut("git", "log", "git log", "git log --oneline -10\n", 2),
            shortcut("git", "pull", "git pull", "git pull\n", 3),
            shortcut("git", "push", "git push", "git push\n", 4),
            shortcut("git", "branch", "git branch", "git branch -a\n", 5),
            shortcut("git", "stash", "git stash", "git stash\n", 6),
            shortcut("git", "stash-pop", "git stash pop", "git stash pop\n", 7),
            shortcut("git", "add-all", "git add .", "git add .\n", 8),
            shortcut("git", "commit", "git commit", "git commit -m \"", 9)
        )
    )

    private fun dockerCommands() = ToolCategory(
        id = "docker",
        displayName = "Docker",
        probeBinary = "docker",
        commands = listOf(
            shortcut("docker", "ps", "docker ps", "docker ps\n", 0),
            shortcut("docker", "ps-all", "docker ps -a", "docker ps -a\n", 1),
            shortcut("docker", "images", "docker images", "docker images\n", 2),
            shortcut("docker", "logs", "docker logs", "docker logs --tail 50 -f ", 3),
            shortcut("docker", "compose-up", "docker compose up", "docker compose up -d\n", 4),
            shortcut("docker", "compose-down", "docker compose down", "docker compose down\n", 5),
            shortcut("docker", "stats", "docker stats", "docker stats\n", 6)
        )
    )

    /**
     * Cloudflare Workers (wrangler) および Cloudflare Tunnel (cloudflared) のコマンド。
     *
     * 変更理由: ユーザ要望によりCloudflareカテゴリを追加。
     * probeBinary は "wrangler" とし、Workers CLIの存在を検出対象とする。
     * cloudflared コマンドも同カテゴリに含め、トンネル操作を一元管理する。
     */
    private fun cloudflareCommands() = ToolCategory(
        id = "cloudflare",
        displayName = "Cloudflare",
        probeBinary = "wrangler",
        commands = listOf(
            // Cloudflare Workers (wrangler)
            shortcut("cloudflare", "wrangler-dev", "wrangler dev", "wrangler dev\n", 0),
            shortcut("cloudflare", "wrangler-deploy", "wrangler deploy", "wrangler deploy\n", 1),
            shortcut("cloudflare", "wrangler-tail", "wrangler tail", "wrangler tail\n", 2),
            shortcut("cloudflare", "wrangler-login", "wrangler login", "wrangler login\n", 3),
            shortcut("cloudflare", "wrangler-whoami", "wrangler whoami", "wrangler whoami\n", 4),
            shortcut("cloudflare", "wrangler-secret", "wrangler secret", "wrangler secret put ", 5),
            shortcut("cloudflare", "wrangler-kv-list", "wrangler kv list", "wrangler kv key list --binding=KV\n", 6),
            // Cloudflare Tunnel (cloudflared)
            shortcut("cloudflare", "tunnel-login", "cf tunnel login", "cloudflared tunnel login\n", 10),
            shortcut("cloudflare", "tunnel-create", "cf tunnel create", "cloudflared tunnel create ", 11),
            shortcut("cloudflare", "tunnel-list", "cf tunnel list", "cloudflared tunnel list\n", 12),
            shortcut("cloudflare", "tunnel-run", "cf tunnel run", "cloudflared tunnel run ", 13),
            shortcut("cloudflare", "tunnel-url", "cf tunnel --url", "cloudflared tunnel --url http://localhost:8080\n", 14),
            shortcut("cloudflare", "tunnel-delete", "cf tunnel delete", "cloudflared tunnel delete ", 15)
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
            shortcut("claude_code", "launch", "claude", "claude\n", 0),
            shortcut("claude_code", "continue", "claude -c", "claude -c\n", 1),
            shortcut("claude_code", "resume", "claude -r", "claude -r ", 2),
            shortcut("claude_code", "print", "claude -p", "claude -p \"", 3),
            shortcut("claude_code", "doctor-cli", "claude doctor", "claude doctor\n", 4),
            shortcut("claude_code", "update-cli", "claude update", "claude update\n", 5),
            shortcut("claude_code", "mcp-cli", "claude mcp", "claude mcp\n", 6),
            shortcut("claude_code", "agents-cli", "claude agents", "claude agents\n", 7),
            shortcut("claude_code", "plugin-cli", "claude plugin", "claude plugin\n", 8),
            shortcut("claude_code", "project-cli", "claude project", "claude project\n", 9),
            shortcut("claude_code", "ultrareview-cli", "claude ultrareview", "claude ultrareview\n", 10),
            // スラッシュコマンド (claude セッション内)
            shortcut("claude_code", "slash-help", "/help", "/help\n", 20),
            shortcut("claude_code", "slash-init", "/init", "/init\n", 21),
            shortcut("claude_code", "slash-compact", "/compact", "/compact\n", 22),
            shortcut("claude_code", "slash-clear", "/clear", "/clear\n", 23),
            shortcut("claude_code", "slash-status", "/status", "/status\n", 24),
            shortcut("claude_code", "slash-model", "/model", "/model\n", 25),
            shortcut("claude_code", "slash-permissions", "/permissions", "/permissions\n", 26),
            shortcut("claude_code", "slash-review", "/review", "/review\n", 27),
            shortcut("claude_code", "slash-security-review", "/security-review", "/security-review\n", 28),
            shortcut("claude_code", "slash-add-dir", "/add-dir", "/add-dir ", 29),
            shortcut("claude_code", "slash-agents", "/agents", "/agents\n", 30),
            shortcut("claude_code", "slash-context", "/context", "/context\n", 31),
            shortcut("claude_code", "slash-cost", "/cost", "/cost\n", 32),
            shortcut("claude_code", "slash-doctor", "/doctor", "/doctor\n", 33),
            shortcut("claude_code", "slash-exit", "/exit", "/exit\n", 34),
            shortcut("claude_code", "slash-export", "/export", "/export\n", 35),
            shortcut("claude_code", "slash-hooks", "/hooks", "/hooks\n", 36),
            shortcut("claude_code", "slash-ide", "/ide", "/ide\n", 37),
            shortcut("claude_code", "slash-mcp", "/mcp", "/mcp\n", 38),
            shortcut("claude_code", "slash-memory", "/memory", "/memory\n", 39),
            shortcut("claude_code", "slash-output-style", "/output-style", "/output-style\n", 40),
            shortcut("claude_code", "slash-pr-comments", "/pr_comments", "/pr_comments\n", 41),
            shortcut("claude_code", "slash-release-notes", "/release-notes", "/release-notes\n", 42),
            shortcut("claude_code", "slash-resume", "/resume", "/resume\n", 43),
            shortcut("claude_code", "slash-todos", "/todos", "/todos\n", 44),
            shortcut("claude_code", "slash-upgrade", "/upgrade", "/upgrade\n", 45),
            shortcut("claude_code", "slash-vim", "/vim", "/vim\n", 46),
            // ワンライナー
            shortcut("claude_code", "commit-one-liner", "claude commit", "claude -p \"create a commit for the current changes with a descriptive message\"\n", 60)
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
            shortcut("codex", "launch", "codex", "codex\n", 0),
            shortcut("codex", "exec", "codex exec", "codex exec \"", 1),
            shortcut("codex", "exec-resume-last", "codex exec resume --last", "codex exec resume --last\n", 2),
            shortcut("codex", "review", "codex review", "codex review\n", 3),
            shortcut("codex", "resume-last", "codex resume --last", "codex resume --last\n", 4),
            shortcut("codex", "fork", "codex fork", "codex fork\n", 5),
            shortcut("codex", "update", "codex update", "codex update\n", 6),
            shortcut("codex", "mcp", "codex mcp", "codex mcp\n", 7),
            shortcut("codex", "features", "codex features", "codex features\n", 8),
            Shortcut(label = "/help", command = "/help\n", category = "codex", templateKey = "codex:slash-help", order = 20),
            Shortcut(label = "/init", command = "/init\n", category = "codex", templateKey = "codex:slash-init", order = 21),
            Shortcut(label = "/status", command = "/status\n", category = "codex", templateKey = "codex:slash-status", order = 22),
            Shortcut(label = "/diff", command = "/diff\n", category = "codex", templateKey = "codex:slash-diff", order = 23),
            Shortcut(label = "/prompts", command = "/prompts\n", category = "codex", templateKey = "codex:slash-prompts", order = 24),
            Shortcut(label = "/model", command = "/model\n", category = "codex", templateKey = "codex:slash-model", order = 25),
            Shortcut(label = "/permissions", command = "/permissions\n", category = "codex", templateKey = "codex:slash-permissions", order = 26),
            Shortcut(label = "/review", command = "/review\n", category = "codex", templateKey = "codex:slash-review", order = 27),
            Shortcut(label = "/new", command = "/new\n", category = "codex", templateKey = "codex:slash-new", order = 28),
            Shortcut(label = "/compact", command = "/compact\n", category = "codex", templateKey = "codex:slash-compact", order = 29),
            Shortcut(label = "/undo", command = "/undo\n", category = "codex", templateKey = "codex:slash-undo", order = 30),
            Shortcut(label = "/mention", command = "/mention ", category = "codex", templateKey = "codex:slash-mention", order = 31),
            Shortcut(label = "/quit", command = "/quit\n", category = "codex", templateKey = "codex:slash-quit", order = 32)
        )
    )
}
