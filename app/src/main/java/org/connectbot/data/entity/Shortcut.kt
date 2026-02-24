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

package org.connectbot.data.entity

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
 * @param order 表示順序 (昇順)
 */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val hostId: Long? = null,
    val category: String? = null,
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
        put(KEY_ORDER, order)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_LABEL = "label"
        private const val KEY_COMMAND = "command"
        private const val KEY_HOST_ID = "hostId"
        private const val KEY_CATEGORY = "category"
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
            // 汎用コマンド
            Shortcut(label = "ls -la", command = "ls -la\n", category = "general", order = 0),
            Shortcut(label = "pwd", command = "pwd\n", category = "general", order = 1),
            Shortcut(label = "clear", command = "clear\n", category = "general", order = 2),
            Shortcut(label = "Ctrl+C", command = "\u0003", category = "general", order = 3),
            Shortcut(label = "Ctrl+D", command = "\u0004", category = "general", order = 4),
            // Git基本操作
            Shortcut(label = "git st", command = "git status\n", category = "git", order = 10),
            Shortcut(label = "git diff", command = "git diff\n", category = "git", order = 11),
            Shortcut(label = "git log", command = "git log --oneline -10\n", category = "git", order = 12),
            Shortcut(label = "git pull", command = "git pull\n", category = "git", order = 13),
            // Claude Code
            Shortcut(label = "claude", command = "claude\n", category = "claude_code", order = 20),
            Shortcut(label = "/help", command = "/help\n", category = "claude_code", order = 21),
            Shortcut(label = "/compact", command = "/compact\n", category = "claude_code", order = 22),
            Shortcut(label = "/cost", command = "/cost\n", category = "claude_code", order = 23),
            Shortcut(label = "/status", command = "/status\n", category = "claude_code", order = 24),
            // 変更理由: Codexカテゴリのデフォルトプリセットを追加。
            // 設定画面とSSH接続後のプロファイルタブの両方でCodexが
            // 表示されるよう、初回起動時からCodexコマンドを含める。
            Shortcut(label = "codex", command = "codex\n", category = "codex", order = 30),
            Shortcut(label = "codex -q", command = "codex -q \"", category = "codex", order = 31),
            Shortcut(label = "/diff", command = "/diff\n", category = "codex", order = 32),
            Shortcut(label = "/undo", command = "/undo\n", category = "codex", order = 33)
        )
    }
}
