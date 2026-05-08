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

import io.shellpilot.app.data.entity.Host
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ショートカットコマンド内のプレースホルダを実際の値に展開する。
 *
 * 変更理由: コマンドテンプレートにホスト情報や日時を動的に埋め込むため新設。
 *
 * 対応プレースホルダ:
 *   {{hostname}}  → ホスト名 (例: "example.com")
 *   {{username}}  → ユーザ名 (例: "root")
 *   {{port}}      → ポート番号 (例: "22")
 *   {{nickname}}  → ニックネーム (例: "My Server")
 *   {{protocol}}  → プロトコル (例: "ssh")
 *   {{date}}      → 現在日付 (yyyy-MM-dd)
 *   {{time}}      → 現在時刻 (HH:mm:ss)
 *   {{timestamp}} → UNIXタイムスタンプ (秒)
 */
object ShortcutExpander {

    private val PLACEHOLDER_REGEX = Regex("\\{\\{(\\w+)}}")

    /**
     * コマンドテンプレート内のプレースホルダを展開する。
     *
     * @param template プレースホルダを含むコマンド文字列
     * @param host 現在接続中のホスト情報
     * @param now 日時取得用 (テストでの注入を可能にするため引数化)
     * @return プレースホルダが展開されたコマンド文字列。
     *         未知のプレースホルダはそのまま残す。
     */
    fun expand(template: String, host: Host, now: Date = Date()): String {
        return PLACEHOLDER_REGEX.replace(template) { match ->
            val key = match.groupValues[1]
            resolve(key, host, now) ?: match.value
        }
    }

    private fun resolve(key: String, host: Host, now: Date): String? {
        return when (key) {
            "hostname" -> host.hostname
            "username" -> host.username
            "port" -> host.port.toString()
            "nickname" -> host.nickname
            "protocol" -> host.protocol
            "date" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            "time" -> SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
            "timestamp" -> (now.time / 1000).toString()
            else -> null // 未知のプレースホルダはそのまま残す
        }
    }
}
