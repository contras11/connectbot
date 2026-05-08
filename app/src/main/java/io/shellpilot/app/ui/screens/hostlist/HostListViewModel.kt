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

package io.shellpilot.app.ui.screens.hostlist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.shellpilot.app.R
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.di.CoroutineDispatchers
import io.shellpilot.app.service.ServiceError
import io.shellpilot.app.service.TerminalManager
import io.shellpilot.app.util.PreferenceConstants
import javax.inject.Inject

private const val DEFAULT_PROFILE_ID = 1L

enum class ConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

data class HostListFilterState(
    val protocol: String? = null,
    val connectedOnly: Boolean = false,
    val keysOnly: Boolean = false,
    val profileOnly: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = protocol != null || connectedOnly || keysOnly || profileOnly
}

data class HostListUiState(
    val hosts: List<Host> = emptyList(),
    val visibleHosts: List<Host> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortedByColor: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val filterState: HostListFilterState = HostListFilterState(),
    val exportedJson: String? = null,
    val exportResult: ExportResult? = null,
    val importResult: ImportResult? = null
)

data class ImportResult(
    val hostsImported: Int,
    val hostsSkipped: Int,
    val profilesImported: Int,
    val profilesSkipped: Int
)

data class ExportResult(
    val hostCount: Int,
    val profileCount: Int
)

