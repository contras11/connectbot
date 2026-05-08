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

    private fun host(
        nickname: String,
        hostname: String,
        profileId: Long? = 1L,
        pubkeyId: Long = HostConstants.PUBKEYID_ANY,
        jumpHostId: Long? = null
    ): Host {
        return Host(
            nickname = nickname,
            protocol = "ssh",
            username = "alice",
            hostname = hostname,
            port = 22,
            profileId = profileId,
            pubkeyId = pubkeyId,
            jumpHostId = jumpHostId
        )
    }

    private fun countRows(tableName: String): Int {
        val cursor = destinationDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName")
        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }
}
