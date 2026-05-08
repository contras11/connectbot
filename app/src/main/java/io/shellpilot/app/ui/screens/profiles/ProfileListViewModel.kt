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

package io.shellpilot.app.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.entity.Profile
import javax.inject.Inject

data class ProfileListUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val showDeleteDialog: Profile? = null,
    val createError: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileListUiState())
    val uiState: StateFlow<ProfileListUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.observeAll().collect { profiles ->
                _uiState.update {
                    it.copy(profiles = profiles, isLoading = false)
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, createError = null) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, createError = null) }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiState.update { it.copy(createError = "プロファイル名を入力してください") }
                return@launch
            }

            if (profileRepository.nameExists(name)) {
                _uiState.update { it.copy(createError = "同じ名前のプロファイルがすでにあります") }
                return@launch
            }

            profileRepository.create(name)
            _uiState.update { it.copy(showCreateDialog = false, createError = null) }
        }
    }

    fun showDeleteDialog(profile: Profile) {
        _uiState.update { it.copy(showDeleteDialog = profile) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            if (profile.id == 1L) {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = null,
                        error = "標準プロファイルは削除できません"
                    )
                }
                return@launch
            }

            val usageCount = profileRepository.getHostsUsingProfile(profile.id)
            if (usageCount > 0) {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = null,
                        error = "このプロファイルは $usageCount 件のホストで使用中のため削除できません"
                    )
                }
                return@launch
            }

            profileRepository.delete(profile.id)
            _uiState.update { it.copy(showDeleteDialog = null) }
        }
    }

    fun duplicateProfile(profile: Profile) {
        viewModelScope.launch {
            val baseName = if (profile.name == "Default") "標準" else profile.name
            var newName = "$baseName のコピー"
            var counter = 1
            while (profileRepository.nameExists(newName)) {
                counter++
                newName = "$baseName のコピー $counter"
            }
            profileRepository.duplicate(profile.id, newName)
        }
    }
}
