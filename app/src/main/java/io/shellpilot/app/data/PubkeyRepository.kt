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

import androidx.room.withTransaction
import io.shellpilot.app.data.dao.PubkeyDao
import io.shellpilot.app.data.entity.KeyStorageType
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.util.HostConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing SSH public/private key pairs.
 * Handles pubkey CRUD operations.
 *
 * @param pubkeyDao The DAO for accessing pubkey data
 */
@Singleton
class PubkeyRepository @Inject constructor(
    private val pubkeyDao: PubkeyDao
) {
    @Inject
    lateinit var database: ShellPilotDatabase

    // ============================================================================
    // Pubkey Operations
    // ============================================================================

    /**
     * Observe all pubkeys reactively.
     *
     * @return Flow of pubkey list that updates automatically
     */
    fun observeAll(): Flow<List<Pubkey>> = pubkeyDao.observeAll().map { pubkeys ->
        pubkeys.map(CoreDataSanitizer::sanitizePubkey)
    }

    /**
     * Observe pubkeys by storage type reactively.
     *
     * @param type The storage type (EXPORTABLE or ANDROID_KEYSTORE)
     * @return Flow of pubkey list filtered by type
     */
    fun observeByStorageType(type: KeyStorageType): Flow<List<Pubkey>> = pubkeyDao.observeByStorageType(type).map { pubkeys ->
        pubkeys.map(CoreDataSanitizer::sanitizePubkey)
    }

    /**
     * Observe a specific pubkey reactively.
     *
     * @param pubkeyId The pubkey ID
     * @return Flow of pubkey that updates automatically
     */
    fun observePubkey(pubkeyId: Long): Flow<Pubkey?> = pubkeyDao.observeById(pubkeyId).map {
        it?.let(CoreDataSanitizer::sanitizePubkey)
    }

    /**
     * Get all pubkeys.
     *
     * @return List of all pubkeys
     */
    suspend fun getAll(): List<Pubkey> = pubkeyDao.getAll().map(CoreDataSanitizer::sanitizePubkey)

    /**
     * Find a pubkey by its unique ID.
     *
     * @param pubkeyId The pubkey ID
     * @return The pubkey, or null if not found
     */
    suspend fun getById(pubkeyId: Long): Pubkey? = pubkeyDao.getById(pubkeyId)?.let(CoreDataSanitizer::sanitizePubkey)

    /**
     * Find a pubkey by its unique ID (blocking).
     * For Java interop from service layer.
     *
     * @param pubkeyId The pubkey ID
     * @return The pubkey, or null if not found
     */
    fun getByIdBlocking(pubkeyId: Long): Pubkey? = runBlocking {
        getById(pubkeyId)
    }

    /**
     * Find a pubkey by nickname.
     *
     * @param nickname The pubkey nickname
     * @return The pubkey, or null if not found
     */
    suspend fun getByNickname(nickname: String): Pubkey? = pubkeyDao.getByNickname(nickname)?.let(CoreDataSanitizer::sanitizePubkey)

    /**
     * Get all pubkeys that allow backup.
     *
     * @return List of backupable pubkeys
     */
    suspend fun getBackupable(): List<Pubkey> = pubkeyDao.getBackupable().map(CoreDataSanitizer::sanitizePubkey)

    /**
     * Get all exportable pubkeys (not stored in Android Keystore).
     *
     * @return List of exportable pubkeys
     */
    suspend fun getExportable(): List<Pubkey> = pubkeyDao.getExportable().map(CoreDataSanitizer::sanitizePubkey)

    /**
     * Get all pubkeys marked for automatic unlocking at startup.
     *
     * @return List of startup pubkeys
     */
    suspend fun getStartupKeys(): List<Pubkey> = pubkeyDao.getStartupKeys()
        .map(CoreDataSanitizer::sanitizePubkey)
        .filter { it.startup }

    /**
     * Get all pubkeys marked for automatic unlocking at startup (blocking).
     * For Java interop from service layer.
     *
     * @return List of startup pubkeys
     */
    fun getStartupKeysBlocking(): List<Pubkey> = runBlocking {
        getStartupKeys()
    }

    /**
     * Save a pubkey (insert or update).
     *
     * @param pubkey The pubkey to save
     * @return The saved pubkey with updated ID
     */
    suspend fun save(pubkey: Pubkey): Pubkey {
        val sanitized = CoreDataSanitizer.sanitizePubkey(pubkey)
        return if (sanitized.id == 0L) {
            // New pubkey - insert
            val newId = pubkeyDao.insert(sanitized)
            sanitized.copy(id = newId)
        } else {
            // Existing pubkey - update
            pubkeyDao.update(sanitized)
            sanitized
        }
    }

    /**
     * Delete a pubkey.
     *
     * @param pubkey The pubkey to delete
     */
    suspend fun delete(pubkey: Pubkey) {
        deleteById(pubkey.id, pubkey)
    }

    /**
     * Delete a pubkey by ID.
     */
    suspend fun deleteById(pubkeyId: Long): Boolean = deleteById(pubkeyId, pubkeyDao.getById(pubkeyId))

    private suspend fun deleteById(pubkeyId: Long, pubkey: Pubkey?): Boolean {
        if (pubkey == null) return false

        if (this::database.isInitialized) {
            database.withTransaction {
                // 変更理由: 鍵削除後にhosts.pubkeyIdが孤立すると接続時に誤った鍵選択になる。
                database.hostDao().getAll()
                    .filter { it.pubkeyId == pubkeyId }
                    .forEach { host ->
                        database.hostDao().update(host.copy(pubkeyId = HostConstants.PUBKEYID_NEVER))
                    }
                pubkeyDao.delete(pubkey)
            }
        } else {
            pubkeyDao.delete(pubkey)
        }
        return true
    }

    /**
     * Update backup permission for a pubkey.
     *
     * @param pubkeyId The pubkey ID
     * @param allowBackup Whether to allow backup
     */
    suspend fun updateBackupPermission(pubkeyId: Long, allowBackup: Boolean): Boolean {
        val pubkey = pubkeyDao.getById(pubkeyId) ?: return false
        val sanitized = CoreDataSanitizer.sanitizePubkey(pubkey.copy(allowBackup = allowBackup))
        pubkeyDao.update(sanitized)
        return sanitized.allowBackup == allowBackup
    }
}
