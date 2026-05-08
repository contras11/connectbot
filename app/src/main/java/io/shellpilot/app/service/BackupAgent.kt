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

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.shellpilot.app.data.ColorSchemeRepository
import io.shellpilot.app.data.HostRepository
import io.shellpilot.app.data.ProfileRepository
import io.shellpilot.app.data.PubkeyRepository
import io.shellpilot.app.data.ShellPilotDatabase
import io.shellpilot.app.di.CoroutineDispatchers
import io.shellpilot.app.util.PreferenceConstants
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * ShellPilot's backup agent with selective pubkey filtering.
 *
 * This agent backs up:
 * - Shared preferences
 * - The Room database (connectbot.db)
 *
 * However, before backing up the database, it filters out:
 * - Pubkeys with allowBackup = false
 * - Pubkeys with storageType = ANDROID_KEYSTORE
 *
 * This ensures sensitive SSH keys are not included in cloud backups
 * if the user has disabled backup for those keys.
 *
 * Implementation: We create a temporary database, insert the backupable
 * data into it, back it up as the original db name, then delete the
 * temporary database. Upon restore, the original db name is used
 * automatically.
 */
class BackupAgent : BackupAgentHelper() {
    companion object {
        private const val TAG = "CB.BackupAgent"
        private const val DATABASE_NAME = "connectbot.db"
        private const val DATABASE_HELPER_KEY = "shellpilot_database"
        private const val TEMP_DATABASE_NAME = "connectbot_backup_temp.db"
    }

    override fun onCreate() {
        Timber.d("onCreate called")

        // 変更理由: DBもBackupHelperに載せ、バックアップと復元を同じ経路で扱う。
        val prefsHelper = SharedPreferencesBackupHelper(
            this,
            packageName + "_preferences"
        )
        addHelper(PreferenceConstants.BACKUP_PREF_KEY, prefsHelper)
        addHelper(DATABASE_HELPER_KEY, DatabaseBackupHelper())
    }

    private inner class DatabaseBackupHelper : BackupHelper {
        override fun performBackup(
            oldState: ParcelFileDescriptor?,
            data: BackupDataOutput,
            newState: ParcelFileDescriptor?
        ) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@BackupAgent)
            val backupKeys = prefs.getBoolean(
                PreferenceConstants.BACKUP_KEYS,
                PreferenceConstants.BACKUP_KEYS_DEFAULT
            )

            try {
                backupDatabaseWithFiltering(data, backupKeys)
            } catch (e: Exception) {
                Timber.e(e, "Failed to backup database")
            }
        }

        override fun restoreEntity(data: BackupDataInputStream) {
            if (data.key != DATABASE_NAME) {
                Timber.w("Unexpected backup entity for database helper: ${data.key}")
                return
            }

            try {
                restoreDatabase(data)
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore database")
            }
        }

        override fun writeNewStateDescription(newState: ParcelFileDescriptor?) {
            // DB差分状態はRoomファイル全体のバックアップで管理するため、個別状態は保持しない。
        }
    }

    /**
     * Create a temporary database with only backupable data, back it up, then delete it.
     */
    private fun backupDatabaseWithFiltering(data: BackupDataOutput, backupKeys: Boolean) {
        val dbFile = getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) {
            Timber.w("Database does not exist yet, skipping backup")
            return
        }

        val tempDbFile = getDatabasePath(TEMP_DATABASE_NAME)

        // 変更理由: 通常のアプリ起動経路を使わず、バックアップ専用に最小限のRepositoryを組み立てる。
        val database = Room.databaseBuilder(
            applicationContext,
            ShellPilotDatabase::class.java,
            DATABASE_NAME
        ).build()
        val dispatchers = CoroutineDispatchers(default = Dispatchers.Default, io = Dispatchers.IO, main = Dispatchers.Main)
        val securePasswordStorage = io.shellpilot.app.util.SecurePasswordStorage(applicationContext)
        val hostRepository = HostRepository(applicationContext, database, database.hostDao(), database.portForwardDao(), database.knownHostDao(), securePasswordStorage)
        val profileRepository = ProfileRepository(database.profileDao(), dispatchers)
        val colorSchemeRepository = ColorSchemeRepository(database.colorSchemeDao(), dispatchers = dispatchers)
        val pubkeyRepository = PubkeyRepository(database.pubkeyDao())

        val filter = BackupFilter(applicationContext, hostRepository, profileRepository, colorSchemeRepository, pubkeyRepository)
        try {
            // 1. バックアップ対象だけを含む一時DBを作る。
            Timber.d("Building temporary database with backupable data")
            kotlinx.coroutines.runBlocking {
                filter.buildFilteredDatabase(tempDbFile, backupKeys)
            }

            // 2. 一時DBを本来のDB名としてバックアップへ書き出す。
            Timber.d("Backing up filtered database")
            backupFile(tempDbFile, DATABASE_NAME, data)
        } catch (e: Exception) {
            Timber.e(e, "Error during database backup with filtering")
            throw e
        } finally {
            // 3. 一時DBは復元対象に混ざらないよう必ず削除する。
            filter.cleanupTempDatabase(tempDbFile)
            database.close()
        }
    }

    /**
     * ファイル全体をBackupDataOutputへ書き出す。
     */
    private fun backupFile(file: File, key: String, data: BackupDataOutput) {
        if (!file.exists()) {
            Timber.w("File does not exist: ${file.path}")
            return
        }

        val fileSize = file.length()
        if (fileSize <= 0L || fileSize > Int.MAX_VALUE) {
            Timber.w("Database file has unsupported size for backup: $fileSize")
            return
        }

        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0
            data.writeEntityHeader(key, fileSize.toInt())
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                data.writeEntityData(buffer, bytesRead)
                totalBytes += bytesRead
            }
            Timber.d("Backed up $key ($totalBytes bytes)")
        }
    }

    /**
     * [backupFile] が書いたRoom DBエンティティを復元する。
     */
    private fun restoreDatabase(data: BackupDataInputStream) {
        val dbFile = getDatabasePath(DATABASE_NAME)
        val tempFile = File(dbFile.parentFile, "$DATABASE_NAME.restore")
        dbFile.parentFile?.mkdirs()

        FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = data.size()
            while (remaining > 0) {
                val read = data.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.fd.sync()
        }

        deleteDatabaseSidecars(dbFile)
        if (dbFile.exists() && !dbFile.delete()) {
            throw IllegalStateException("Existing database could not be deleted before restore: ${dbFile.path}")
        }
        if (!tempFile.renameTo(dbFile)) {
            tempFile.delete()
            throw IllegalStateException("Restored database could not be moved into place: ${dbFile.path}")
        }
        Timber.d("Restored database from backup (${dbFile.length()} bytes)")
    }

    private fun deleteDatabaseSidecars(dbFile: File) {
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm")
        ).forEach { file ->
            if (file != dbFile && file.exists() && !file.delete()) {
                Timber.w("Could not delete database sidecar before restore: ${file.path}")
            }
        }
    }
}
