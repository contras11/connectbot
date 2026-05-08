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

    /** 公式ドキュメントを確認した日付。設定画面の同期状態表示に使う。 */
    const val OFFICIAL_COMMANDS_REVIEWED_DATE = "2026-05-07"

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
            shortcut("control", "tab", "Tab", "\t", 4),
            shortcut("control", "ctrl-l", "Ctrl+L", "\u000C", 5),
            shortcut("control", "ctrl-a", "Ctrl+A", "\u0001", 6),
            shortcut("control", "ctrl-e", "Ctrl+E", "\u0005", 7),
            shortcut("control", "ctrl-u", "Ctrl+U", "\u0015", 8),
            shortcut("control", "ctrl-k", "Ctrl+K", "\u000B", 9),
            shortcut("control", "ctrl-w", "Ctrl+W", "\u0017", 10),
            shortcut("control", "ctrl-r", "Ctrl+R", "\u0012", 11)
        )
    )

    private fun generalCommands() = ToolCategory(
        id = "general",
        displayName = "汎用コマンド",
        probeBinary = null,
        commands = listOf(
            shortcut("general", "ls-la", "ls -la", "ls -la\n", 0),
            shortcut("general", "pwd", "pwd", "pwd\n", 1),
            shortcut("general", "date", "date", "date\n", 2),
            shortcut("general", "whoami", "whoami", "whoami\n", 3),
            shortcut("general", "hostname", "hostname", "hostname\n", 4),
            shortcut("general", "uname", "uname -a", "uname -a\n", 5),
            shortcut("general", "uptime", "uptime", "uptime\n", 6),
            shortcut("general", "df-h", "df -h", "df -h\n", 7),
            shortcut("general", "du-current", "du -sh .", "du -sh .\n", 8),
            shortcut("general", "ps-head", "ps head", "ps aux | head -20\n", 9),
            shortcut("general", "env-shell", "shell info", "printf 'shell=%s\\nuser=%s\\nhost=%s\\n' \"${'$'}SHELL\" \"${'$'}USER\" \"${'$'}(hostname)\"\n", 10),
            shortcut("general", "clear", "clear", "clear\n", 11),
            shortcut("general", "history-tail", "history", "history | tail -20\n", 12),
            shortcut("general", "tail-file", "tail file", "tail -n 100 ", 13),
            shortcut("general", "grep", "grep", "grep -R \"\" .\n", 14),
            shortcut("general", "systemctl-status", "systemctl status", "systemctl status ", 15)
        )
    )

    private fun gitCommands() = ToolCategory(
        id = "git",
        displayName = "Git",
        probeBinary = "git",
        commands = listOf(
            shortcut("git", "status", "git status", "git status -sb\n", 0),
            shortcut("git", "diff-stat", "git diff --stat", "git diff --stat\n", 1),
            shortcut("git", "diff", "git diff", "git diff\n", 2),
            shortcut("git", "diff-cached", "git diff --cached", "git diff --cached\n", 3),
            shortcut("git", "log", "git log", "git log --oneline --graph --decorate -20\n", 4),
            shortcut("git", "branch-current", "git branch current", "git branch --show-current\n", 5),
            shortcut("git", "branch-vv", "git branch -vv", "git branch -vv\n", 6),
            shortcut("git", "remote", "git remote -v", "git remote -v\n", 7),
            shortcut("git", "stash-list", "git stash list", "git stash list\n", 8),
            shortcut("git", "stash-show-stat", "git stash show", "git stash show --stat\n", 9),
            shortcut("git", "switch-prev", "git switch -", "git switch -\n", 10),
            shortcut("git", "switch-new", "git switch -c", "git switch -c ", 11),
            shortcut("git", "restore-staged", "git restore --staged", "git restore --staged ", 12),
            shortcut("git", "fetch-prune", "git fetch --prune", "git fetch --prune\n", 13),
            shortcut("git", "pull-ff-only", "git pull --ff-only", "git pull --ff-only ", 14),
            shortcut("git", "stash-push", "git stash push", "git stash push -m \"", 15),
            shortcut("git", "worktree-list", "git worktree list", "git worktree list\n", 16),
            shortcut("git", "worktree-add", "git worktree add", "git worktree add ", 17),
            shortcut("git", "add-patch", "git add -p", "git add -p\n", 18),
            shortcut("git", "add-path", "git add path", "git add ", 19),
            shortcut("git", "commit", "git commit", "git commit -m \"", 20),
            shortcut("git", "push-current", "git push current", "git push -u origin HEAD ", 21)
        )
    )

    private fun dockerCommands() = ToolCategory(
        id = "docker",
        displayName = "Docker",
        probeBinary = "docker",
        commands = listOf(
            shortcut("docker", "version", "docker version", "docker version\n", 0),
            shortcut("docker", "info", "docker info", "docker info\n", 1),
            shortcut("docker", "ps", "docker ps", "docker ps\n", 2),
            shortcut("docker", "ps-all", "docker ps -a", "docker ps -a\n", 3),
            shortcut("docker", "images", "docker image ls", "docker image ls\n", 4),
            shortcut("docker", "volume-list", "docker volume ls", "docker volume ls\n", 5),
            shortcut("docker", "network-list", "docker network ls", "docker network ls\n", 6),
            shortcut("docker", "stats", "docker stats", "docker stats --no-stream\n", 7),
            shortcut("docker", "logs", "docker logs", "docker logs --tail 100 ", 8),
            shortcut("docker", "exec-shell", "docker exec shell", "docker exec -it  sh", 9),
            shortcut("docker", "compose-ls", "compose ls", "docker compose ls\n", 20),
            shortcut("docker", "compose-ps", "compose ps", "docker compose ps\n", 21),
            shortcut("docker", "compose-config", "compose config", "docker compose config\n", 22),
            shortcut("docker", "compose-logs", "compose logs", "docker compose logs --tail=100\n", 23),
            shortcut("docker", "compose-stats", "compose stats", "docker compose stats --no-stream\n", 24),
            shortcut("docker", "compose-up", "compose up", "docker compose up -d ", 25),
            shortcut("docker", "compose-pull", "compose pull", "docker compose pull ", 26),
            shortcut("docker", "compose-build", "compose build", "docker compose build ", 27),
            shortcut("docker", "compose-restart", "compose restart", "docker compose restart ", 28)
        )
    )

    /**
     * Cloudflare Workers (wrangler) および Cloudflare Tunnel (cloudflared) のコマンド。
     *
     * 変更理由: ユーザ要望によりCloudflareカテゴリを追加。
     * probeBinary は "npx" とし、Cloudflare推奨のプロジェクトローカルWrangler実行を優先する。
     * cloudflared コマンドも同カテゴリに含め、トンネル操作を一元管理する。
     */
    private fun cloudflareCommands() = ToolCategory(
        id = "cloudflare",
        displayName = "Cloudflare",
        probeBinary = "npx",
        commands = listOf(
            // Cloudflare Workers (wrangler)
            shortcut("cloudflare", "wrangler-version", "wrangler --version", "npx wrangler --version\n", 0),
            shortcut("cloudflare", "wrangler-whoami", "wrangler whoami", "npx wrangler whoami\n", 1),
            shortcut("cloudflare", "wrangler-dev", "wrangler dev", "npx wrangler dev\n", 2),
            shortcut("cloudflare", "wrangler-tail", "wrangler tail", "npx wrangler tail\n", 3),
            shortcut("cloudflare", "wrangler-types", "wrangler types", "npx wrangler types\n", 4),
            shortcut("cloudflare", "wrangler-deploy", "wrangler deploy", "npx wrangler deploy ", 5),
            shortcut("cloudflare", "wrangler-deployments", "wrangler deployments", "npx wrangler deployments list\n", 6),
            shortcut("cloudflare", "wrangler-versions", "wrangler versions", "npx wrangler versions list\n", 7),
            shortcut("cloudflare", "wrangler-versions-upload", "wrangler versions upload", "npx wrangler versions upload ", 8),
            shortcut("cloudflare", "wrangler-secret-list", "wrangler secret list", "npx wrangler secret list\n", 9),
            shortcut("cloudflare", "wrangler-secret", "wrangler secret put", "npx wrangler secret put ", 10),
            shortcut("cloudflare", "wrangler-kv-namespace-list", "wrangler kv namespace", "npx wrangler kv namespace list\n", 11),
            shortcut("cloudflare", "wrangler-kv-key-list", "wrangler kv key list", "npx wrangler kv key list --binding=KV\n", 12),
            shortcut("cloudflare", "wrangler-d1-list", "wrangler d1 list", "npx wrangler d1 list\n", 13),
            shortcut("cloudflare", "wrangler-d1-migrations", "wrangler d1 migrations", "npx wrangler d1 migrations list ", 14),
            shortcut("cloudflare", "wrangler-r2-bucket-list", "wrangler r2 buckets", "npx wrangler r2 bucket list\n", 15),
            // Cloudflare Tunnel (cloudflared)
            shortcut("cloudflare", "cloudflared-version", "cloudflared --version", "cloudflared --version\n", 30),
            shortcut("cloudflare", "cloudflared-update", "cloudflared update", "cloudflared update\n", 31),
            shortcut("cloudflare", "tunnel-login", "cf tunnel login", "cloudflared tunnel login\n", 32),
            shortcut("cloudflare", "tunnel-create", "cf tunnel create", "cloudflared tunnel create ", 33),
            shortcut("cloudflare", "tunnel-list", "cf tunnel list", "cloudflared tunnel list\n", 34),
            shortcut("cloudflare", "tunnel-info", "cf tunnel info", "cloudflared tunnel info ", 35),
            shortcut("cloudflare", "tunnel-run", "cf tunnel run", "cloudflared tunnel run ", 36),
            shortcut("cloudflare", "tunnel-route-dns", "cf tunnel route dns", "cloudflared tunnel route dns ", 37),
            shortcut("cloudflare", "tunnel-url", "cf tunnel --url", "cloudflared tunnel --url http://localhost:8080\n", 38)
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
            shortcut("claude_code", "install-stable-cli", "claude install stable", "claude install stable\n", 6),
            shortcut("claude_code", "auth-login-cli", "claude auth login", "claude auth login\n", 7),
            shortcut("claude_code", "auth-status-cli", "claude auth status", "claude auth status --text\n", 8),
            shortcut("claude_code", "auth-logout-cli", "claude auth logout", "claude auth logout\n", 9),
            shortcut("claude_code", "agents-cli", "claude agents", "claude agents\n", 10),
            shortcut("claude_code", "mcp-cli", "claude mcp", "claude mcp\n", 11),
            shortcut("claude_code", "plugin-cli", "claude plugin", "claude plugin\n", 12),
            shortcut("claude_code", "project-purge-cli", "claude project purge", "claude project purge ", 13),
            shortcut("claude_code", "remote-control-cli", "claude remote-control", "claude remote-control --name \"", 14),
            shortcut("claude_code", "setup-token-cli", "claude setup-token", "claude setup-token\n", 15),
            shortcut("claude_code", "ultrareview-cli", "claude ultrareview", "claude ultrareview ", 16),
            shortcut("claude_code", "auto-mode-defaults-cli", "claude auto-mode defaults", "claude auto-mode defaults\n", 17),
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
            shortcut("claude_code", "slash-copy", "/copy", "/copy\n", 32),
            shortcut("claude_code", "slash-cost", "/cost", "/cost\n", 33),
            shortcut("claude_code", "slash-config", "/config", "/config\n", 34),
            shortcut("claude_code", "slash-debug", "/debug", "/debug ", 35),
            shortcut("claude_code", "slash-desktop", "/desktop", "/desktop\n", 36),
            shortcut("claude_code", "slash-diff", "/diff", "/diff\n", 37),
            shortcut("claude_code", "slash-effort", "/effort", "/effort\n", 38),
            shortcut("claude_code", "slash-doctor", "/doctor", "/doctor\n", 39),
            shortcut("claude_code", "slash-exit", "/exit", "/exit\n", 40),
            shortcut("claude_code", "slash-export", "/export", "/export\n", 41),
            shortcut("claude_code", "slash-fast", "/fast", "/fast\n", 42),
            shortcut("claude_code", "slash-feedback", "/feedback", "/feedback\n", 43),
            shortcut("claude_code", "slash-hooks", "/hooks", "/hooks\n", 44),
            shortcut("claude_code", "slash-ide", "/ide", "/ide\n", 45),
            shortcut("claude_code", "slash-keybindings", "/keybindings", "/keybindings\n", 46),
            shortcut("claude_code", "slash-login", "/login", "/login\n", 47),
            shortcut("claude_code", "slash-logout", "/logout", "/logout\n", 48),
            shortcut("claude_code", "slash-mcp", "/mcp", "/mcp\n", 49),
            shortcut("claude_code", "slash-memory", "/memory", "/memory\n", 50),
            shortcut("claude_code", "slash-output-style", "/output-style", "/output-style\n", 51),
            shortcut("claude_code", "slash-plan", "/plan", "/plan ", 52),
            shortcut("claude_code", "slash-plugin", "/plugin", "/plugin\n", 53),
            shortcut("claude_code", "slash-release-notes", "/release-notes", "/release-notes\n", 54),
            shortcut("claude_code", "slash-resume", "/resume", "/resume\n", 55),
            shortcut("claude_code", "slash-rewind", "/rewind", "/rewind\n", 56),
            shortcut("claude_code", "slash-sandbox", "/sandbox", "/sandbox\n", 57),
            shortcut("claude_code", "slash-schedule", "/schedule", "/schedule ", 58),
            shortcut("claude_code", "slash-skills", "/skills", "/skills\n", 59),
            shortcut("claude_code", "slash-statusline", "/statusline", "/statusline\n", 60),
            shortcut("claude_code", "slash-tasks", "/tasks", "/tasks\n", 61),
            shortcut("claude_code", "slash-theme", "/theme", "/theme\n", 62),
            shortcut("claude_code", "slash-terminal-setup", "/terminal-setup", "/terminal-setup\n", 63),
            shortcut("claude_code", "slash-ultrareview", "/ultrareview", "/ultrareview ", 64),
            shortcut("claude_code", "slash-upgrade", "/upgrade", "/upgrade\n", 65),
            shortcut("claude_code", "slash-usage", "/usage", "/usage\n", 66),
            // ワンライナー
            shortcut("claude_code", "commit-one-liner", "claude commit", "claude -p \"create a commit for the current changes with a descriptive message\"\n", 80)
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
            shortcut("codex", "app", "codex app", "codex app\n", 9),
            shortcut("codex", "apply", "codex apply", "codex apply\n", 10),
            shortcut("codex", "cloud", "codex cloud", "codex cloud\n", 11),
            shortcut("codex", "login", "codex login", "codex login\n", 12),
            shortcut("codex", "logout", "codex logout", "codex logout\n", 13),
            shortcut("codex", "completion", "codex completion", "codex completion zsh\n", 14),
            shortcut("codex", "docs", "codex docs", "codex docs\n", 15),
            shortcut("codex", "flags", "codex flags", "codex flags\n", 16),
            shortcut("codex", "slash-permissions", "/permissions", "/permissions\n", 30),
            shortcut("codex", "slash-agent", "/agent", "/agent\n", 31),
            shortcut("codex", "slash-apps", "/apps", "/apps\n", 32),
            shortcut("codex", "slash-plugins", "/plugins", "/plugins\n", 33),
            shortcut("codex", "slash-clear", "/clear", "/clear\n", 34),
            shortcut("codex", "slash-compact", "/compact", "/compact\n", 35),
            shortcut("codex", "slash-copy", "/copy", "/copy\n", 36),
            shortcut("codex", "slash-diff", "/diff", "/diff\n", 37),
            shortcut("codex", "slash-exit", "/exit", "/exit\n", 38),
            shortcut("codex", "slash-experimental", "/experimental", "/experimental\n", 39),
            shortcut("codex", "slash-feedback", "/feedback", "/feedback\n", 40),
            shortcut("codex", "slash-init", "/init", "/init\n", 41),
            shortcut("codex", "slash-logout", "/logout", "/logout\n", 42),
            shortcut("codex", "slash-mcp", "/mcp", "/mcp\n", 43),
            shortcut("codex", "slash-mention", "/mention", "/mention ", 44),
            shortcut("codex", "slash-model", "/model", "/model\n", 45),
            shortcut("codex", "slash-fast", "/fast", "/fast\n", 46),
            shortcut("codex", "slash-plan", "/plan", "/plan ", 47),
            shortcut("codex", "slash-personality", "/personality", "/personality\n", 48),
            shortcut("codex", "slash-ps", "/ps", "/ps\n", 49),
            shortcut("codex", "slash-stop", "/stop", "/stop\n", 50),
            shortcut("codex", "slash-fork", "/fork", "/fork\n", 51),
            shortcut("codex", "slash-side", "/side", "/side ", 52),
            shortcut("codex", "slash-resume", "/resume", "/resume\n", 53),
            shortcut("codex", "slash-new", "/new", "/new\n", 54),
            shortcut("codex", "slash-quit", "/quit", "/quit\n", 55),
            shortcut("codex", "slash-review", "/review", "/review\n", 56),
            shortcut("codex", "slash-status", "/status", "/status\n", 57),
            shortcut("codex", "slash-debug-config", "/debug-config", "/debug-config\n", 58),
            shortcut("codex", "slash-statusline", "/statusline", "/statusline\n", 59),
            shortcut("codex", "slash-title", "/title", "/title\n", 60),
            shortcut("codex", "slash-keymap", "/keymap", "/keymap\n", 61),
            shortcut("codex", "slash-approvals-alias", "/approvals", "/approvals\n", 62)
        )
    )
}
