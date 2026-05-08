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

package io.shellpilot.app.service

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import io.shellpilot.app.data.ColorSchemeRepository
import io.shellpilot.app.data.CoreDataSanitizer
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.ShellPilotDatabase
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KeyStorageType
import io.shellpilot.app.util.HostConstants
import timber.log.Timber
import java.io.File

/**
 * Handles filtering logic for backup operations.
 *
 * This class is separated from BackupAgent to allow for unit testing
 * of the filtering logic without requiring system-level backup permissions.
 */
class BackupFilter(
    private val context: Context,
    private val hostRepository: HostRepository,
    private val profileRepository: ProfileRepository,
    private val colorSchemeRepository: ColorSchemeRepository,
    private val pubkeyRepository: PubkeyRepository
) {
    /**
     * Build a filtered database containing only backupable data.
     *
     * Opens both the main database and a new temporary database,
     * then copies all data except non-backupable pubkeys.
     *
     * @param tempDbFile The temporary database file to create
     */
    suspend fun buildFilteredDatabase(tempDbFile: File, backupKeys: Boolean) {
        cleanupTempDatabase(tempDbFile)

        // Create a new temporary database
        val tempDb = Room.databaseBuilder(
            context,
            ShellPilotDatabase::class.java,
            tempDbFile.absolutePath
        )
            .addMigrations(
                ShellPilotDatabase.MIGRATION_4_5,
                ShellPilotDatabase.MIGRATION_7_8,
                ShellPilotDatabase.MIGRATION_8_9,
                ShellPilotDatabase.MIGRATION_9_10,
                ShellPilotDatabase.MIGRATION_10_11
            )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries() // Backup runs on backup thread
            .build()

        try {
            // Get all data from main database
            val allProfiles = profileRepository.getAll()
            val allHosts = hostRepository.getHosts()
            val allColorSchemes = colorSchemeRepository.getAllSchemes()
            val customColorSchemeIds = allColorSchemes
                .filter { !it.isBuiltIn && it.id > 0L }
                .map { it.id }
                .toSet()
            val backupProfiles = normalizeProfiles(allProfiles, customColorSchemeIds)

            val backupablePubkeys = if (backupKeys) {
                // Filter pubkeys - only keep backupable ones
                val allPubkeys = pubkeyRepository.getAll()
                filterBackupablePubkeys(allPubkeys)
            } else {
                emptyList()
            }

            Timber.d("Backing up ${backupProfiles.size} profiles, ${allHosts.size} hosts, ${backupablePubkeys.size} pubkeys, ${allColorSchemes.size} color schemes")

            // Insert all backupable data into temp database
            backupProfiles.forEach { profile ->
                tempDb.profileDao().insert(profile)
            }

            val backupablePubkeyIds = backupablePubkeys.map { it.id }.toSet()
            val profileIds = backupProfiles.map { it.id }.toSet()
            val hostIds = allHosts.map { it.id }.toSet()
            val hostById = allHosts.associateBy { it.id }
            val backupHosts = allHosts.map { host ->
                sanitizeHostReference(host, backupablePubkeyIds, profileIds, hostIds, hostById)
            }

            backupHosts.forEach { host ->
                // 変更理由: self FKを追加したため、全ホスト挿入後にjump_host_idを戻す。
                tempDb.hostDao().insert(host.copy(jumpHostId = null))
            }
            backupHosts.filter { it.jumpHostId != null }.forEach { host ->
                tempDb.hostDao().update(host)
            }

            backupHosts.forEach { host ->
                // Also backup port forwards and known hosts for this host
                val portForwards = hostRepository.getPortForwardsForHost(host.id)
                portForwards.mapNotNull { sanitizePortForward(it, host) }.forEach { tempDb.portForwardDao().insert(it) }

                val knownHosts = hostRepository.getKnownHostsForHost(host.id)
                knownHosts.mapNotNull { CoreDataSanitizer.sanitizeKnownHost(it, host) }
                    .forEach { tempDb.knownHostDao().insert(it) }
            }

            backupablePubkeys.forEach { pubkey ->
                tempDb.pubkeyDao().insert(pubkey)
            }

            allColorSchemes.forEach { scheme ->
                val normalizedScheme = sanitizeColorSchemeForBackup(scheme) ?: return@forEach
                tempDb.colorSchemeDao().insert(normalizedScheme)
                // Also backup color palette for this scheme
                val colors = colorSchemeRepository.getSchemeColors(normalizedScheme.id)
                colors.forEachIndexed { index, color ->
                    tempDb.colorSchemeDao().insertColor(
                        io.shellpilot.app.data.entity.ColorPalette(
                            schemeId = normalizedScheme.id,
                            colorIndex = index,
                            color = color
                        )
                    )
                }
            }
        } finally {
            tempDb.close()
        }
    }

    /**
     * Filter a list of pubkeys to only include backupable ones.
     *
     * A pubkey is backupable if:
     * - allowBackup = true
     * - storageType != ANDROID_KEYSTORE
     *
     * This method is public and suspend to allow for easy unit testing.
     *
     * @param pubkeys The list of pubkeys to filter
     * @return The list of backupable pubkeys
     */
    fun filterBackupablePubkeys(pubkeys: List<io.shellpilot.app.data.entity.Pubkey>):
            List<io.shellpilot.app.data.entity.Pubkey> {
        return pubkeys.filter { pubkey ->
            val sanitized = CoreDataSanitizer.sanitizePubkey(pubkey)
            val isBackupable = sanitized.allowBackup && sanitized.storageType != KeyStorageType.ANDROID_KEYSTORE

            if (!isBackupable) {
                Timber.d("Filtering out pubkey: ${pubkey.nickname} " +
                        "(allowBackup=${pubkey.allowBackup}, storageType=${pubkey.storageType})")
            }

            isBackupable
        }.map { pubkey ->
            val sanitized = CoreDataSanitizer.sanitizePubkey(pubkey)
            // 変更理由: exportable鍵にKeystore aliasが残っていても復元先の別aliasへ誤接続しないよう落とす。
            sanitized.copy(
                storageType = KeyStorageType.EXPORTABLE,
                keystoreAlias = null,
                startup = sanitized.startup && sanitized.privateKey != null
            )
        }
    }

    private fun normalizeProfiles(
        profiles: List<io.shellpilot.app.data.entity.Profile>,
        customColorSchemeIds: Set<Long>
    ): List<io.shellpilot.app.data.entity.Profile> {
        val sanitizedProfiles = profiles
            .filter { it.id > 0L }
            .map { CoreDataSanitizer.sanitizeProfile(it, customColorSchemeIds) }

        val withDefault = if (sanitizedProfiles.any { it.id == DEFAULT_PROFILE_ID }) {
            sanitizedProfiles
        } else {
            // 変更理由: hosts.profile_id はSET DEFAULT=1なので、temp DBにも必ずDefault profileを作る。
            listOf(io.shellpilot.app.data.entity.Profile(id = DEFAULT_PROFILE_ID, name = DEFAULT_PROFILE_NAME)) + sanitizedProfiles
        }

        val seenNames = mutableSetOf<String>()
        return withDefault.map { profile ->
            var name = profile.name.ifBlank { "Profile ${profile.id}" }
            val baseName = name
            var suffix = 1
            while (!seenNames.add(name.lowercase())) {
                name = "$baseName ($suffix)"
                suffix++
            }
            val renamed = if (name == profile.name) profile else profile.copy(name = name)
            CoreDataSanitizer.sanitizeProfile(renamed, customColorSchemeIds)
        }
    }

    private fun sanitizeColorSchemeForBackup(
        scheme: io.shellpilot.app.data.entity.ColorScheme
    ): io.shellpilot.app.data.entity.ColorScheme? {
        return CoreDataSanitizer.sanitizeColorScheme(scheme)
    }

    private fun sanitizeHostReference(
        host: Host,
        backupablePubkeyIds: Set<Long>,
        profileIds: Set<Long>,
        hostIds: Set<Long>,
        hostById: Map<Long, Host>
    ): Host {
        val baseHost = CoreDataSanitizer.sanitizeHost(
            host = host,
            profileExists = { it in profileIds },
            pubkeyExists = { it in backupablePubkeyIds },
            jumpHostById = { hostById[it] }
        )
        val sanitizedPubkeyId = when {
            host.pubkeyId <= 0L -> host.pubkeyId
            host.pubkeyId in backupablePubkeyIds -> host.pubkeyId
            else -> HostConstants.PUBKEYID_NEVER
        }
        val sanitizedProfileId = baseHost.profileId.takeIf { it in profileIds } ?: DEFAULT_PROFILE_ID
        val sanitizedJumpHostId = sanitizeJumpHostId(baseHost, hostIds, hostById)

        // 変更理由: バックアップから落とした鍵や壊れた踏み台参照を復元先へ持ち込まない。
        return baseHost.copy(
            profileId = sanitizedProfileId,
            pubkeyId = sanitizedPubkeyId,
            jumpHostId = sanitizedJumpHostId
        )
    }

    private fun sanitizeJumpHostId(
        host: Host,
        hostIds: Set<Long>,
        hostById: Map<Long, Host>
    ): Long? {
        val jumpHostId = host.jumpHostId ?: return null
        val jumpHost = hostById[jumpHostId]
        if (jumpHostId !in hostIds || jumpHostId == host.id || jumpHost?.protocol != "ssh") {
            return null
        }
        return if (hasJumpCycle(host.id, jumpHostId, hostById)) null else jumpHostId
    }

    private fun hasJumpCycle(rootId: Long, firstJumpHostId: Long, hostById: Map<Long, Host>): Boolean {
        val visited = mutableSetOf(rootId)
        var nextId: Long? = firstJumpHostId
        while (nextId != null && nextId > 0L) {
            if (!visited.add(nextId)) {
                return true
            }
            val nextHost = hostById[nextId] ?: return true
            if (nextHost.protocol != "ssh") {
                return true
            }
            nextId = nextHost.jumpHostId
        }
        return false
    }

    private fun sanitizePortForward(
        portForward: io.shellpilot.app.data.entity.PortForward,
        host: Host
    ): io.shellpilot.app.data.entity.PortForward? {
        // 変更理由: バックアップ用DBにも実行時が扱える転送設定だけを書き出す。
        return CoreDataSanitizer.sanitizePortForward(portForward, host)
    }

    /**
     * Clean up temporary database files.
     *
     * @param tempDbFile The temporary database file
     */
    fun cleanupTempDatabase(tempDbFile: File) {
        if (tempDbFile.exists()) {
            tempDbFile.delete()
        }
        // Also delete temp database files (WAL, SHM)
        File(tempDbFile.path + "-wal").delete()
        File(tempDbFile.path + "-shm").delete()
        File(tempDbFile.path + "-journal").delete()
        Timber.d("Deleted temporary database files")
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
        const val DEFAULT_PROFILE_NAME = "Default"
    }
}
