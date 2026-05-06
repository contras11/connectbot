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

package io.shellpilot.app.data

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shellpilot.app.session.CliCommandRegistry
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * プロファイルタブの表示順序を管理するリポジトリ。
 *
 * 変更理由: ShortcutBarのプロファイルタブ(カスタム/Git/Docker等)の
 * 表示順序をユーザが変更できるようにするため新設。
 * SharedPreferencesにJSON配列として保存する。
 *
 * 保存形式: ["null", "claude_code", "git", "docker", ...] (nullはカスタムタブ)
 */
@Singleton
class ProfileOrderRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * 保存されたプロファイルタブ順序を取得する。
     * 未保存の場合はデフォルト順序を返す。
     *
     * @return プロファイルIDのリスト (nullはカスタムタブ)
     */
    fun getOrder(): List<String?> {
        val json = prefs.getString(PREF_KEY, null) ?: return defaultOrder()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val value = array.getString(i)
                if (value == NULL_MARKER) null else value
            }
        } catch (_: Exception) {
            defaultOrder()
        }
    }

    /**
     * プロファイルタブ順序を保存する。
     *
     * @param order プロファイルIDのリスト (nullはカスタムタブ)
     */
    fun saveOrder(order: List<String?>) {
        val array = JSONArray()
        order.forEach { array.put(it ?: NULL_MARKER) }
        prefs.edit().putString(PREF_KEY, array.toString()).apply()
    }

    /**
     * デフォルトのプロファイルタブ順序。
     * カスタム + CliCommandRegistryの全カテゴリ順。
     */
    fun defaultOrder(): List<String?> {
        return buildList {
            add(null) // カスタムタブ
            CliCommandRegistry.categories.forEach { add(it.id) }
        }
    }

    companion object {
        private const val PREF_KEY = "profile_tab_order"
        private const val NULL_MARKER = "null"
    }
}
