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

package io.shellpilot.app.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.shellpilot.app.data.ShellPilotDatabase
import io.shellpilot.app.util.HostConstants
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellPilotDatabaseV8MigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration7To8NormalizesCoreReferencesAndValidatesSchema() {
        createV7Database()

        val db = runMigrationWithOpenHelper()
        assertV8DataWasNormalized(db)
        db.close()

        val roomDb = Room.databaseBuilder(context, ShellPilotDatabase::class.java, TEST_DB)
            .addMigrations(ShellPilotDatabase.MIGRATION_4_5, ShellPilotDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertThat(roomDb.hostDao().getAll()).hasSize(4)
            }
        } finally {
            roomDb.close()
        }
    }

    private fun createV7Database() {
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createV7Schema(db)
            insertV7Fixture(db)
            db.version = 7
        }
    }

    private fun runMigrationWithOpenHelper(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertThat(oldVersion).isEqualTo(7)
                        assertThat(newVersion).isEqualTo(8)
                        ShellPilotDatabase.MIGRATION_7_8.migrate(db)
                    }
                })
                .build()
        )
        return helper.writableDatabase
    }

    private fun assertV8DataWasNormalized(db: SupportSQLiteDatabase) {
        db.query("SELECT profile_id, pubkey_id, jump_host_id FROM hosts WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(1L)
            assertThat(cursor.getLong(1)).isEqualTo(HostConstants.PUBKEYID_NEVER)
            assertThat(cursor.isNull(2)).isTrue()
        }

        db.query("SELECT jump_host_id FROM hosts WHERE id IN (3, 4) ORDER BY id").use { cursor ->
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
        }

        db.query("SELECT type FROM port_forwards ORDER BY id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo(HostConstants.PORTFORWARD_DYNAMIC5)
            assertThat(cursor.moveToNext()).isFalse()
        }

        db.query("SELECT COUNT(*) FROM known_hosts").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(2)
        }

        db.query("SELECT startup, allow_backup, private_key FROM pubkeys WHERE id = 2").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
            assertThat(cursor.getInt(1)).isEqualTo(0)
            assertThat(cursor.isNull(2)).isTrue()
        }

        db.query("SELECT startup FROM pubkeys WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }

    private fun createV7Schema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hosts (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nickname` TEXT NOT NULL, `protocol` TEXT NOT NULL, `username` TEXT NOT NULL, `hostname` TEXT NOT NULL, `port` INTEGER NOT NULL, `host_key_algo` TEXT, `last_connect` INTEGER NOT NULL, `color` TEXT, `use_keys` INTEGER NOT NULL, `use_auth_agent` TEXT, `post_login` TEXT, `pubkey_id` INTEGER NOT NULL, `want_session` INTEGER NOT NULL, `compression` INTEGER NOT NULL, `stay_connected` INTEGER NOT NULL, `quick_disconnect` INTEGER NOT NULL, `scrollback_lines` INTEGER NOT NULL, `use_ctrl_alt_as_meta_key` INTEGER NOT NULL, `jump_host_id` INTEGER, `profile_id` INTEGER, `ip_version` TEXT NOT NULL DEFAULT 'IPV4_AND_IPV6')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hosts_nickname` ON `hosts` (`nickname`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_protocol_username_hostname_port` ON `hosts` (`protocol`, `username`, `hostname`, `port`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS pubkeys (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nickname` TEXT NOT NULL, `type` TEXT NOT NULL, `private_key` BLOB, `public_key` BLOB NOT NULL, `encrypted` INTEGER NOT NULL, `startup` INTEGER NOT NULL, `confirmation` INTEGER NOT NULL, `created_date` INTEGER NOT NULL, `storage_type` TEXT NOT NULL, `allow_backup` INTEGER NOT NULL, `keystore_alias` TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pubkeys_nickname` ON `pubkeys` (`nickname`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pubkeys_storage_type` ON `pubkeys` (`storage_type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pubkeys_allow_backup` ON `pubkeys` (`allow_backup`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS port_forwards (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `host_id` INTEGER NOT NULL, `nickname` TEXT NOT NULL, `type` TEXT NOT NULL, `source_port` INTEGER NOT NULL, `dest_addr` TEXT, `dest_port` INTEGER NOT NULL, FOREIGN KEY(`host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_port_forwards_host_id` ON `port_forwards` (`host_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS known_hosts (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `host_id` INTEGER, `hostname` TEXT NOT NULL, `port` INTEGER NOT NULL, `host_key_algo` TEXT NOT NULL, `host_key` BLOB NOT NULL, FOREIGN KEY(`host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id` ON `known_hosts` (`host_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id_host_key` ON `known_hosts` (`host_id`, `host_key`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS color_schemes (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `is_built_in` INTEGER NOT NULL, `description` TEXT NOT NULL, `foreground` INTEGER NOT NULL, `background` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_color_schemes_name` ON `color_schemes` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS color_palette (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scheme_id` INTEGER NOT NULL, `color_index` INTEGER NOT NULL, `color` INTEGER NOT NULL, FOREIGN KEY(`scheme_id`) REFERENCES `color_schemes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_color_palette_scheme_id` ON `color_palette` (`scheme_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_color_palette_scheme_id_color_index` ON `color_palette` (`scheme_id`, `color_index`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS profiles (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon_color` TEXT, `color_scheme_id` INTEGER NOT NULL DEFAULT -1, `font_family` TEXT, `font_size` INTEGER NOT NULL DEFAULT 10, `del_key` TEXT NOT NULL DEFAULT 'del', `encoding` TEXT NOT NULL DEFAULT 'UTF-8', `emulation` TEXT NOT NULL DEFAULT 'xterm-256color', `force_size_rows` INTEGER, `force_size_columns` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profiles_name` ON `profiles` (`name`)")
    }

    private fun insertV7Fixture(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO profiles (id, name, color_scheme_id, font_size, del_key, encoding, emulation) VALUES (1, 'Default', -1, 10, 'del', 'UTF-8', 'xterm-256color')")
        db.execSQL("INSERT INTO profiles (id, name, color_scheme_id, font_size, del_key, encoding, emulation) VALUES (2, 'Broken scheme', 999, 10, 'del', 'UTF-8', 'xterm-256color')")

        db.execSQL("INSERT INTO pubkeys (id, nickname, type, private_key, public_key, encrypted, startup, confirmation, created_date, storage_type, allow_backup, keystore_alias) VALUES (1, 'broken exportable', 'ssh-rsa', NULL, X'01', 0, 1, 0, 100, 'EXPORTABLE', 1, NULL)")
        db.execSQL("INSERT INTO pubkeys (id, nickname, type, private_key, public_key, encrypted, startup, confirmation, created_date, storage_type, allow_backup, keystore_alias) VALUES (2, 'keystore key', 'ssh-rsa', X'02', X'03', 0, 1, 0, 100, 'ANDROID_KEYSTORE', 1, 'alias')")

        insertHost(db, id = 1, nickname = "target", protocol = "ssh", profileId = 999, pubkeyId = 999, jumpHostId = 2)
        insertHost(db, id = 2, nickname = "not-ssh", protocol = "telnet", profileId = 1, pubkeyId = -1, jumpHostId = null)
        insertHost(db, id = 3, nickname = "cycle-a", protocol = "ssh", profileId = 1, pubkeyId = -1, jumpHostId = 4)
        insertHost(db, id = 4, nickname = "cycle-b", protocol = "ssh", profileId = 1, pubkeyId = -1, jumpHostId = 3)

        db.execSQL("INSERT INTO port_forwards (id, host_id, nickname, type, source_port, dest_addr, dest_port) VALUES (1, 1, 'dyn4', 'dynamic4', 1080, NULL, 0)")
        db.execSQL("INSERT INTO port_forwards (id, host_id, nickname, type, source_port, dest_addr, dest_port) VALUES (2, 1, 'bad', 'unsupported', 1081, NULL, 0)")

        db.execSQL("INSERT INTO known_hosts (id, host_id, hostname, port, host_key_algo, host_key) VALUES (1, 1, 'example.com', 22, 'ssh-ed25519', X'AA')")
        db.execSQL("INSERT INTO known_hosts (id, host_id, hostname, port, host_key_algo, host_key) VALUES (2, 1, 'example.com', 22, 'ssh-ed25519', X'AA')")
        db.execSQL("INSERT INTO known_hosts (id, host_id, hostname, port, host_key_algo, host_key) VALUES (3, 1, 'example.com', 22, 'ssh-rsa', X'BB')")
    }

    private fun insertHost(
        db: SQLiteDatabase,
        id: Long,
        nickname: String,
        protocol: String,
        profileId: Long,
        pubkeyId: Long,
        jumpHostId: Long?
    ) {
        db.execSQL(
            """
            INSERT INTO hosts (
                id, nickname, protocol, username, hostname, port, host_key_algo,
                last_connect, color, use_keys, use_auth_agent, post_login, pubkey_id,
                want_session, compression, stay_connected, quick_disconnect,
                scrollback_lines, use_ctrl_alt_as_meta_key, jump_host_id,
                profile_id, ip_version
            )
            VALUES (?, ?, ?, ?, ?, ?, NULL, 0, NULL, 1, 'no', NULL, ?, 1, 0, 0, 0, 140, 0, ?, ?, 'IPV4_AND_IPV6')
            """.trimIndent(),
            arrayOf<Any?>(id, nickname, protocol, "user", "example.com", 22, pubkeyId, jumpHostId, profileId)
        )
    }

    private companion object {
        const val TEST_DB = "shellpilot-v8-migration-test"
    }
}
