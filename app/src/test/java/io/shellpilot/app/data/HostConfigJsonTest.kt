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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.util.HostConstants
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Host JSON import/export の参照変換を検証する。
 *
 * 変更理由: profileId / pubkeyId / jumpHostId は Room FK だけでは表現されず、
 * 汎用推定で変換すると別鍵・別プロファイルへ誤って紐づくため。
 */
@RunWith(AndroidJUnit4::class)
class HostConfigJsonTest {

    private lateinit var context: Context
    private lateinit var sourceDb: ShellPilotDatabase
    private lateinit var destinationDb: ShellPilotDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = Room.inMemoryDatabaseBuilder(context, ShellPilotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        destinationDb = Room.inMemoryDatabaseBuilder(context, ShellPilotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            sourceDb.profileDao().insert(Profile(id = 1, name = "Default"))
            destinationDb.profileDao().insert(Profile(id = 1, name = "Default"))
        }
    }

    @After
    fun tearDown() {
        sourceDb.close()
        destinationDb.close()
    }

    @Test
    fun importFromJson_remapsProfileAndJumpHostButDropsPubkeyReference() = runTest {
        val sourceProfileId = sourceDb.profileDao().insert(Profile(name = "Remote profile"))
        val jumpHostId = sourceDb.hostDao().insert(
            host(
                nickname = "jump",
                hostname = "jump.example.com",
                profileId = sourceProfileId,
                pubkeyId = 41L
            )
        )
        val appHostId = sourceDb.hostDao().insert(
            host(
                nickname = "app",
                hostname = "app.example.com",
                profileId = sourceProfileId,
                pubkeyId = 42L,
                jumpHostId = jumpHostId
            )
        )
        sourceDb.portForwardDao().insert(
            PortForward(
                hostId = appHostId,
                nickname = "web",
                type = "local",
                sourcePort = 18080,
                destAddr = "127.0.0.1",
                destPort = 8080
            )
        )

        val existingProfileId = destinationDb.profileDao().insert(Profile(name = "Remote profile"))
        destinationDb.hostDao().insert(
            host(nickname = "unrelated", hostname = "unrelated.example.com", pubkeyId = 999L)
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        val counts = HostConfigJson.importFromJson(context, destinationDb, json)

        val importedHosts = destinationDb.hostDao().getAll()
        val importedJump = importedHosts.first { it.nickname == "jump" }
        val importedApp = importedHosts.first { it.nickname == "app" }
        val forwards = destinationDb.portForwardDao().getByHost(importedApp.id)

        assertThat(counts.hostsImported).isEqualTo(2)
        assertThat(counts.profilesSkipped).isEqualTo(2)
        assertThat(importedJump.profileId).isEqualTo(existingProfileId)
        assertThat(importedApp.profileId).isEqualTo(existingProfileId)
        assertThat(importedJump.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
        assertThat(importedApp.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
        assertThat(importedApp.jumpHostId).isEqualTo(importedJump.id)
        assertThat(forwards).hasSize(1)
        assertThat(forwards.single().hostId).isEqualTo(importedApp.id)
    }

    @Test
    fun importFromJson_keepsSpecialPubkeyValuesButDropsPositivePubkeyToNever() = runTest {
        sourceDb.hostDao().insert(host(nickname = "any-key", hostname = "any.example.com", pubkeyId = HostConstants.PUBKEYID_ANY))
        sourceDb.hostDao().insert(host(nickname = "never-key", hostname = "never.example.com", pubkeyId = HostConstants.PUBKEYID_NEVER))
        sourceDb.hostDao().insert(host(nickname = "specific-key", hostname = "specific.example.com", pubkeyId = 77L))

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, json)

        val importedHosts = destinationDb.hostDao().getAll()
        assertThat(importedHosts.first { it.nickname == "any-key" }.pubkeyId).isEqualTo(HostConstants.PUBKEYID_ANY)
        assertThat(importedHosts.first { it.nickname == "never-key" }.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
        assertThat(importedHosts.first { it.nickname == "specific-key" }.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
    }

    @Test
    fun importFromJson_selfReferenceSecondPassDoesNotUpdateSkippedExistingHost() = runTest {
        val profileId = sourceDb.profileDao().insert(Profile(name = "Shared"))
        val sourceJumpId = sourceDb.hostDao().insert(host(nickname = "jump", hostname = "jump.example.com", profileId = profileId))
        sourceDb.hostDao().insert(
            host(
                nickname = "app",
                hostname = "source-app.example.com",
                profileId = profileId,
                jumpHostId = sourceJumpId
            )
        )

        destinationDb.profileDao().insert(Profile(name = "Shared"))
        val existingAppId = destinationDb.hostDao().insert(
            host(nickname = "app", hostname = "existing-app.example.com", jumpHostId = null)
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        val counts = HostConfigJson.importFromJson(context, destinationDb, json)

        val existingApp = destinationDb.hostDao().getById(existingAppId)
        assertThat(counts.hostsImported).isEqualTo(1)
        assertThat(counts.hostsSkipped).isEqualTo(1)
        assertThat(existingApp?.jumpHostId).isNull()
    }

    @Test
    fun importFromJson_portForwardsWithCollidingSourceIdAreInsertedWhenNoUniqueIndexExists() = runTest {
        val sourceProfileId = sourceDb.profileDao().insert(Profile(name = "Source profile"))
        val sourceHostId = sourceDb.hostDao().insert(host(nickname = "source-host", hostname = "source.example.com", profileId = sourceProfileId))
        sourceDb.portForwardDao().insert(
            PortForward(
                hostId = sourceHostId,
                nickname = "source-forward",
                type = "local",
                sourcePort = 10022,
                destAddr = "127.0.0.1",
                destPort = 22
            )
        )

        val destinationHostId = destinationDb.hostDao().insert(host(nickname = "existing-host", hostname = "existing.example.com"))
        destinationDb.portForwardDao().insert(
            PortForward(
                hostId = destinationHostId,
                nickname = "existing-forward",
                type = "local",
                sourcePort = 20022,
                destAddr = "127.0.0.1",
                destPort = 22
            )
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, json)

        val importedHost = destinationDb.hostDao().getAll().first { it.nickname == "source-host" }
        val importedForwards = destinationDb.portForwardDao().getByHost(importedHost.id)
        val totalForwards = countRows("port_forwards")

        assertThat(importedForwards).hasSize(1)
        assertThat(importedForwards.single().nickname).isEqualTo("source-forward")
        assertThat(totalForwards).isEqualTo(2)
    }

    @Test
    fun importFromJson_dropsCustomColorSchemeReference() = runTest {
        val sourceProfileId = sourceDb.profileDao().insert(Profile(name = "Custom colors", colorSchemeId = 99L))
        sourceDb.hostDao().insert(host(nickname = "profile-host", hostname = "profile.example.com", profileId = sourceProfileId))

        val (exportedJson, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, exportedJson)

        val importedProfile = destinationDb.profileDao().getAll().first { it.name == "Custom colors" }
        val importedHost = destinationDb.hostDao().getAll().first { it.nickname == "profile-host" }
        assertThat(importedProfile.colorSchemeId).isEqualTo(-1L)
        assertThat(importedHost.profileId).isEqualTo(importedProfile.id)
    }

    @Test
    fun importFromJson_fallsBackMissingProfileToDefault() = runTest {
        sourceDb.hostDao().insert(host(nickname = "default-host", hostname = "default.example.com"))

        val (exportedJson, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        val json = JSONObject(exportedJson)
        json.put("profiles", org.json.JSONArray())
        json.getJSONArray("hosts").getJSONObject(0).put("profileId", 999L)

        HostConfigJson.importFromJson(context, destinationDb, json.toString())

        val importedHosts = destinationDb.hostDao().getAll()
        assertThat(importedHosts.first { it.nickname == "default-host" }.profileId).isEqualTo(1L)
        assertThat(destinationDb.profileDao().getAll()).extracting("name").containsExactly("Default")
    }

    @Test
    fun importFromJson_sanitizesJumpHostsAndPortForwardTypes() = runTest {
        val jumpAId = sourceDb.hostDao().insert(host(nickname = "jump-a", hostname = "jump-a.example.com"))
        val jumpBId = sourceDb.hostDao().insert(host(nickname = "jump-b", hostname = "jump-b.example.com", jumpHostId = jumpAId))
        sourceDb.hostDao().update(sourceDb.hostDao().getById(jumpAId)!!.copy(jumpHostId = jumpBId))
        val telnetId = sourceDb.hostDao().insert(
            host(nickname = "telnet", hostname = "telnet.example.com").copy(protocol = "telnet")
        )
        val appId = sourceDb.hostDao().insert(host(nickname = "app", hostname = "app.example.com", jumpHostId = telnetId))
        sourceDb.portForwardDao().insert(
            PortForward(
                hostId = appId,
                nickname = "old-dynamic",
                type = HostConstants.PORTFORWARD_DYNAMIC4,
                sourcePort = 1080,
                destAddr = null,
                destPort = 0
            )
        )
        sourceDb.portForwardDao().insert(
            PortForward(
                hostId = appId,
                nickname = "bad-type",
                type = "udp",
                sourcePort = 1081,
                destAddr = null,
                destPort = 0
            )
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, json)

        val importedHosts = destinationDb.hostDao().getAll()
        assertThat(importedHosts.first { it.nickname == "jump-a" }.jumpHostId).isNull()
        assertThat(importedHosts.first { it.nickname == "jump-b" }.jumpHostId).isNull()
        assertThat(importedHosts.first { it.nickname == "app" }.jumpHostId).isNull()

        val importedApp = importedHosts.first { it.nickname == "app" }
        val importedForwards = destinationDb.portForwardDao().getByHost(importedApp.id)
        assertThat(importedForwards).hasSize(1)
        assertThat(importedForwards.single().nickname).isEqualTo("old-dynamic")
        assertThat(importedForwards.single().type).isEqualTo(HostConstants.PORTFORWARD_DYNAMIC5)
    }

    @Test
    fun importFromJson_sanitizesInvalidRuntimeValuesBeforeRawInsert() = runTest {
        val json = JSONObject()
            .put("version", ShellPilotDatabase.SCHEMA_VERSION)
            .put("profiles", org.json.JSONArray())
            .put(
                "hosts",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", 100L)
                        .put("nickname", "broken-host")
                        .put("protocol", "ftp")
                        .put("username", "alice")
                        .put("hostname", "broken.example.com")
                        .put("port", 70000)
                        .put("color", JSONObject.NULL)
                        .put("useKeys", 1)
                        .put("useAuthAgent", "agent")
                        .put("postLogin", JSONObject.NULL)
                        .put("pubkeyId", 999L)
                        .put("wantSession", 1)
                        .put("compression", 0)
                        .put("stayConnected", 0)
                        .put("quickDisconnect", 0)
                        .put("scrollbackLines", 200000)
                        .put("useCtrlAltAsMetaKey", 0)
                        .put("jumpHostId", JSONObject.NULL)
                        .put("profileId", 1L)
                        .put("ipVersion", "BROKEN")
                )
            )
            .put(
                "port_forwards",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("id", 10L)
                            .put("hostId", 100L)
                            .put("nickname", "bad-port")
                            .put("type", HostConstants.PORTFORWARD_LOCAL)
                            .put("sourcePort", 70000)
                            .put("destAddr", "127.0.0.1")
                            .put("destPort", 22)
                    )
                    .put(
                        JSONObject()
                            .put("id", 11L)
                            .put("hostId", 100L)
                            .put("nickname", "dynamic")
                            .put("type", HostConstants.PORTFORWARD_DYNAMIC4)
                            .put("sourcePort", 1080)
                            .put("destAddr", "ignored.example.com")
                            .put("destPort", 2222)
                    )
            )

        HostConfigJson.importFromJson(context, destinationDb, json.toString())

        val imported = destinationDb.hostDao().getAll().first { it.nickname == "broken-host" }
        assertThat(imported.protocol).isEqualTo("ssh")
        assertThat(imported.port).isEqualTo(22)
        assertThat(imported.useAuthAgent).isEqualTo(HostConstants.AUTHAGENT_NO)
        assertThat(imported.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
        assertThat(imported.scrollbackLines).isEqualTo(140)
        assertThat(imported.ipVersion).isEqualTo("IPV4_AND_IPV6")
        val forwards = destinationDb.portForwardDao().getByHost(imported.id)
        assertThat(forwards).hasSize(1)
        assertThat(forwards.single().type).isEqualTo(HostConstants.PORTFORWARD_DYNAMIC5)
        assertThat(forwards.single().destAddr).isNull()
        assertThat(forwards.single().destPort).isEqualTo(0)
    }

    @Test
    fun importFromJson_skipsPortForwardsWhenParentHostAlreadyExists() = runTest {
        val sourceHostId = sourceDb.hostDao().insert(host(nickname = "existing-host", hostname = "source.example.com"))
        sourceDb.portForwardDao().insert(
            PortForward(
                hostId = sourceHostId,
                nickname = "source-forward",
                type = "local",
                sourcePort = 10022,
                destAddr = "127.0.0.1",
                destPort = 22
            )
        )
        val destinationHostId = destinationDb.hostDao().insert(host(nickname = "existing-host", hostname = "destination.example.com"))
        destinationDb.portForwardDao().insert(
            PortForward(
                hostId = destinationHostId,
                nickname = "existing-forward",
                type = "local",
                sourcePort = 20022,
                destAddr = "127.0.0.1",
                destPort = 22
            )
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, json)
        HostConfigJson.importFromJson(context, destinationDb, json)

        val destinationForwards = destinationDb.portForwardDao().getByHost(destinationHostId)
        assertThat(destinationForwards).hasSize(1)
        assertThat(destinationForwards.single().nickname).isEqualTo("existing-forward")
    }

    @Test
    fun importFromJson_normalizesJumpHostAfterExistingHostRemap() = runTest {
        val sourceJumpId = sourceDb.hostDao().insert(host(nickname = "shared-jump", hostname = "source-jump.example.com"))
        sourceDb.hostDao().insert(host(nickname = "app", hostname = "app.example.com", jumpHostId = sourceJumpId))
        destinationDb.hostDao().insert(
            host(nickname = "shared-jump", hostname = "telnet-jump.example.com").copy(protocol = "telnet")
        )

        val (json, _) = HostConfigJson.exportToJson(context, sourceDb, pretty = false)
        HostConfigJson.importFromJson(context, destinationDb, json)

        val importedApp = destinationDb.hostDao().getAll().first { it.nickname == "app" }
        assertThat(importedApp.jumpHostId).isNull()
    }

    @Test
    fun schemaBasedImporter_usesRoomDefaultValueForExcludedNotNullFields() = runTest {
        val schema = DatabaseSchema.fromJsonForTesting(schemaWithExcludedHostDefaults())
        val exporter = SchemaBasedExporter(destinationDb, schema)
        val json = JSONObject()
            .put("version", 9)
            .put(
                "hosts",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", 100L)
                        .put("nickname", "schema-default-host")
                        .put("protocol", "ssh")
                        .put("username", "alice")
                        .put("hostname", "schema.example.com")
                        .put("port", 22)
                        .put("color", JSONObject.NULL)
                        .put("useKeys", 1)
                        .put("useAuthAgent", "no")
                        .put("postLogin", JSONObject.NULL)
                        .put("pubkeyId", HostConstants.PUBKEYID_NEVER)
                        .put("wantSession", 1)
                        .put("compression", 0)
                        .put("stayConnected", 0)
                        .put("quickDisconnect", 0)
                        .put("scrollbackLines", 140)
                        .put("useCtrlAltAsMetaKey", 0)
                        .put("jumpHostId", JSONObject.NULL)
                        .put("profileId", 1L)
                )
            )

        exporter.importFromJson(json.toString(), listOf("hosts"))

        val imported = destinationDb.hostDao().getAll().first { it.nickname == "schema-default-host" }
        assertThat(imported.lastConnect).isEqualTo(1234L)
        assertThat(imported.ipVersion).isEqualTo("IPV6_ONLY")
    }

    private fun host(
        nickname: String,
        hostname: String,
        profileId: Long = 1L,
        pubkeyId: Long = HostConstants.PUBKEYID_ANY,
        jumpHostId: Long? = null
    ): Host = Host(
        nickname = nickname,
        protocol = "ssh",
        username = "alice",
        hostname = hostname,
        port = 22,
        profileId = profileId,
        pubkeyId = pubkeyId,
        jumpHostId = jumpHostId
    )

    private fun countRows(tableName: String): Int {
        val cursor = destinationDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName")
        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    private fun schemaWithExcludedHostDefaults(): JSONObject {
        fun field(
            fieldPath: String,
            columnName: String,
            affinity: String,
            notNull: Boolean = false,
            excluded: Boolean = false,
            defaultValue: String? = null
        ): JSONObject = JSONObject()
            .put("fieldPath", fieldPath)
            .put("columnName", columnName)
            .put("affinity", affinity)
            .apply {
                if (notNull) put("notNull", true)
                if (excluded) put("excluded", true)
                if (defaultValue != null) put("defaultValue", defaultValue)
            }

        val fields = org.json.JSONArray()
            .put(field("id", "id", "INTEGER", notNull = true))
            .put(field("nickname", "nickname", "TEXT", notNull = true))
            .put(field("protocol", "protocol", "TEXT", notNull = true))
            .put(field("username", "username", "TEXT", notNull = true))
            .put(field("hostname", "hostname", "TEXT", notNull = true))
            .put(field("port", "port", "INTEGER", notNull = true))
            .put(field("hostKeyAlgo", "host_key_algo", "TEXT", excluded = true))
            .put(field("lastConnect", "last_connect", "INTEGER", notNull = true, excluded = true, defaultValue = "1234"))
            .put(field("color", "color", "TEXT"))
            .put(field("useKeys", "use_keys", "INTEGER", notNull = true))
            .put(field("useAuthAgent", "use_auth_agent", "TEXT"))
            .put(field("postLogin", "post_login", "TEXT"))
            .put(field("pubkeyId", "pubkey_id", "INTEGER", notNull = true))
            .put(field("wantSession", "want_session", "INTEGER", notNull = true))
            .put(field("compression", "compression", "INTEGER", notNull = true))
            .put(field("stayConnected", "stay_connected", "INTEGER", notNull = true))
            .put(field("quickDisconnect", "quick_disconnect", "INTEGER", notNull = true))
            .put(field("scrollbackLines", "scrollback_lines", "INTEGER", notNull = true))
            .put(field("useCtrlAltAsMetaKey", "use_ctrl_alt_as_meta_key", "INTEGER", notNull = true))
            .put(field("jumpHostId", "jump_host_id", "INTEGER"))
            .put(field("profileId", "profile_id", "INTEGER", notNull = true, defaultValue = "1"))
            .put(field("ipVersion", "ip_version", "TEXT", notNull = true, excluded = true, defaultValue = "'IPV6_ONLY'"))

        val hostsEntity = JSONObject()
            .put("tableName", "hosts")
            .put("fields", fields)
            .put(
                "primaryKey",
                JSONObject()
                    .put("autoGenerate", true)
                    .put("columnNames", org.json.JSONArray().put("id"))
            )
            .put("foreignKeys", org.json.JSONArray())
            .put("indices", org.json.JSONArray())

        return JSONObject()
            .put(
                "database",
                JSONObject()
                    .put("version", 9)
                    .put("entities", org.json.JSONArray().put(hostsEntity))
            )
    }
}
