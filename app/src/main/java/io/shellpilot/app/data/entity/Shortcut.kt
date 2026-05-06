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

package io.shellpilot.app.data.entity

import org.json.JSONObject
import java.util.UUID

/**
 * ターミナルショートカットコマンドのモデル。
 *
 * 変更理由: Claude Code / Codex用にワンタップでコマンド送信できる
 * ショートカット機能を実装するため新設。
 * Roomは使わずJSON形式でinternal storageに保存する。
 *
 * @param id ショートカットの一意識別子 (UUID)
 * @param label ボタンに表示するラベル
 * @param command 実行するコマンドテンプレート。複数行可。
 *               プレースホルダ (例: {{hostname}}) を含めることができる。
 * @param hostId ホスト固有のショートカットの場合はホストID、
 *               グローバルショートカットの場合はnull
 * @param category CliCommandRegistryのカテゴリID (例: "git", "docker", "claude_code")。
 *                 カテゴリなし(カスタム)の場合はnull。
 *                 変更理由: ShortcutListScreenでカテゴリ別グルーピング表示を
 *                 実現するためフィールドを追加。既存JSONにcategoryキーがない場合は
 *                 optString でnullとして扱うため後方互換を維持する。
 * @param templateKey 公式テンプレートの安定識別子。
 *                    変更理由: Claude Code / Codex などの既定コマンドを
 *                    後から最新化しても、ユーザ作成ショートカットを上書きせず
 *                    既知テンプレートだけを同期できるようにするため追加。
 * @param order 表示順序 (昇順)
 */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val hostId: Long? = null,
    val category: String? = null,
    val templateKey: String? = null,
    val order: Int = 0
) {
    /** JSONObjectへの変換 */
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_LABEL, label)
        put(KEY_COMMAND, command)
        // hostId が null の場合は JSONObject.NULL を設定
        put(KEY_HOST_ID, hostId ?: JSONObject.NULL)
        // category が null の場合は JSONObject.NULL を設定
        put(KEY_CATEGORY, category ?: JSONObject.NULL)
        put(KEY_TEMPLATE_KEY, templateKey ?: JSONObject.NULL)
        put(KEY_ORDER, order)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_LABEL = "label"
        private const val KEY_COMMAND = "command"
        private const val KEY_HOST_ID = "hostId"
        private const val KEY_CATEGORY = "category"
        private const val KEY_TEMPLATE_KEY = "templateKey"
        private const val KEY_ORDER = "order"

        /**
         * JSONObjectからShortcutを復元。
         * 変更理由: categoryフィールドを追加。旧データにはcategoryキーがないため
         * optString(KEY_CATEGORY, null) で安全にnullとして読み込む（後方互換）。
         */
        fun fromJson(json: JSONObject): Shortcut = Shortcut(
            id = json.getString(KEY_ID),
            label = json.getString(KEY_LABEL),
            command = json.getString(KEY_COMMAND),
            hostId = if (json.isNull(KEY_HOST_ID)) null else json.getLong(KEY_HOST_ID),
            category = if (json.isNull(KEY_CATEGORY)) null else json.optString(KEY_CATEGORY),
            templateKey = if (json.isNull(KEY_TEMPLATE_KEY)) null else json.optString(KEY_TEMPLATE_KEY),
            order = json.optInt(KEY_ORDER, 0)
        )

        /**
         * 初回起動時に生成するデフォルトショートカット (グローバル)。
         *
         * 変更理由: よく使うコマンドを幅広くカバーするよう拡充。
         * 汎用操作、制御文字、Git基本操作、Claude Code / Codex 起動を含む。
         * 変更理由: categoryフィールドを各ショートカットに設定し、
         * グルーピング表示に対応。
         */
        fun createDefaults(): List<Shortcut> = listOf(
            shortcut("control", "ctrl-c", "Ctrl+C", "\u0003", 0),
            shortcut("control", "ctrl-d", "Ctrl+D", "\u0004", 1),
            shortcut("control", "ctrl-z", "Ctrl+Z", "\u001A", 2),
            shortcut("control", "esc", "Esc", "\u001B", 3),
            shortcut("general", "ls-la", "ls -la", "ls -la\n", 10),
            shortcut("general", "pwd", "pwd", "pwd\n", 11),
            shortcut("general", "clear", "clear", "clear\n", 12),
            shortcut("git", "status", "git status", "git status\n", 20),
            shortcut("git", "diff", "git diff", "git diff\n", 21),
            shortcut("git", "log", "git log", "git log --oneline -10\n", 22),
            shortcut("git", "pull", "git pull", "git pull\n", 23),
            shortcut("claude_code", "launch", "claude", "claude\n", 30),
            shortcut("claude_code", "continue", "claude -c", "claude -c\n", 31),
            shortcut("claude_code", "resume", "claude -r", "claude -r ", 32),
            shortcut("claude_code", "print", "claude -p", "claude -p \"", 33),
            shortcut("claude_code", "slash-help", "/help", "/help\n", 40),
            shortcut("claude_code", "slash-compact", "/compact", "/compact\n", 41),
            shortcut("claude_code", "slash-status", "/status", "/status\n", 42),
            shortcut("claude_code", "slash-model", "/model", "/model\n", 43),
            shortcut("codex", "launch", "codex", "codex\n", 50),
            shortcut("codex", "exec", "codex exec", "codex exec \"", 51),
            shortcut("codex", "review", "codex review", "codex review\n", 52),
            shortcut("codex", "resume-last", "codex resume --last", "codex resume --last\n", 53),
            shortcut("codex", "slash-diff", "/diff", "/diff\n", 60),
            shortcut("codex", "slash-review", "/review", "/review\n", 61),
            shortcut("codex", "slash-model", "/model", "/model\n", 62),
            shortcut("codex", "slash-permissions", "/permissions", "/permissions\n", 63)
        )

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
    }
}
