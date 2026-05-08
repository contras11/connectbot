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

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.shellpilot.app.data.dao.ColorSchemeDao
import io.shellpilot.app.data.dao.HostDao
import io.shellpilot.app.data.dao.KnownHostDao
import io.shellpilot.app.data.dao.PortForwardDao
import io.shellpilot.app.data.dao.ProfileDao
import io.shellpilot.app.data.dao.PubkeyDao
import io.shellpilot.app.data.entity.ColorPalette
import io.shellpilot.app.data.entity.ColorScheme
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.data.entity.Pubkey

/**
 * ShellPilot Room database.
 *
 * This database contains all the tables needed to run ShellPilot:
 * - hosts: SSH/Telnet connection configurations
 * - pubkeys: SSH key pairs with security-conscious backup controls
 * - port_forwards: SSH port forwarding rules
 * - known_hosts: SSH host key verification data
 * - color_schemes: Terminal color scheme metadata
 * - color_palette: Terminal color overrides
 * - profiles: Terminal profile configurations
 *
 * Migration Strategy:
 * - Version 1: Initial Room schema (migrated from HostDatabase v27 + PubkeyDatabase v2)
 * - Version 2: Added jump_host_id column for ProxyJump support (AutoMigration)
 * - Version 3: Added unique index on known_hosts (hostname, port) (AutoMigration)
 * - Version 4: Changed known_hosts index to (host_id, host_key) (AutoMigration)
 * - Version 5: Added profiles table and profile_id column to hosts (manual migration)
 * - Version 6: Added force_size_rows and force_size_columns to profiles (AutoMigration)
 * - Version 7: Added ip_version column to hosts for IP version preference (AutoMigration)
 * - Version 8: Normalized broken references and added core FK/index guards (manual migration)
 * - Future versions: Use Room AutoMigration when possible for simple schema changes
 *
 * Security Considerations:
 * - Pubkeys table supports per-key backup control via allowBackup field
 * - Custom BackupAgent filters pubkeys during backup/restore operations
 */
