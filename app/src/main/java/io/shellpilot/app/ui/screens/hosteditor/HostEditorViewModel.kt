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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.util.SecurePasswordStorage
import javax.inject.Inject

data class HostEditorUiState(
    val hostId: Long = -1L,
    val quickConnect: String = "",
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
                // Exclude the current host being edited to prevent circular references
                val sshHosts = repository.getSshHosts()
                    .filter { it.id != hostId }
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
                    it.copy(isLoading = false, error = e.message ?: "Failed to load host")
                }
            }
        }
    }

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateProtocol(value: String) {
        _uiState.update { it.copy(protocol = value) }
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
        val localNickname = parseLocalQuickConnect(trimmed)
        if (localNickname != null) {
            // 変更理由: local://ReviewLocal をSSHホストとして保存しないよう、
            // クイック接続段階でLocalプロトコルへ明示的に正規化する。
            _uiState.update {
                it.copy(
                    quickConnect = value,
                    nickname = localNickname,
                    protocol = "local",
                    username = "",
                    hostname = "",
                    port = "0",
                    pubkeyId = -2L,
                    useAuthAgent = "no",
                    compression = false,
                    jumpHostId = null
                )
            }
            return
        }

        _uiState.update { it.copy(quickConnect = value, protocol = "ssh") }

        // Parse quick connect string: [user@]hostname[:port]
        val regex = Regex("^(?:([^@]+)@)?([^:]+)(?::(\\d+))?$")
        val match = regex.find(trimmed)

        if (match != null) {
            val (username, hostname, port) = match.destructured
            _uiState.update {
                it.copy(
                    nickname = trimmed,
                    username = username.ifBlank { "" },
                    hostname = hostname,
                    port = port.ifBlank { "22" }
                )
            }
        }
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
            try {
                val state = _uiState.value
                val existingHost = if (hostId != -1L) {
                    repository.findHostById(hostId)
                } else {
                    null
                }

                // In quick connect mode, use the quickConnect string as the nickname
                val rawNickname = if (!useExpandedMode && state.quickConnect.isNotBlank()) {
                    if (state.protocol == "local") state.nickname.ifBlank { "Local" } else state.quickConnect
                } else {
                    state.nickname
                }

                // Only SSH hosts can have a jump host
                val jumpHostId = if (state.protocol == "ssh") state.jumpHostId else null
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
                    username = if (isLocal) "" else state.username,
                    hostname = if (isLocal) "" else state.hostname,
                    port = if (isLocal) 0 else state.port.toIntOrNull() ?: 22,
                    color = state.color.takeIf { it != "gray" },
                    pubkeyId = if (isLocal) -2L else state.pubkeyId,
                    profileId = state.profileId,
                    useAuthAgent = if (isLocal) null else state.useAuthAgent.takeIf { it != "no" },
                    compression = if (isLocal) false else state.compression,
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
                if (state.protocol == "ssh") {
                    if (state.password.isNotEmpty()) {
                        // Save or update the password
                        securePasswordStorage.savePassword(savedHost.id, state.password)
                    } else if (!state.hasExistingPassword) {
                        // No password entered and no existing password - ensure it's cleared
                        securePasswordStorage.deletePassword(savedHost.id)
                    }
                    // If password is empty but hasExistingPassword is true, keep existing
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save host")
                }
            }
        }
    }
}
