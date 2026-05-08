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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import io.shellpilot.app.data.dao.ProfileDao
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.di.CoroutineDispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing terminal profiles.
 * Profiles bundle terminal-specific settings like color scheme, font, encoding, etc.
 *
 * @param profileDao The DAO for accessing profile data
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val database: ShellPilotDatabase,
    private val profileDao: ProfileDao,
    private val dispatchers: CoroutineDispatchers
) {
    /**
     * Observe all profiles.
     */
    fun observeAll(): Flow<List<Profile>> = profileDao.observeAll().map { profiles ->
        val colorSchemeIds = database.colorSchemeDao().getAll().map { it.id }.toSet()
        profiles.map { CoreDataSanitizer.sanitizeProfile(it, colorSchemeIds) }
    }

    /**
     * Observe a single profile by ID.
     */
    fun observeById(profileId: Long): Flow<Profile?> = profileDao.observeById(profileId).map { profile ->
        val colorSchemeIds = database.colorSchemeDao().getAll().map { it.id }.toSet()
        profile?.let { CoreDataSanitizer.sanitizeProfile(it, colorSchemeIds) }
    }

    /**
     * Get all profiles.
     */
    suspend fun getAll(): List<Profile> = withContext(dispatchers.io) {
        val colorSchemeIds = database.colorSchemeDao().getAll().map { it.id }.toSet()
        profileDao.getAll().map { CoreDataSanitizer.sanitizeProfile(it, colorSchemeIds) }
    }

    /**
     * Get a profile by ID.
     */
    suspend fun getById(profileId: Long): Profile? = withContext(dispatchers.io) {
        val colorSchemeIds = database.colorSchemeDao().getAll().map { it.id }.toSet()
        profileDao.getById(profileId)?.let { CoreDataSanitizer.sanitizeProfile(it, colorSchemeIds) }
    }

    /**
     * Get the default profile.
     */
    suspend fun getDefault(): Profile = withContext(dispatchers.io) {
        profileDao.getDefault() ?: Profile.createDefault().copy(id = DEFAULT_PROFILE_ID).also {
            // 変更理由: hosts.profile_id のDB defaultが参照するDefault profile欠損をRepository境界でも修復する。
            profileDao.insertOrUpdate(it)
        }
    }

    /**
     * Create a new profile.
     *
     * @param name The name for the new profile
     * @param basedOnProfileId Optional profile ID to copy settings from
     * @return The ID of the newly created profile
     */
    suspend fun create(
        name: String,
        basedOnProfileId: Long? = null
    ): Long = withContext(dispatchers.io) {
        val baseProfile = if (basedOnProfileId != null) {
            profileDao.getById(basedOnProfileId) ?: Profile.createDefault()
        } else {
            Profile.createDefault()
        }

        val newProfile = sanitizeProfile(
            baseProfile.copy(
                id = 0, // Auto-generate
                name = name
            )
        )
        ensureNameIsAvailable(newProfile.name, excludeProfileId = null)
        profileDao.insert(newProfile)
    }

    /**
     * Update an existing profile.
     */
    suspend fun update(profile: Profile) = withContext(dispatchers.io) {
        val sanitized = sanitizeProfile(profile)
        ensureNameIsAvailable(sanitized.name, excludeProfileId = sanitized.id)
        profileDao.update(sanitized)
    }

    /**
     * Save a profile (insert or update).
     *
     * @return The ID of the saved profile
     */
    suspend fun save(profile: Profile): Long = withContext(dispatchers.io) {
        val sanitized = sanitizeProfile(profile)
        ensureNameIsAvailable(sanitized.name, excludeProfileId = sanitized.id.takeIf { it > 0L })
        if (sanitized.id == 0L) {
            profileDao.insert(sanitized)
        } else {
            profileDao.update(sanitized)
            sanitized.id
        }
    }

    /**
     * Delete a profile by ID.
     *
     * @param profileId The profile ID to delete
     * @return true if deleted, false if not found
     */
    suspend fun delete(profileId: Long): Boolean = withContext(dispatchers.io) {
        if (profileId == DEFAULT_PROFILE_ID) {
            // 変更理由: hosts.profile_id のDB defaultが参照するDefault profileは削除不可にする。
            return@withContext false
        }
        profileDao.deleteById(profileId) > 0
    }

    /**
     * Check if a profile name already exists.
     *
     * @param name The name to check
     * @param excludeProfileId Optional profile ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    suspend fun nameExists(name: String, excludeProfileId: Long? = null): Boolean =
        withContext(dispatchers.io) {
            profileDao.nameExists(name, excludeProfileId)
        }

    /**
     * Get the count of hosts using a specific profile.
     */
    suspend fun getHostsUsingProfile(profileId: Long): Int = withContext(dispatchers.io) {
        profileDao.getHostsUsingProfile(profileId)
    }

    /**
     * Get the count of profiles using a specific color scheme.
     */
    suspend fun getProfilesUsingColorScheme(schemeId: Long): Int = withContext(dispatchers.io) {
        profileDao.getProfilesUsingColorScheme(schemeId)
    }

    /**
     * Blocking wrapper for getById - for use from non-coroutine code.
     */
    fun getByIdBlocking(profileId: Long): Profile? = runBlocking {
        getById(profileId)
    }

    /**
     * Blocking wrapper that returns the profile or the default if not found.
     * This ensures a profile is always returned.
     */
    fun getByIdOrDefaultBlocking(profileId: Long?): Profile = runBlocking {
        if (profileId == null) {
            getDefault()
        } else {
            getById(profileId) ?: getDefault()
        }
    }

    /**
     * Duplicate an existing profile.
     *
     * @param sourceProfileId The profile to duplicate
     * @param newName The name for the duplicated profile
     * @return The ID of the newly created profile
     */
    suspend fun duplicate(sourceProfileId: Long, newName: String): Long =
        withContext(dispatchers.io) {
            val source = profileDao.getById(sourceProfileId) ?: Profile.createDefault()
            val newProfile = sanitizeProfile(
                source.copy(
                    id = 0,
                    name = newName
                )
            )
            ensureNameIsAvailable(newProfile.name, excludeProfileId = null)
            profileDao.insert(newProfile)
        }

    private suspend fun sanitizeProfile(profile: Profile): Profile {
        val colorSchemeIds = database.colorSchemeDao().getAll().map { it.id }.toSet()

        // 変更理由: TerminalBridgeが直接使う値をRepository境界で安全な範囲へ揃える。
        return CoreDataSanitizer.sanitizeProfile(profile, colorSchemeIds)
    }

    private suspend fun ensureNameIsAvailable(name: String, excludeProfileId: Long?) {
        if (profileDao.nameExists(name, excludeProfileId)) {
            throw IllegalArgumentException("Profile name already exists: $name")
        }
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
    }
}
