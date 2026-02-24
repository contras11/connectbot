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

package org.connectbot.ui.screens.shortcutlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.data.ShortcutRepository
import org.connectbot.data.entity.Shortcut
import org.connectbot.session.CliCommandRegistry
import javax.inject.Inject

/**
 * ショートカット一覧・編集画面のViewModel。
 *
 * 変更理由: ショートカットの追加・編集・削除を行う設定画面のため新設。
 * ShortcutRepositoryを経由してCRUD操作を行う。
 */
@HiltViewModel
class ShortcutListViewModel @Inject constructor(
    private val shortcutRepository: ShortcutRepository
) : ViewModel() {

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())

    /** 全ショートカット一覧 */
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts.asStateFlow()

    init {
        loadShortcuts()
    }

    private fun loadShortcuts() {
        viewModelScope.launch {
            _shortcuts.value = shortcutRepository.loadAll()
        }
        // StateFlowの更新を監視
        viewModelScope.launch {
            shortcutRepository.shortcuts.collect { list ->
                _shortcuts.value = list
            }
        }
    }

    /** ショートカットを追加または更新する */
    fun save(shortcut: Shortcut) {
        viewModelScope.launch {
            shortcutRepository.save(shortcut)
        }
    }

    /** ショートカットを削除する */
    fun delete(id: String) {
        viewModelScope.launch {
            shortcutRepository.delete(id)
        }
    }

    /**
     * カテゴリ別にテンプレートコマンドを一括インポートする。
     * 同一カテゴリ内で同じラベルを持つものはスキップする。
     *
     * 変更理由: Claude Code / Codex / Git / Docker 等の既知コマンドを
     * ワンタップで追加できるようにするため。
     *
     * 変更理由: label単独の重複判定から(label, category)ペアに変更。
     * Claude CodeとCodexが同名コマンド("/help"等)を持つ場合でも
     * カテゴリが異なれば正しくインポートできるようにする。
     */
    fun importCategory(categoryId: String) {
        val category = CliCommandRegistry.findCategory(categoryId) ?: return
        viewModelScope.launch {
            val existing = shortcutRepository.loadAll()
            // (label, category)ペアで重複チェック
            val existingPairs = existing.map { it.label to it.category }.toSet()
            val maxOrder = existing.maxOfOrNull { it.order } ?: 0
            // 変更理由: インポート時にcategoryIdを設定し、
            // ShortcutListScreenでカテゴリ別グルーピング表示に対応する
            category.commands
                .filter { (it.label to categoryId) !in existingPairs }
                .forEachIndexed { index, cmd ->
                    shortcutRepository.save(
                        cmd.copy(
                            order = maxOrder + index + 1,
                            category = categoryId
                        )
                    )
                }
        }
    }
}
