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

package io.shellpilot.app.ui.screens.hosteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.util.HostConstants
import io.shellpilot.app.util.SecurePasswordStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HostEditorUiState(
    val hostId: Long = -1L,
    val quickConnect: String = "",
    val quickConnectError: String? = null,
    val nickname: String = "",
    val protocol: String = "ssh",
    val username: String = "",
    val hostname: String = "",
    val port: String = "22",
    val color: String = "gray",
    val pubkeyId: Long = -1L,
    val availablePubkeys: List<Pubkey> = emptyList(),
    val profileId: Long? = 1L,
    val availableProfiles: List<Profile> = emptyList(),
    val useAuthAgent: String = "no",
    val compression: Boolean = false,
    val wantSession: Boolean = true,
    val stayConnected: Boolean = false,
    val quickDisconnect: Boolean = false,
    val postLogin: String = "",
    val jumpHostId: Long? = null,
    val availableJumpHosts: List<Host> = emptyList(),
    val ipVersion: String = "IPV4_AND_IPV6",
    val password: String = "",
    val hasExistingPassword: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val pubkeyRepository: PubkeyRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: android.content.SharedPreferences,
    private val securePasswordStorage: SecurePasswordStorage
) : ViewModel() {

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val _uiState = MutableStateFlow(HostEditorUiState(hostId = hostId))
    val uiState: StateFlow<HostEditorUiState> = _uiState.asStateFlow()

    init {
        loadPubkeys()
        loadJumpHosts()
        loadProfiles()
        if (hostId != -1L) {
            loadHost()
        } else {
            // For new hosts, apply the default profile from settings
            val defaultProfileId = prefs.getLong("defaultProfileId", 0L)
            if (defaultProfileId > 0) {
                _uiState.update { it.copy(profileId = defaultProfileId) }
            }
        }
    }

    private fun loadPubkeys() {
        viewModelScope.launch {
            try {
                val pubkeys = pubkeyRepository.getAll()
                _uiState.update { it.copy(availablePubkeys = pubkeys) }
            } catch (e: Exception) {
                // Don't fail the whole screen if pubkeys can't be loaded
                _uiState.update { it.copy(availablePubkeys = emptyList()) }
            }
        }
    }

    private fun loadJumpHosts() {
        viewModelScope.launch {
            try {
                // Get all SSH hosts that can be used as jump hosts
                // 変更理由: 現在編集中ホストへ戻る経路を持つ候補を除外し、
                // ProxyJumpの循環参照をUI選択時点で防ぐ。
                val sshHosts = filterValidJumpHostCandidates(repository.getSshHosts())
                _uiState.update { it.copy(availableJumpHosts = sshHosts) }
            } catch (e: Exception) {
                // Don't fail the whole screen if jump hosts can't be loaded
                _uiState.update { it.copy(availableJumpHosts = emptyList()) }
            }
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                val profiles = profileRepository.getAll()
                _uiState.update { it.copy(availableProfiles = profiles) }
            } catch (e: Exception) {
                // Don't fail the whole screen if profiles can't be loaded
                _uiState.update { it.copy(availableProfiles = emptyList()) }
            }
        }
    }

    private fun loadHost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val host = repository.findHostById(hostId)
                if (host != null) {
                    val hasPassword = securePasswordStorage.hasPassword(hostId)
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname,
                            protocol = host.protocol,
                            username = host.username,
                            hostname = host.hostname,
                            port = host.port.toString(),
                            color = host.color ?: "gray",
                            pubkeyId = host.pubkeyId,
                            profileId = host.profileId,
                            useAuthAgent = host.useAuthAgent ?: "no",
                            compression = host.compression,
                            wantSession = host.wantSession,
                            stayConnected = host.stayConnected,
                            quickDisconnect = host.quickDisconnect,
                            postLogin = host.postLogin ?: "",
                            jumpHostId = host.jumpHostId,
                            ipVersion = host.ipVersion,
                            hasExistingPassword = hasPassword,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "ホストが見つかりません")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "ホストを読み込めませんでした")
                }
            }
        }
    }

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateProtocol(value: String) {
        _uiState.update {
            it.copy(
                protocol = value,
                pubkeyId = if (value == "ssh") it.pubkeyId else HostConstants.PUBKEYID_NEVER,
                useAuthAgent = if (value == "ssh") it.useAuthAgent else HostConstants.AUTHAGENT_NO,
                compression = if (value == "ssh") it.compression else false,
                jumpHostId = if (value == "ssh") it.jumpHostId else null
            )
        }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun updateHostname(value: String) {
        _uiState.update { it.copy(hostname = value) }
    }

    fun updatePort(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(port = value) }
        }
    }

    fun updateQuickConnect(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    quickConnect = value,
                    quickConnectError = null,
                    nickname = "",
                    protocol = "ssh",
                    username = "",
                    hostname = "",
                    port = "22"
                )
            }
            return
        }

        when (val parsed = parseQuickConnect(trimmed)) {
            is QuickConnectParseResult.Valid -> {
                // 変更理由: local/telnet/ssh URIの意図を保存前に正規化し、
                // 不正な入力をSSHホストとして保存しない。
                val isSsh = parsed.protocol == "ssh"
                val isLocal = parsed.protocol == "local"
                _uiState.update {
                    it.copy(
                        quickConnect = value,
                        quickConnectError = null,
                        nickname = parsed.nickname,
                        protocol = parsed.protocol,
                        username = if (isSsh) parsed.username else "",
                        hostname = if (isLocal) "" else parsed.hostname,
                        port = parsed.port.toString(),
                        pubkeyId = if (isSsh) it.pubkeyId else HostConstants.PUBKEYID_NEVER,
                        useAuthAgent = if (isSsh) it.useAuthAgent else HostConstants.AUTHAGENT_NO,
                        compression = if (isSsh) it.compression else false,
                        jumpHostId = if (isSsh) it.jumpHostId else null
                    )
                }
            }

            is QuickConnectParseResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        quickConnect = value,
                        quickConnectError = parsed.message,
                        hostname = "",
                        username = "",
                        nickname = "",
                        protocol = "ssh"
                    )
                }
            }
        }
    }

    private sealed interface QuickConnectParseResult {
        data class Valid(
            val protocol: String,
            val nickname: String,
            val username: String,
            val hostname: String,
            val port: Int
        ) : QuickConnectParseResult

        data class Invalid(val message: String) : QuickConnectParseResult
    }

    private fun parseQuickConnect(value: String): QuickConnectParseResult {
        parseLocalQuickConnect(value)?.let { nickname ->
            return QuickConnectParseResult.Valid(
                protocol = "local",
                nickname = nickname,
                username = "",
                hostname = "",
                port = 0
            )
        }

        val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(value)
        val scheme = schemeMatch?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme !in setOf("ssh", "telnet")) {
            return QuickConnectParseResult.Invalid("ssh://、telnet://、local://、または [user@]host[:port] 形式で入力してください")
        }

        return if (scheme == "ssh" || scheme == "telnet") {
            parseNetworkUriQuickConnect(value, scheme)
        } else {
            parseImplicitSshQuickConnect(value)
        }
    }

    private fun parseNetworkUriQuickConnect(value: String, protocol: String): QuickConnectParseResult {
        val match = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://(?:([^@/?#]+)@)?([^:/?#]+)(?::(\\d+))?(?:/[^#?]*)?(?:\\?[^#]*)?(?:#(.+))?$")
            .find(value)
            ?: return QuickConnectParseResult.Invalid("$protocol://host[:port] 形式で入力してください")

        val username = match.groupValues[1]
        val hostname = match.groupValues[2]
        val portText = match.groupValues[3]
        val fragment = match.groupValues[4]
        val port = parsePortOrError(portText, if (protocol == "ssh") 22 else 23)
            ?: return QuickConnectParseResult.Invalid("ポートは1〜65535の数値で入力してください")
        val nickname = fragment.ifBlank { value }

        return QuickConnectParseResult.Valid(
            protocol = protocol,
            nickname = nickname,
            username = if (protocol == "ssh") username else "",
            hostname = hostname,
            port = port
        )
    }

    private fun parseImplicitSshQuickConnect(value: String): QuickConnectParseResult {
        val match = Regex("^(?:([^@\\s]+)@)?([^:@\\s]+)(?::(\\d+))?$").find(value)
            ?: return QuickConnectParseResult.Invalid("[user@]host[:port] 形式で入力してください")

        val (username, hostname, portText) = match.destructured
        val port = parsePortOrError(portText, 22)
            ?: return QuickConnectParseResult.Invalid("ポートは1〜65535の数値で入力してください")

        return QuickConnectParseResult.Valid(
            protocol = "ssh",
            nickname = value,
            username = username,
            hostname = hostname,
            port = port
        )
    }

    private fun parsePortOrError(value: String, defaultPort: Int): Int? {
        if (value.isBlank()) {
            return defaultPort
        }
        val port = value.toIntOrNull() ?: return null
        return port.takeIf { it in 1..65535 }
    }

    private fun parseLocalQuickConnect(value: String): String? {
        if (!value.startsWith("local:", ignoreCase = true)) {
            return null
        }

        val payload = value
            .substringAfter(':', "")
            .removePrefix("//")
        val nickname = when {
            payload.startsWith("#") -> payload.removePrefix("#")
            payload.contains("#") -> payload.substringAfter("#")
            payload.isNotBlank() -> payload.substringBefore("?").substringBefore("/")
            else -> ""
        }.trim()

        return nickname.ifBlank { "Local" }
    }

    fun updateColor(value: String) {
        _uiState.update { it.copy(color = value) }
    }

    fun updatePubkeyId(value: Long) {
        _uiState.update { it.copy(pubkeyId = value) }
    }

    fun updateProfileId(value: Long?) {
        _uiState.update { it.copy(profileId = value) }
    }

    fun updateUseAuthAgent(value: String) {
        _uiState.update { it.copy(useAuthAgent = value) }
    }

    fun updateCompression(value: Boolean) {
        _uiState.update { it.copy(compression = value) }
    }

    fun updateWantSession(value: Boolean) {
        _uiState.update { it.copy(wantSession = value) }
    }

    fun updateStayConnected(value: Boolean) {
        _uiState.update { it.copy(stayConnected = value) }
    }

    fun updateQuickDisconnect(value: Boolean) {
        _uiState.update { it.copy(quickDisconnect = value) }
    }

    fun updatePostLogin(value: String) {
        _uiState.update { it.copy(postLogin = value) }
    }

    fun updateJumpHostId(value: Long?) {
        _uiState.update { it.copy(jumpHostId = value) }
    }

    fun updateIpVersion(value: String) {
        _uiState.update { it.copy(ipVersion = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun clearSavedPassword() {
        _uiState.update { it.copy(password = "", hasExistingPassword = false) }
    }

    fun saveHost(useExpandedMode: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSaving = true, saveSucceeded = false, error = null)
            }
            try {
                val state = _uiState.value
                if (!useExpandedMode && state.quickConnectError != null) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "クイック接続の入力を確認してください")
                    }
                    return@launch
                }
                if (!useExpandedMode && state.quickConnect.isBlank()) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "クイック接続を入力してください")
                    }
                    return@launch
                }
                if (state.protocol != "local" && state.hostname.isBlank()) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "ホスト名を入力してください")
                    }
                    return@launch
                }
                val port = state.port.toIntOrNull()
                if (state.protocol != "local" && (port == null || port !in 1..65535)) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "ポートは1〜65535の数値で入力してください")
                    }
                    return@launch
                }

                val existingHost = if (hostId != -1L) {
                    repository.findHostById(hostId)
                } else {
                    null
                }

                // In quick connect mode, use the quickConnect string as the nickname
                val rawNickname = if (!useExpandedMode && state.quickConnect.isNotBlank()) {
                    state.nickname.ifBlank { state.quickConnect }
                } else {
                    state.nickname
                }

                // Only SSH hosts can have a jump host
                val isSsh = state.protocol == "ssh"
                val requestedJumpHostId = if (isSsh) state.jumpHostId else null
                val sshHostsById = if (requestedJumpHostId != null && requestedJumpHostId > 0L) {
                    repository.getSshHosts().associateBy { it.id }
                } else {
                    emptyMap()
                }
                if (requestedJumpHostId != null &&
                    requestedJumpHostId > 0L &&
                    wouldCreateJumpHostCycle(
                        currentHostId = existingHost?.id ?: hostId,
                        selectedJumpHostId = requestedJumpHostId,
                        hostsById = sshHostsById
                    )
                ) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "Jump Host が循環参照になるため保存できません")
                    }
                    return@launch
                }
                val jumpHostId = requestedJumpHostId
                val isLocal = state.protocol == "local"
                val nickname = if (isLocal) {
                    parseLocalQuickConnect(rawNickname) ?: rawNickname.ifBlank { "Local" }
                } else {
                    rawNickname
                }

                val host = Host(
                    id = existingHost?.id ?: 0L,
                    nickname = nickname,
                    protocol = state.protocol,
                    username = if (isSsh) state.username else "",
                    hostname = if (isLocal) "" else state.hostname,
                    port = if (isLocal) 0 else requireNotNull(port),
                    color = state.color.takeIf { it != "gray" },
                    pubkeyId = if (isSsh) state.pubkeyId else HostConstants.PUBKEYID_NEVER,
                    profileId = state.profileId ?: DEFAULT_PROFILE_ID,
                    useAuthAgent = if (isSsh) state.useAuthAgent.takeIf { it != HostConstants.AUTHAGENT_NO } else null,
                    compression = if (isSsh) state.compression else false,
                    wantSession = state.wantSession,
                    stayConnected = state.stayConnected,
                    quickDisconnect = state.quickDisconnect,
                    postLogin = state.postLogin.ifBlank { null },
                    lastConnect = existingHost?.lastConnect ?: System.currentTimeMillis(),
                    hostKeyAlgo = existingHost?.hostKeyAlgo,
                    useKeys = existingHost?.useKeys ?: true,
                    scrollbackLines = existingHost?.scrollbackLines ?: 140,
                    useCtrlAltAsMetaKey = existingHost?.useCtrlAltAsMetaKey ?: false,
                    jumpHostId = jumpHostId,
                    ipVersion = state.ipVersion
                )

                val savedHost = repository.saveHost(host)

                // Handle password storage (only for SSH protocol)
                if (isSsh) {
                    if (state.password.isNotEmpty()) {
                        // 変更理由: Keystore保存に失敗した状態を成功扱いにしない。
                        val passwordSaved = securePasswordStorage.savePassword(savedHost.id, state.password)
                        if (!passwordSaved) {
                            rollbackHostSaveAfterPasswordFailure(savedHost, existingHost)
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    saveSucceeded = false,
                                    error = "パスワードを安全に保存できませんでした。端末の認証情報ストレージを確認してください"
                                )
                            }
                            return@launch
                        }
                    } else if (!state.hasExistingPassword) {
                        // No password entered and no existing password - ensure it's cleared
                        securePasswordStorage.deletePassword(savedHost.id)
                    }
                    // If password is empty but hasExistingPassword is true, keep existing
                } else if (existingHost?.protocol == "ssh") {
                    // 変更理由: SSHからLocal/Telnetへ変更したホストにSSHパスワードを残さない。
                    securePasswordStorage.deletePassword(savedHost.id)
                }
                _uiState.update {
                    it.copy(isSaving = false, saveSucceeded = true)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSucceeded = false,
                        error = e.message ?: "ホストを保存できませんでした"
                    )
                }
            }
        }
    }

    private suspend fun rollbackHostSaveAfterPasswordFailure(savedHost: Host, previousHost: Host?) {
        runCatching {
            if (previousHost == null) {
                repository.deleteHost(savedHost)
            } else {
                repository.saveHost(previousHost)
            }
        }.onFailure {
            Timber.w(it, "Failed to rollback host after password storage failure")
        }
    }

    fun consumeSaveSucceeded() {
        _uiState.update { it.copy(saveSucceeded = false) }
    }

    private fun filterValidJumpHostCandidates(hosts: List<Host>): List<Host> {
        val hostsById = hosts.associateBy { it.id }
        return hosts.filter { candidate ->
            candidate.id != hostId &&
                !wouldCreateJumpHostCycle(
                    currentHostId = hostId,
                    selectedJumpHostId = candidate.id,
                    hostsById = hostsById
                )
        }
    }

    private fun wouldCreateJumpHostCycle(
        currentHostId: Long,
        selectedJumpHostId: Long,
        hostsById: Map<Long, Host>
    ): Boolean {
        val visited = mutableSetOf<Long>()
        var nextId: Long? = selectedJumpHostId
        while (nextId != null && nextId > 0L) {
            if (nextId == currentHostId) return true
            if (!visited.add(nextId)) return true
            nextId = hostsById[nextId]?.jumpHostId
        }
        return false
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
    }
}