@Database(
    entities = [
        Host::class,
        Pubkey::class,
        PortForward::class,
        KnownHost::class,
        ColorScheme::class,
        ColorPalette::class,
        Profile::class
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 4),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7)
    ]
)
@TypeConverters(Converters::class)
abstract class ShellPilotDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun pubkeyDao(): PubkeyDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun colorSchemeDao(): ColorSchemeDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /**
         * Current database schema version.
         * This is also used for JSON export/import versioning.
         */
        const val SCHEMA_VERSION = 8

        /**
         * Migration from version 4 to 5: Add profiles table and profile_id to hosts.
         * Also creates profiles from existing host settings and migrates hosts to use them.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create profiles table
                // Note: No foreign key to color_schemes because built-in color schemes use negative IDs
                // and are virtual (not stored in the database). Only custom schemes have positive IDs.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon_color` TEXT,
                        `color_scheme_id` INTEGER NOT NULL DEFAULT -1,
                        `font_family` TEXT,
                        `font_size` INTEGER NOT NULL DEFAULT 10,
                        `del_key` TEXT NOT NULL DEFAULT 'del',
                        `encoding` TEXT NOT NULL DEFAULT 'UTF-8',
                        `emulation` TEXT NOT NULL DEFAULT 'xterm-256color'
                    )
                    """.trimIndent()
                )

                // Create index on profile name
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profiles_name` ON `profiles` (`name`)")

                // Insert default profile with color_scheme_id = -1 (Default built-in scheme)
                db.execSQL(
                    """
                    INSERT INTO `profiles` (`id`, `name`, `color_scheme_id`, `font_family`, `font_size`, `del_key`, `encoding`, `emulation`)
                    VALUES (1, 'Default', -1, NULL, 10, 'del', 'UTF-8', 'xterm-256color')
                    """.trimIndent()
                )

                // Create profiles from unique host settings combinations
                // Use a data class key: (color_scheme_id, font_size, del_key, encoding)
                // This groups hosts with identical terminal settings into shared profiles
                db.execSQL(
                    """
                    CREATE TEMP TABLE temp_profile_settings AS
                    SELECT DISTINCT color_scheme_id, font_size, del_key, encoding
                    FROM hosts
                    WHERE NOT (color_scheme_id = 1 AND font_size = 10 AND del_key = 'DEL' AND encoding = 'UTF-8')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `profiles` (`name`, `color_scheme_id`, `font_size`, `del_key`, `encoding`)
                    SELECT
                        'Migrated Profile ' || ROWID,
                        color_scheme_id,
                        font_size,
                        del_key,
                        encoding
                    FROM temp_profile_settings
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE temp_profile_settings")

                // Recreate hosts table without the old columns (encoding, font_size, color_scheme_id, del_key, font_family)
                // and add profile_id column. SQLite doesn't support DROP COLUMN before 3.35.0,
                // so we need to recreate the table.

                // Create new hosts table with correct schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hosts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `hostname` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `host_key_algo` TEXT,
                        `last_connect` INTEGER NOT NULL,
                        `color` TEXT,
                        `use_keys` INTEGER NOT NULL,
                        `use_auth_agent` TEXT,
                        `post_login` TEXT,
                        `pubkey_id` INTEGER NOT NULL,
                        `want_session` INTEGER NOT NULL,
                        `compression` INTEGER NOT NULL,
                        `stay_connected` INTEGER NOT NULL,
                        `quick_disconnect` INTEGER NOT NULL,
                        `scrollback_lines` INTEGER NOT NULL,
                        `use_ctrl_alt_as_meta_key` INTEGER NOT NULL,
                        `jump_host_id` INTEGER,
                        `profile_id` INTEGER
                    )
                    """.trimIndent()
                )

                // Copy data from old table to new table, mapping old columns to profile_id
                db.execSQL(
                    """
                    INSERT INTO `hosts_new` (
                        `id`, `nickname`, `protocol`, `username`, `hostname`, `port`,
                        `host_key_algo`, `last_connect`, `color`, `use_keys`, `use_auth_agent`,
                        `post_login`, `pubkey_id`, `want_session`, `compression`, `stay_connected`,
                        `quick_disconnect`, `scrollback_lines`, `use_ctrl_alt_as_meta_key`,
                        `jump_host_id`, `profile_id`
                    )
                    SELECT
                        h.id, h.nickname, h.protocol, h.username, h.hostname, h.port,
                        h.host_key_algo, h.last_connect, h.color, h.use_keys, h.use_auth_agent,
                        h.post_login, h.pubkey_id, h.want_session, h.compression, h.stay_connected,
                        h.quick_disconnect, h.scrollback_lines, h.use_ctrl_alt_as_meta_key,
                        h.jump_host_id,
                        COALESCE((
                            SELECT p.id FROM profiles p
                            WHERE p.color_scheme_id = h.color_scheme_id
                              AND p.font_size = h.font_size
                              AND p.del_key = h.del_key
                              AND p.encoding = h.encoding
                            LIMIT 1
                        ), 1) as profile_id
                    FROM hosts h
                    """.trimIndent()
                )

                // Drop old table
                db.execSQL("DROP TABLE `hosts`")

                // Rename new table to hosts
                db.execSQL("ALTER TABLE `hosts_new` RENAME TO `hosts`")

                // Recreate indices
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hosts_nickname` ON `hosts` (`nickname`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_protocol_username_hostname_port` ON `hosts` (`protocol`, `username`, `hostname`, `port`)")
            }
        }

        /**
         * Migration from version 7 to 8: normalize legacy/corrupted references and
         * add the minimum FK/index constraints needed to keep core data consistent.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 変更理由: hosts.profile_id の FK 追加前に、Default profile(id=1) を必ず用意する。
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `profiles` (
                        `id`, `name`, `color_scheme_id`, `font_family`, `font_size`,
                        `del_key`, `encoding`, `emulation`, `force_size_rows`, `force_size_columns`
                    )
                    VALUES (1, 'Default', -1, NULL, 10, 'del', 'UTF-8', 'xterm-256color', NULL, NULL)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE `profiles`
                    SET `color_scheme_id` = -1
                    WHERE `color_scheme_id` > 0
                      AND NOT EXISTS (
                          SELECT 1 FROM `color_schemes`
                          WHERE `color_schemes`.`id` = `profiles`.`color_scheme_id`
                      )
                    """.trimIndent()
                )

                // 変更理由: Keystore鍵や壊れたexportable鍵が起動時ロードやバックアップに混ざらないよう正規化する。
                db.execSQL(
                    """
                    UPDATE `pubkeys`
                    SET `startup` = 0,
                        `allow_backup` = 0,
                        `private_key` = NULL
                    WHERE `storage_type` = 'ANDROID_KEYSTORE'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `pubkeys`
                    SET `startup` = 0
                    WHERE `storage_type` = 'EXPORTABLE'
                      AND `private_key` IS NULL
                    """.trimIndent()
                )

                // 変更理由: 旧dynamic4は現SSH実装ではdynamic5相当として扱い、不明typeは接続時事故を避けて落とす。
                db.execSQL("UPDATE `port_forwards` SET `type` = 'dynamic5' WHERE `type` = 'dynamic4'")
                db.execSQL("DELETE FROM `port_forwards` WHERE `type` NOT IN ('local', 'remote', 'dynamic5')")

                db.execSQL(
                    """
                    UPDATE `hosts`
                    SET `profile_id` = 1
                    WHERE `profile_id` IS NULL
                       OR `profile_id` <= 0
                       OR NOT EXISTS (
                           SELECT 1 FROM `profiles`
                           WHERE `profiles`.`id` = `hosts`.`profile_id`
                       )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `hosts`
                    SET `pubkey_id` = -2
                    WHERE `pubkey_id` > 0
                      AND NOT EXISTS (
                          SELECT 1 FROM `pubkeys`
                          WHERE `pubkeys`.`id` = `hosts`.`pubkey_id`
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `hosts`
                    SET `jump_host_id` = NULL
                    WHERE `jump_host_id` IS NOT NULL
                      AND (
                          `jump_host_id` = `id`
                          OR NOT EXISTS (
                              SELECT 1 FROM `hosts` AS `jump`
                              WHERE `jump`.`id` = `hosts`.`jump_host_id`
                                AND `jump`.`protocol` = 'ssh'
                          )
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    WITH RECURSIVE `jump_chain`(`root_id`, `next_id`, `path`, `cycle`) AS (
                        SELECT `id`, `jump_host_id`, ',' || `id` || ',', 0
                        FROM `hosts`
                        WHERE `jump_host_id` IS NOT NULL
                        UNION ALL
                        SELECT
                            `jump_chain`.`root_id`,
                            `hosts`.`jump_host_id`,
                            `jump_chain`.`path` || `hosts`.`id` || ',',
                            CASE
                                WHEN instr(`jump_chain`.`path`, ',' || `hosts`.`id` || ',') > 0 THEN 1
                                ELSE 0
                            END
                        FROM `jump_chain`
                        JOIN `hosts` ON `hosts`.`id` = `jump_chain`.`next_id`
                        WHERE `jump_chain`.`next_id` IS NOT NULL
                          AND `jump_chain`.`cycle` = 0
                    )
                    UPDATE `hosts`
                    SET `jump_host_id` = NULL
                    WHERE `id` IN (
                        SELECT DISTINCT `root_id`
                        FROM `jump_chain`
                        WHERE `cycle` = 1
                    )
                    """.trimIndent()
                )

                recreateHostsTable(db)
                normalizeKnownHosts(db)

                db.execSQL("PRAGMA foreign_keys=ON")
            }

            private fun recreateHostsTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hosts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `hostname` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `host_key_algo` TEXT,
                        `last_connect` INTEGER NOT NULL,
                        `color` TEXT,
                        `use_keys` INTEGER NOT NULL,
                        `use_auth_agent` TEXT,
                        `post_login` TEXT,
                        `pubkey_id` INTEGER NOT NULL,
                        `want_session` INTEGER NOT NULL,
                        `compression` INTEGER NOT NULL,
                        `stay_connected` INTEGER NOT NULL,
                        `quick_disconnect` INTEGER NOT NULL,
                        `scrollback_lines` INTEGER NOT NULL,
                        `use_ctrl_alt_as_meta_key` INTEGER NOT NULL,
                        `jump_host_id` INTEGER,
                        `profile_id` INTEGER DEFAULT 1,
                        `ip_version` TEXT NOT NULL DEFAULT 'IPV4_AND_IPV6',
                        FOREIGN KEY(`profile_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE SET DEFAULT,
                        FOREIGN KEY(`jump_host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `hosts_new` (
                        `id`, `nickname`, `protocol`, `username`, `hostname`, `port`,
                        `host_key_algo`, `last_connect`, `color`, `use_keys`, `use_auth_agent`,
                        `post_login`, `pubkey_id`, `want_session`, `compression`, `stay_connected`,
                        `quick_disconnect`, `scrollback_lines`, `use_ctrl_alt_as_meta_key`,
                        `jump_host_id`, `profile_id`, `ip_version`
                    )
                    SELECT
                        `id`, `nickname`, `protocol`, `username`, `hostname`, `port`,
                        `host_key_algo`, `last_connect`, `color`, `use_keys`, `use_auth_agent`,
                        `post_login`, `pubkey_id`, `want_session`, `compression`, `stay_connected`,
                        `quick_disconnect`, `scrollback_lines`, `use_ctrl_alt_as_meta_key`,
                        NULL, `profile_id`, `ip_version`
                    FROM `hosts`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `hosts_new`
                    SET `jump_host_id` = (
                        SELECT `hosts`.`jump_host_id`
                        FROM `hosts`
                        WHERE `hosts`.`id` = `hosts_new`.`id`
                    )
                    WHERE EXISTS (
                        SELECT 1
                        FROM `hosts`
                        WHERE `hosts`.`id` = `hosts_new`.`id`
                          AND `hosts`.`jump_host_id` IS NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `hosts`")
                db.execSQL("ALTER TABLE `hosts_new` RENAME TO `hosts`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hosts_nickname` ON `hosts` (`nickname`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_protocol_username_hostname_port` ON `hosts` (`protocol`, `username`, `hostname`, `port`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_profile_id` ON `hosts` (`profile_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_jump_host_id` ON `hosts` (`jump_host_id`)")
            }

            private fun normalizeKnownHosts(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM `known_hosts`
                    WHERE `id` NOT IN (
                        SELECT MIN(`id`)
                        FROM `known_hosts`
                        GROUP BY
                            COALESCE(`host_id`, -1),
                            `hostname`,
                            `port`,
                            `host_key_algo`,
                            hex(`host_key`)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS `index_known_hosts_host_id_host_key`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id` ON `known_hosts` (`host_id`)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_known_hosts_host_id_hostname_port_host_key_algo_host_key`
                    ON `known_hosts` (`host_id`, `hostname`, `port`, `host_key_algo`, `host_key`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id_hostname_port_host_key_algo`
                    ON `known_hosts` (`host_id`, `hostname`, `port`, `host_key_algo`)
                    """.trimIndent()
                )
            }
        }
    }
}
