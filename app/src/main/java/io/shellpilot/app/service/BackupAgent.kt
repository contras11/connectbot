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
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
            val requestedBackupKeys = prefs.getBoolean(
                PreferenceConstants.BACKUP_KEYS,
                PreferenceConstants.BACKUP_KEYS_DEFAULT
            )
            val backupKeys = requestedBackupKeys && canBackupKeysWithTransport(data)
            if (requestedBackupKeys && !backupKeys) {
                // 変更理由: 秘密鍵を暗号化なしのクラウドバックアップへ載せない。
                Timber.w("Skipping private-key backup because the active transport is not encrypted or device-to-device")
            }

            try {
                backupDatabaseWithFiltering(data, backupKeys)
            } catch (e: Exception) {
                Timber.e(e, "Failed to backup database")
                throw e
            }
        }

        override fun restoreEntity(data: BackupDataInputStream) {
            if (data.key != DATABASE_NAME) {
                throw IllegalArgumentException("Unexpected backup entity for database helper: ${data.key}")
            }

            try {
                restoreDatabase(data)
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore database")
                throw e
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
            throw IllegalStateException("Database does not exist for backup: ${dbFile.path}")
        }

        val tempDbFile = File(noBackupFilesDir, TEMP_DATABASE_NAME)

        // 変更理由: 復元直後の旧DBでも通常経路と同じmigration/callbackで開く。
        val database = buildMainDatabase()
        val dispatchers = CoroutineDispatchers(default = Dispatchers.Default, io = Dispatchers.IO, main = Dispatchers.Main)
        val securePasswordStorage = io.shellpilot.app.util.SecurePasswordStorage(applicationContext)
        val hostRepository = HostRepository(applicationContext, database, database.hostDao(), database.portForwardDao(), database.knownHostDao(), securePasswordStorage)
        val profileRepository = ProfileRepository(database, database.profileDao(), dispatchers)
        val colorSchemeRepository = ColorSchemeRepository(database.colorSchemeDao(), dispatchers = dispatchers)
        val pubkeyRepository = PubkeyRepository(database.pubkeyDao())

        val filter = BackupFilter(applicationContext, hostRepository, profileRepository, colorSchemeRepository, pubkeyRepository)
        try {
            // 1. バックアップ対象だけを含む一時DBを作る。
            Timber.d("Building temporary database with backupable data")
            filter.cleanupTempDatabase(tempDbFile)
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

    private fun buildMainDatabase(): ShellPilotDatabase = buildDatabase(DATABASE_NAME)

    private fun canBackupKeysWithTransport(data: BackupDataOutput): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val flags = data.transportFlags
        return flags and android.app.backup.BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0 ||
            flags and android.app.backup.BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
    }

    private fun buildDatabase(databaseName: String): ShellPilotDatabase = Room.databaseBuilder(
        applicationContext,
        ShellPilotDatabase::class.java,
        databaseName
    )
        .addMigrations(
            ShellPilotDatabase.MIGRATION_4_5,
            ShellPilotDatabase.MIGRATION_7_8,
            ShellPilotDatabase.MIGRATION_8_9,
            ShellPilotDatabase.MIGRATION_9_10,
            ShellPilotDatabase.MIGRATION_10_11
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 変更理由: DatabaseModuleと同じDefault profile不変条件をBackupAgent経由でも守る。
                ShellPilotDatabase.ensureDefaultProfileInvariant(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // 変更理由: 復元DBでも通常起動時と同じ不変条件を検証する。
                ShellPilotDatabase.normalizeRuntimeInvariants(db)
            }
        })
        .build()

    /**
     * ファイル全体をBackupDataOutputへ書き出す。
     */
    private fun backupFile(file: File, key: String, data: BackupDataOutput) {
        if (!file.exists()) {
            throw IllegalStateException("File does not exist for backup: ${file.path}")
        }

        val fileSize = file.length()
        if (fileSize <= 0L || fileSize > Int.MAX_VALUE) {
            throw IllegalStateException("Database file has unsupported size for backup: $fileSize")
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
        val entitySize = data.size()
        if (entitySize <= 0) {
            throw IllegalStateException("Database backup entity is empty: $entitySize bytes")
        }

        val dbFile = getDatabasePath(DATABASE_NAME)
        val tempFile = File(noBackupFilesDir, "$DATABASE_NAME.restore")
        val backupFile = File(noBackupFilesDir, "$DATABASE_NAME.pre-restore")
        dbFile.parentFile?.mkdirs()
        noBackupFilesDir.mkdirs()
        deleteDatabaseWithSidecars(tempFile, warnOnly = true)
        deleteDatabaseWithSidecars(backupFile, warnOnly = true)

        FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = entitySize
            while (remaining > 0) {
                val read = data.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) {
                    throw IllegalStateException("Database backup entity ended early; $remaining bytes missing")
                }
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.fd.sync()
        }

        validateDatabaseFile(tempFile, "restored temp database")
        validateRoomDatabaseFile(tempFile, "restored temp database")

        try {
            moveExistingDatabaseAside(dbFile, backupFile)
            if (!tempFile.renameTo(dbFile)) {
                throw IllegalStateException("Restored database could not be moved into place: ${dbFile.path}")
            }
            validateDatabaseFile(dbFile, "restored database")
            validateRestoredRoomDatabase()
            deleteDatabaseWithSidecars(backupFile, warnOnly = true)
            Timber.d("Restored database from backup (${dbFile.length()} bytes)")
        } catch (e: Exception) {
            Timber.e(e, "Restore failed; attempting rollback")
            rollbackDatabase(dbFile, backupFile)
            throw e
        } finally {
            deleteDatabaseWithSidecars(tempFile, warnOnly = true)
        }
    }

    private fun validateDatabaseFile(dbFile: File, label: String) {
        if (!dbFile.exists() || dbFile.length() <= 0L) {
            throw IllegalStateException("$label is missing or empty: ${dbFile.path}")
        }

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val integrity = db.singlePragmaValue("PRAGMA integrity_check")
            if (integrity != "ok") {
                throw IllegalStateException("$label failed integrity_check: $integrity")
            }

            val quick = db.singlePragmaValue("PRAGMA quick_check")
            if (quick != "ok") {
                throw IllegalStateException("$label failed quick_check: $quick")
            }
        }
    }

    private fun SQLiteDatabase.singlePragmaValue(sql: String): String {
        rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw IllegalStateException("No result from $sql")
            }
            return cursor.getString(0)
        }
    }

    private fun validateRestoredRoomDatabase() {
        val database = buildMainDatabase()
        try {
            // 変更理由: raw SQLiteとして正常でもRoom migration/schema validationが失敗するDBを復元完了扱いしない。
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    throw IllegalStateException("restored database failed foreign_key_check")
                }
            }
        } finally {
            database.close()
        }
    }

    private fun validateRoomDatabaseFile(dbFile: File, label: String) {
        val database = buildDatabase(dbFile.absolutePath)
        try {
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    throw IllegalStateException("$label failed foreign_key_check")
                }
            }
        } finally {
            database.close()
        }
    }

    private fun moveExistingDatabaseAside(dbFile: File, backupFile: File) {
        deleteDatabaseWithSidecars(backupFile, warnOnly = false)
        deleteDatabaseSidecars(dbFile)
        if (!dbFile.exists()) return
        if (dbFile.renameTo(backupFile)) return
        dbFile.copyTo(backupFile, overwrite = true)
        if (!dbFile.delete()) {
            backupFile.delete()
            throw IllegalStateException("Existing database could not be moved before restore: ${dbFile.path}")
        }
    }

    private fun rollbackDatabase(dbFile: File, backupFile: File) {
        deleteDatabaseWithSidecars(dbFile, warnOnly = true)
        if (backupFile.exists() && !backupFile.renameTo(dbFile)) {
            runCatching {
                backupFile.copyTo(dbFile, overwrite = true)
                backupFile.delete()
            }.onFailure {
                Timber.e(it, "Failed to roll back database restore")
            }
        }
    }

    private fun deleteDatabaseSidecars(dbFile: File) {
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            File("${dbFile.path}-journal")
        ).forEach { file ->
            if (file != dbFile && file.exists() && !file.delete()) {
                Timber.w("Could not delete database sidecar before restore: ${file.path}")
            }
        }
    }

    private fun deleteDatabaseWithSidecars(dbFile: File, warnOnly: Boolean) {
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            File("${dbFile.path}-journal")
        ).forEach { file ->
            if (file.exists() && !file.delete() && !warnOnly) {
                throw IllegalStateException("Could not delete database file: ${file.path}")
            } else if (file.exists()) {
                Timber.w("Could not delete database file: ${file.path}")
            }
        }
    }
}
