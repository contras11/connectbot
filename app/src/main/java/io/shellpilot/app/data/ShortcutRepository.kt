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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.di.CoroutineDispatchers
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ショートカットコマンドのリポジトリ。
 * JSON形式でinternal storageに保存する。Roomは使用しない。
 *
 * 変更理由: ショートカット機能のデータ層を担当。
 * Repositoryパターンで実装し、StateFlowでリアクティブに状態を公開する。
 *
 * 保存先: {filesDir}/shortcuts.json
 * フォーマット: { "version": 1, "shortcuts": [ ... ] }
 */
@Singleton
class ShortcutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers
) {
    private val mutex = Mutex()
    private val file: File = File(context.filesDir, FILE_NAME)

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())

    /** 全ショートカットのリアクティブストリーム */
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts.asStateFlow()

    private var loaded = false

    /**
     * JSONファイルからショートカットを読み込む。
     * 初回呼び出し時にファイルが存在しなければデフォルトショートカットを生成する。
     */
    suspend fun loadAll(): List<Shortcut> = mutex.withLock {
        if (loaded) return@withLock _shortcuts.value

        val result = withContext(dispatchers.io) {
            if (!file.exists()) {
                // 初回起動: デフォルトショートカットを生成して保存
                val defaults = Shortcut.createDefaults()
                writeToFileInternal(defaults)
                defaults
            } else {
                readFromFileInternal()
            }
        }
        _shortcuts.value = result
        loaded = true
        result
    }

    /**
     * 特定ホスト向け + グローバルのショートカットを取得する。
     * グローバル (hostId == null) を先に、ホスト固有を後に並べる。
     */
    suspend fun getForHost(hostId: Long): List<Shortcut> {
        val all = loadAll()
        return all.filter { it.hostId == null || it.hostId == hostId }
            .sortedWith(compareBy<Shortcut> { it.hostId != null }.thenBy { it.order })
    }

    /** ショートカットを追加または更新する */
    suspend fun save(shortcut: Shortcut) = mutex.withLock {
        val current = _shortcuts.value.toMutableList()
        val index = current.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) {
            current[index] = shortcut
        } else {
            current.add(shortcut)
        }
        withContext(dispatchers.io) { writeToFileInternal(current) }
        _shortcuts.value = current
    }

    /** ショートカットを削除する */
    suspend fun delete(id: String) = mutex.withLock {
        val current = _shortcuts.value.filter { it.id != id }
        withContext(dispatchers.io) { writeToFileInternal(current) }
        _shortcuts.value = current
    }

    /** 全ショートカットを置き換える (並べ替え時等) */
    suspend fun replaceAll(shortcuts: List<Shortcut>) = mutex.withLock {
        withContext(dispatchers.io) { writeToFileInternal(shortcuts) }
        _shortcuts.value = shortcuts
    }

    // --- 内部I/O (mutex保持下で呼び出すこと) ---

    private fun readFromFileInternal(): List<Shortcut> {
        return try {
            val text = file.readText()
            val root = JSONObject(text)
            val array = root.getJSONArray(KEY_SHORTCUTS)
            (0 until array.length()).map { Shortcut.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            Timber.e(e, "ShortcutRepository: JSONの読込に失敗")
            emptyList()
        }
    }

    private fun writeToFileInternal(shortcuts: List<Shortcut>) {
        try {
            val root = JSONObject().apply {
                put(KEY_VERSION, CURRENT_VERSION)
                put(KEY_SHORTCUTS, JSONArray().apply {
                    shortcuts.forEach { put(it.toJson()) }
                })
            }
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Timber.e(e, "ShortcutRepository: JSONの書込に失敗")
        }
    }

    companion object {
        internal const val FILE_NAME = "shortcuts.json"
        private const val KEY_VERSION = "version"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val CURRENT_VERSION = 1
    }
}