@HiltViewModel
class HostListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private var terminalManager: TerminalManager? = null

    /**
     * 変更理由: 意図的に切断されたホストIDを追跡する。
     * メニューから切断した場合に赤いエラーアイコンを表示しないようにするため。
     */
    private val intentionallyDisconnected = mutableSetOf<Long>()

    /**
     * 変更理由: 接続「失敗」(ServiceError.ConnectionFailed) が発生したホストの
     * nicknameを追跡する。正常切断との区別に使用する。
     * - 接続失敗 → DISCONNECTED (赤！アイコン)
     * - 正常切断 (exit, 戻るボタン等) → UNKNOWN (アイコンなし)
     * nicknameで追跡するのは ServiceError.ConnectionFailed が hostname/nickname のみを
     * 保持しており hostId を持たないため。
     */
    private val failedHostNicknames = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(
        HostListUiState(
            isLoading = true,
            sortedByColor = sharedPreferences.getBoolean(PreferenceConstants.SORT_BY_COLOR, false)
        )
    )
    val uiState: StateFlow<HostListUiState> = _uiState.asStateFlow()

    init {
        observeHosts()
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager
            // Observe host status changes from Flow
            observeHostStatusChanges()
            // Collect service errors from TerminalManager
            collectServiceErrors()
            // Update initial connection states
            updateConnectionStates(_uiState.value.hosts)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeHosts() {
        viewModelScope.launch {
            _uiState
                .map { it.sortedByColor }
                .distinctUntilChanged()
                .flatMapLatest { sortedByColor ->
                    if (sortedByColor) {
                        repository.observeHostsSortedByColor()
                    } else {
                        repository.observeHosts()
                    }
                }
                .collect { hosts ->
                    updateConnectionStates(hosts)
                    _uiState.update {
                        val visibleHosts = filterHosts(
                            hosts = hosts,
                            searchQuery = it.searchQuery,
                            filterState = it.filterState,
                            connectionStates = it.connectionStates
                        )
                        it.copy(
                            hosts = hosts,
                            visibleHosts = visibleHosts,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    private fun observeHostStatusChanges() {
        val manager = terminalManager ?: return
        viewModelScope.launch {
            manager.hostStatusChangedFlow.collect {
                // Update connection states when terminal manager notifies us of changes
                updateConnectionStates(_uiState.value.hosts)
            }
        }
    }

    private fun collectServiceErrors() {
        val manager = terminalManager ?: return
        viewModelScope.launch {
            manager.serviceErrors.collect { error ->
                val errorMessage = formatServiceError(error)
                // 変更理由: 接続失敗ホストを追跡し、正常切断と区別する。
                // ServiceError.ConnectionFailed のみ赤アイコン対象とする。
                // 正常切断 (exit, back, disconnect menu) は UNKNOWN を返すため
                // failedHostNicknames に含まれない限り赤アイコンを表示しない。
                if (error is ServiceError.ConnectionFailed) {
                    failedHostNicknames.add(error.hostNickname)
                    updateConnectionStates(_uiState.value.hosts)
                }
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    private fun formatServiceError(error: ServiceError): String = when (error) {
        is ServiceError.KeyLoadFailed -> {
            context.getString(R.string.error_key_load_failed, error.keyName, error.reason)
        }

        is ServiceError.ConnectionFailed -> {
            context.getString(
                R.string.error_connection_failed,
                error.hostNickname,
                error.hostname,
                error.reason
            )
        }

        is ServiceError.PortForwardLoadFailed -> {
            context.getString(
                R.string.error_port_forward_load_failed,
                error.hostNickname,
                error.reason
            )
        }

        is ServiceError.HostSaveFailed -> {
            context.getString(R.string.error_host_save_failed, error.hostNickname, error.reason)
        }

        is ServiceError.ColorSchemeLoadFailed -> {
            context.getString(R.string.error_color_scheme_load_failed, error.reason)
        }
    }

    private fun updateConnectionStates(hosts: List<Host>) {
        val states = hosts.associate { host ->
            host.id to getConnectionState(host)
        }
        _uiState.update {
            val visibleHosts = filterHosts(
                hosts = hosts,
                searchQuery = it.searchQuery,
                filterState = it.filterState,
                connectionStates = states
            )
            it.copy(connectionStates = states, visibleHosts = visibleHosts)
        }
    }

    private fun getConnectionState(host: Host): ConnectionState {
        val manager = terminalManager ?: return ConnectionState.UNKNOWN

        // Check if connected by ID
        if (manager.bridgesFlow.value.any { it.host.id == host.id }) {
            // 変更理由: 再接続したら意図的切断・接続失敗のトラッキングをクリア
            intentionallyDisconnected.remove(host.id)
            failedHostNicknames.remove(host.nickname)
            return ConnectionState.CONNECTED
        }

        // Check if in disconnected list by comparing ID
        if (manager.disconnectedFlow.value.any { it.id == host.id }) {
            // 変更理由: 意図的切断 (メニューからの切断) はアイコンなし (UNKNOWN)
            if (host.id in intentionallyDisconnected) {
                return ConnectionState.UNKNOWN
            }
            // 変更理由: 接続「失敗」(ServiceError.ConnectionFailed) の場合のみ
            // 赤！アイコン (DISCONNECTED) を表示する。
            // 正常切断 (exit コマンド, 戻るボタン等) は UNKNOWN (アイコンなし) を返す。
            // これにより「接続に失敗した時だけ赤丸が出る」という期待動作を実現する。
            if (host.nickname in failedHostNicknames) {
                return ConnectionState.DISCONNECTED
            }
            // 正常切断 → アイコンなし
            return ConnectionState.UNKNOWN
        }

        return ConnectionState.UNKNOWN
    }

    fun toggleSortOrder() {
        val newSortedByColor = !_uiState.value.sortedByColor
        sharedPreferences.edit { putBoolean(PreferenceConstants.SORT_BY_COLOR, newSortedByColor) }
        _uiState.update { it.copy(sortedByColor = newSortedByColor) }
    }

    fun enterSearchMode() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun exitSearchMode() {
        _uiState.update {
            val visibleHosts = filterHosts(
                hosts = it.hosts,
                searchQuery = "",
                filterState = it.filterState,
                connectionStates = it.connectionStates
            )
            it.copy(isSearchActive = false, searchQuery = "", visibleHosts = visibleHosts)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            val visibleHosts = filterHosts(
                hosts = it.hosts,
                searchQuery = query,
                filterState = it.filterState,
                connectionStates = it.connectionStates
            )
            it.copy(searchQuery = query, visibleHosts = visibleHosts)
        }
    }

    fun setProtocolFilter(protocol: String?) {
        updateFilter { it.copy(protocol = protocol) }
    }

    fun toggleConnectedOnly() {
        updateFilter { it.copy(connectedOnly = !it.connectedOnly) }
    }

    fun toggleKeysOnly() {
        updateFilter { it.copy(keysOnly = !it.keysOnly) }
    }

    fun toggleProfileOnly() {
        updateFilter { it.copy(profileOnly = !it.profileOnly) }
    }

    fun clearFilters() {
        updateFilter { HostListFilterState() }
    }

    private fun updateFilter(transform: (HostListFilterState) -> HostListFilterState) {
        _uiState.update {
            val nextFilter = transform(it.filterState)
            val visibleHosts = filterHosts(
                hosts = it.hosts,
                searchQuery = it.searchQuery,
                filterState = nextFilter,
                connectionStates = it.connectionStates
            )
            it.copy(filterState = nextFilter, visibleHosts = visibleHosts)
        }
    }

    /**
     * 変更理由: 検索/フィルタはレビュー画面の即時確認が目的のため、
     * DBクエリを増やさず現在の表示リスト上で安全に絞り込む。
     */
    private fun filterHosts(
        hosts: List<Host>,
        searchQuery: String,
        filterState: HostListFilterState,
        connectionStates: Map<Long, ConnectionState>
    ): List<Host> {
        val normalizedQuery = searchQuery.trim().lowercase()
        return hosts
            .asSequence()
            .filter { host ->
                normalizedQuery.isBlank() ||
                    host.nickname.contains(normalizedQuery, ignoreCase = true) ||
                    host.username.contains(normalizedQuery, ignoreCase = true) ||
                    host.hostname.contains(normalizedQuery, ignoreCase = true) ||
                    host.protocol.contains(normalizedQuery, ignoreCase = true)
            }
            .filter { host ->
                filterState.protocol == null || host.protocol == filterState.protocol
            }
            .filter { host ->
                !filterState.connectedOnly || connectionStates[host.id] == ConnectionState.CONNECTED
            }
            .filter { host ->
                !filterState.keysOnly || (host.protocol == "ssh" && host.useKeys)
            }
            .filter { host ->
                !filterState.profileOnly || host.profileId != DEFAULT_PROFILE_ID
            }
            .toList()
    }

    fun deleteHost(host: Host) {
        viewModelScope.launch {
            try {
                repository.deleteHost(host)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete host")
                }
            }
        }
    }

    fun duplicateHost(host: Host) {
        viewModelScope.launch {
            try {
                // Create new host with reset fields
                val newHost = host.copy(
                    id = 0L,
                    nickname = context.getString(R.string.host_duplicate_nickname, host.nickname),
                    lastConnect = 0,
                    hostKeyAlgo = null
                )
                val savedHost = repository.saveHost(newHost)

                // Copy port forwards
                val portForwards = repository.getPortForwardsForHost(host.id)
                for (pf in portForwards) {
                    repository.savePortForward(pf.copy(id = 0L, hostId = savedHost.id))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to duplicate host")
                }
            }
        }
    }

    fun forgetHostKeys(host: Host) {
        viewModelScope.launch {
            try {
                repository.deleteKnownHostsForHost(host.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to forget host keys")
                }
            }
        }
    }

    fun disconnectAll() {
        // 変更理由: 全ホスト一括切断時も意図的切断として記録
        terminalManager?.bridgesFlow?.value?.forEach { bridge ->
            intentionallyDisconnected.add(bridge.host.id)
        }
        terminalManager?.disconnectAll(immediate = true, excludeLocal = false)
    }

    fun disconnectHost(host: Host) {
        // 変更理由: メニューからの切断を意図的切断として記録し、赤アイコン表示を防止
        intentionallyDisconnected.add(host.id)
        val bridge = terminalManager?.bridgesFlow?.value?.find { it.host.id == host.id }
        bridge?.dispatchDisconnect(true)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun exportHosts() {
        viewModelScope.launch {
            try {
                val (json, exportCounts) = withContext(dispatchers.io) {
                    repository.exportHostsToJson()
                }
                val exportResult = ExportResult(
                    hostCount = exportCounts.hostCount,
                    profileCount = exportCounts.profileCount
                )
                _uiState.update { it.copy(exportedJson = json, exportResult = exportResult) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to export hosts")
                }
            }
        }
    }

    fun clearExportedJson() {
        _uiState.update { it.copy(exportedJson = null, exportResult = null) }
    }

    fun importHosts(jsonString: String) {
        viewModelScope.launch {
            try {
                val importCounts = withContext(dispatchers.io) {
                    repository.importHostsFromJson(jsonString)
                }
                val importResult = ImportResult(
                    hostsImported = importCounts.hostsImported,
                    hostsSkipped = importCounts.hostsSkipped,
                    profilesImported = importCounts.profilesImported,
                    profilesSkipped = importCounts.profilesSkipped
                )
                _uiState.update { it.copy(importResult = importResult) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to import hosts")
                }
            }
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }
}
