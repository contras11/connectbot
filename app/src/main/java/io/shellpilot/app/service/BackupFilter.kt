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
import io.shellpilot.app.data.ShellPilotDatabase
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
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
            tempDbFile.name
        )
            .addMigrations(ShellPilotDatabase.MIGRATION_4_5, ShellPilotDatabase.MIGRATION_7_8)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries() // Backup runs on backup thread
            .build()

        try {
            // Get all data from main database
            val allProfiles = profileRepository.getAll()
            val allHosts = hostRepository.getHosts()
            val allColorSchemes = colorSchemeRepository.getAllSchemes()

            val backupablePubkeys = if (backupKeys) {
                // Filter pubkeys - only keep backupable ones
                val allPubkeys = pubkeyRepository.getAll()
                filterBackupablePubkeys(allPubkeys)
            } else {
                emptyList()
            }

            Timber.d("Backing up ${allProfiles.size} profiles, ${allHosts.size} hosts, ${backupablePubkeys.size} pubkeys, ${allColorSchemes.size} color schemes")

            // Insert all backupable data into temp database
            allProfiles.forEach { profile ->
                tempDb.profileDao().insert(profile)
            }

            val backupablePubkeyIds = backupablePubkeys.map { it.id }.toSet()
            val hostIds = allHosts.map { it.id }.toSet()
            val backupHosts = allHosts.map { host ->
                sanitizeHostReference(host, backupablePubkeyIds, hostIds)
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
                portForwards.forEach { tempDb.portForwardDao().insert(it) }

                val knownHosts = hostRepository.getKnownHostsForHost(host.id)
                knownHosts.forEach { tempDb.knownHostDao().insert(it) }
            }

            backupablePubkeys.forEach { pubkey ->
                tempDb.pubkeyDao().insert(pubkey)
            }

            allColorSchemes.forEach { scheme ->
                if (!scheme.isBuiltIn) {
                    tempDb.colorSchemeDao().insert(scheme)
                    // Also backup color palette for this scheme
                    val colors = colorSchemeRepository.getSchemeColors(scheme.id)
                    colors.forEachIndexed { index, color ->
                        tempDb.colorSchemeDao().insertColor(
                            io.shellpilot.app.data.entity.ColorPalette(
                                schemeId = scheme.id,
                                colorIndex = index,
                                color = color
                            )
                        )
                    }
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
            val isBackupable = pubkey.allowBackup && pubkey.storageType != KeyStorageType.ANDROID_KEYSTORE

            if (!isBackupable) {
                Timber.d("Filtering out pubkey: ${pubkey.nickname} " +
                        "(allowBackup=${pubkey.allowBackup}, storageType=${pubkey.storageType})")
            }

            isBackupable
        }
    }

    private fun sanitizeHostReference(
        host: Host,
        backupablePubkeyIds: Set<Long>,
        hostIds: Set<Long>
    ): Host {
        val sanitizedPubkeyId = when {
            host.pubkeyId <= 0L -> host.pubkeyId
            host.pubkeyId in backupablePubkeyIds -> host.pubkeyId
            else -> HostConstants.PUBKEYID_NEVER
        }
        val sanitizedJumpHostId = host.jumpHostId?.takeIf { it in hostIds && it != host.id }

        // 変更理由: バックアップから落とした鍵や壊れた踏み台参照を復元先へ持ち込まない。
        return host.copy(pubkeyId = sanitizedPubkeyId, jumpHostId = sanitizedJumpHostId)
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
}
