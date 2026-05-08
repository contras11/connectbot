package io.shellpilot.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shellpilot.app.data.dao.HostDao
import io.shellpilot.app.data.dao.KnownHostDao
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.util.SecurePasswordStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostRepositoryTest {

    private lateinit var database: ShellPilotDatabase
    private lateinit var hostDao: HostDao
    private lateinit var knownHostDao: KnownHostDao
    private lateinit var repository: HostRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShellPilotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hostDao = database.hostDao()
        knownHostDao = database.knownHostDao()
        runBlocking {
            database.profileDao().insert(Profile(id = 1, name = "Default"))
        }
        repository = HostRepository(
            context,
            database,
            hostDao,
            database.portForwardDao(),
            knownHostDao,
            SecurePasswordStorage(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceKnownHostForEndpoint_replacesOnlySameEndpointAndAlgorithm() = runTest {
        val hostId = hostDao.insert(
            Host(
                nickname = "server",
                protocol = "ssh",
                username = "alice",
                hostname = "example.com",
                port = 22
            )
        )
        val host = hostDao.getById(hostId)!!

        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-rsa", "old-rsa"))
        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-ed25519", "old-ed"))
        knownHostDao.insert(knownHost(hostId, "example.com", 2222, "ssh-rsa", "other-port"))

        repository.replaceKnownHostForEndpoint(
            host,
            hostname = "example.com",
            port = 22,
            serverHostKeyAlgorithm = "ssh-rsa",
            serverHostKey = "new-rsa".toByteArray()
        )

        val saved = knownHostDao.getByHostId(hostId)
        assertThat(saved.map { it.hostKey.decodeToString() })
            .containsExactlyInAnyOrder("new-rsa", "old-ed", "other-port")
        val endpointKeys = saved.filter {
            it.hostname == "example.com" && it.port == 22 && it.hostKeyAlgo == "ssh-rsa"
        }
        assertThat(endpointKeys).hasSize(1)
        assertThat(endpointKeys[0].hostKey.decodeToString()).isEqualTo("new-rsa")
    }

    private fun knownHost(
        hostId: Long,
        hostname: String,
        port: Int,
        algorithm: String,
        key: String
    ) = KnownHost(
        hostId = hostId,
        hostname = hostname,
        port = port,
        hostKeyAlgo = algorithm,
        hostKey = key.toByteArray()
    )
}
