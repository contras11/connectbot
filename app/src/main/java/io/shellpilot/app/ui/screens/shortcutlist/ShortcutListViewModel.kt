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

package io.shellpilot.app.ui.screens.shortcutlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.shellpilot.app.data.ProfileOrderRepository
import io.shellpilot.app.data.ShortcutRepository
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.session.CliCommandRegistry
import javax.inject.Inject

/**
 * ショートカット一覧・編集画面のViewModel。
 *
 * 変更理由: ショートカットの追加・編集・削除を行う設定画面のため新設。
 * ShortcutRepositoryを経由してCRUD操作を行う。
 */
@HiltViewModel
class ShortcutListViewModel @Inject constructor(
    private val shortcutRepository: ShortcutRepository,
    private val profileOrderRepository: ProfileOrderRepository
) : ViewModel() {

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())

    /** 全ショートカット一覧 */
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts.asStateFlow()

    /**
     * 変更理由: プロファイルタブの表示順序をユーザが変更できるようにする。
     * ProfileOrderRepositoryから読み込んだ順序をStateFlowで公開する。
     */
    private val _profileOrder = MutableStateFlow<List<String?>>(emptyList())

    /** プロファイルタブの表示順序 */
    val profileOrder: StateFlow<List<String?>> = _profileOrder.asStateFlow()

    private val _hiddenProfileIds = MutableStateFlow<Set<String>>(emptySet())

    /** 非表示にしたプロファイルタブID */
    val hiddenProfileIds: StateFlow<Set<String>> = _hiddenProfileIds.asStateFlow()

    private val _templateSyncMessage = MutableStateFlow<String?>(null)

    /** 公式テンプレート同期結果の表示用メッセージ */
    val templateSyncMessage: StateFlow<String?> = _templateSyncMessage.asStateFlow()

    init {
        loadShortcuts()
        loadProfileOrder()
    }

    /** プロファイルタブ順序を読み込む */
    private fun loadProfileOrder() {
        _profileOrder.value = profileOrderRepository.getOrder()
        _hiddenProfileIds.value = profileOrderRepository.getHiddenProfileIds()
        viewModelScope.launch {
            profileOrderRepository.order.collect { order ->
                _profileOrder.value = order
            }
        }
        viewModelScope.launch {
            profileOrderRepository.hiddenProfileIds.collect { hiddenIds ->
                _hiddenProfileIds.value = hiddenIds
            }
        }
    }

    private fun loadShortcuts() {
        viewModelScope.launch {
            // 変更理由: 画面へ遷移するだけで公式テンプレート同期を走らせると、
            // 古いショートカットJSONを持つ環境で毎回項目が増えたように見える。
            // 起動時は読み込みに限定し、同期はユーザが「公式テンプレートを更新」を
            // 押したときだけ実行する。
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
            val all = shortcutRepository.loadAll()
            val existing = all.firstOrNull { it.id == shortcut.id }
            val normalized = if (existing == null) {
                val nextOrder = all
                    .filter { it.hostId == shortcut.hostId && it.category == shortcut.category }
                    .maxOfOrNull { it.order }
                    ?.plus(1)
                    ?: 0
                shortcut.copy(order = nextOrder)
            } else {
                shortcut.copy(order = existing.order)
            }
            shortcutRepository.save(normalized)
        }
    }

    /** ショートカットを削除する */
    fun delete(id: String) {
        viewModelScope.launch {
            shortcutRepository.delete(id)
        }
    }

    /**
     * ショートカットを1つ上に移動する（order値を入れ替え）。
     *
     * 変更理由: ショートカットの表示順をユーザが自由に変更できるようにする。
     * 同じカテゴリ内でorder値をスワップし、並び順を永続化する。
     * SSH接続後のShortcutBarにもこの順序が反映される。
     */
    fun moveUp(shortcut: Shortcut) {
        viewModelScope.launch {
            val all = shortcutRepository.loadAll()
            // 同じカテゴリ・同じhostIdグループ内でソート
            val group = all.filter {
                it.category == shortcut.category && it.hostId == shortcut.hostId
            }.sortedBy { it.order }
            val index = group.indexOfFirst { it.id == shortcut.id }
            if (index > 0) {
                val prev = group[index - 1]
                shortcutRepository.save(shortcut.copy(order = prev.order))
                shortcutRepository.save(prev.copy(order = shortcut.order))
            }
        }
    }

    /**
     * ショートカットを1つ下に移動する（order値を入れ替え）。
     *
     * 変更理由: moveUpと対になる下方向移動。
     */
    fun moveDown(shortcut: Shortcut) {
        viewModelScope.launch {
            val all = shortcutRepository.loadAll()
            val group = all.filter {
                it.category == shortcut.category && it.hostId == shortcut.hostId
            }.sortedBy { it.order }
            val index = group.indexOfFirst { it.id == shortcut.id }
            if (index >= 0 && index < group.size - 1) {
                val next = group[index + 1]
                shortcutRepository.save(shortcut.copy(order = next.order))
                shortcutRepository.save(next.copy(order = shortcut.order))
            }
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
    /**
     * プロファイルタブを1つ上に移動する。
     *
     * 変更理由: プロファイルタブの表示順をユーザが変更できるようにする。
     * SharedPreferencesに保存されたJSON配列内の位置をスワップする。
     */
    fun moveProfileUp(profileId: String?) {
        val order = _profileOrder.value.toMutableList()
        val index = order.indexOf(profileId)
        if (index > 0) {
            order[index] = order[index - 1]
            order[index - 1] = profileId
            profileOrderRepository.saveOrder(order)
        }
    }

    /**
     * プロファイルタブを1つ下に移動する。
     *
     * 変更理由: moveProfileUpと対になる下方向移動。
     */
    fun moveProfileDown(profileId: String?) {
        val order = _profileOrder.value.toMutableList()
        val index = order.indexOf(profileId)
        if (index >= 0 && index < order.size - 1) {
            order[index] = order[index + 1]
            order[index + 1] = profileId
            profileOrderRepository.saveOrder(order)
        }
    }

    fun setProfileVisible(profileId: String?, visible: Boolean) {
        // カスタムタブはユーザ作成ショートカットの入口として常に表示する。
        val id = profileId ?: return
        profileOrderRepository.setProfileVisible(id, visible)
    }

    fun importCategory(categoryId: String) {
        val category = CliCommandRegistry.findCategory(categoryId) ?: return
        viewModelScope.launch {
            val existing = shortcutRepository.loadAll()
            // 変更理由: templateKeyを優先した重複判定により、同名スラッシュ
            // コマンドが複数ツールに存在しても正しく同期できるようにする。
            val existingTemplateKeys = existing.mapNotNull { it.templateKey }.toSet()
            val existingPairs = existing.map { it.label to it.category }.toSet()
            val maxOrder = existing.maxOfOrNull { it.order } ?: 0
            // 変更理由: インポート時にcategoryIdを設定し、
            // ShortcutListScreenでカテゴリ別グルーピング表示に対応する
            category.commands
                .filter {
                    val key = it.templateKey
                    (key == null || key !in existingTemplateKeys) &&
                        (it.label to categoryId) !in existingPairs
                }
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

    fun syncOfficialTemplates() {
        viewModelScope.launch {
            val result = shortcutRepository.syncOfficialTemplates()
            _shortcuts.value = shortcutRepository.loadAll()
            _templateSyncMessage.value =
                "公式テンプレートを更新しました（追加${result.added}件・更新${result.updated}件・識別${result.tagged}件）"
        }
    }

    fun consumeTemplateSyncMessage() {
        _templateSyncMessage.value = null
    }
}
