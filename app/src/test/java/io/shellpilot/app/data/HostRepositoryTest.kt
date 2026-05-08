package io.shellpilot.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shellpilot.app.data.dao.HostDao
import io.shellpilot.app.data.dao.KnownHostDao
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.util.HostConstants
import io.shellpilot.app.util.SecurePasswordStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun saveHost_whenExistingJumpHostBecomesNonSsh_clearsReferencingHosts() = runTest {
        val jumpHostId = hostDao.insert(Host(nickname = "jump", protocol = "ssh", hostname = "jump.example.com"))
        val appHostId = hostDao.insert(
            Host(
                nickname = "app",
                protocol = "ssh",
                hostname = "app.example.com",
                jumpHostId = jumpHostId
            )
        )

        repository.saveHost(hostDao.getById(jumpHostId)!!.copy(protocol = "telnet"))

        assertThat(hostDao.getById(appHostId)?.jumpHostId).isNull()
    }

    @Test
    fun saveHost_whenHostBecomesNonSsh_clearsOwnSshOnlyData() = runTest {
        val hostId = hostDao.insert(
            Host(
                nickname = "server",
                protocol = "ssh",
                username = "alice",
                hostname = "server.example.com",
                pubkeyId = 42L,
                jumpHostId = null
            )
        )
        database.portForwardDao().insert(
            PortForward(
                hostId = hostId,
                nickname = "web",
                type = HostConstants.PORTFORWARD_LOCAL,
                sourcePort = 18080,
                destAddr = "127.0.0.1",
                destPort = 8080
            )
        )
        knownHostDao.insert(knownHost(hostId, "server.example.com", 22, "ssh-rsa", "key"))

        repository.saveHost(hostDao.getById(hostId)!!.copy(protocol = "local"))

        val saved = hostDao.getById(hostId)!!
        assertThat(saved.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
        assertThat(saved.jumpHostId).isNull()
        assertThat(database.portForwardDao().getByHost(hostId)).isEmpty()
        assertThat(knownHostDao.getByHostId(hostId)).isEmpty()
    }

    @Test
    fun savePortForward_rejectsNonSshHost() = runTest {
        val telnetHostId = hostDao.insert(Host(nickname = "telnet", protocol = "telnet", hostname = "telnet.example.com"))

        assertThatThrownBy {
            runBlocking {
                repository.savePortForward(
                    PortForward(
                        hostId = telnetHostId,
                        nickname = "pf",
                        type = HostConstants.PORTFORWARD_LOCAL,
                        sourcePort = 8080,
                        destAddr = "example.com",
                        destPort = 80
                    )
                )
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun savePortForward_normalizesDynamicAndBlankNickname() = runTest {
        val hostId = hostDao.insert(Host(nickname = "ssh", protocol = "ssh", hostname = "ssh.example.com"))

        val saved = repository.savePortForward(
            PortForward(
                hostId = hostId,
                nickname = "  ",
                type = HostConstants.PORTFORWARD_DYNAMIC5,
                sourcePort = 1080,
                destAddr = "ignored.example.com",
                destPort = 1080
            )
        )

        assertThat(saved.nickname).isEqualTo("Dynamic 1080")
        assertThat(saved.destAddr).isNull()
    }

    @Test
    fun savePortForward_requiresDestinationForLocalAndRemote() = runTest {
        val hostId = hostDao.insert(Host(nickname = "ssh2", protocol = "ssh", hostname = "ssh2.example.com"))

        assertThatThrownBy {
            runBlocking {
                repository.savePortForward(
                    PortForward(
                        hostId = hostId,
                        nickname = "local",
                        type = HostConstants.PORTFORWARD_LOCAL,
                        sourcePort = 8080,
                        destAddr = " ",
                        destPort = 80
                    )
                )
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun removeKnownHost_usesEndpointWhenProvided() = runTest {
        val hostId = hostDao.insert(Host(nickname = "server", protocol = "ssh", hostname = "example.com"))
        val key = "shared-key".toByteArray()
        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-rsa", "old-rsa"))
        knownHostDao.insert(knownHost(hostId, "example.com", 2222, "ssh-rsa", "other-port"))
        knownHostDao.insert(knownHost(hostId, "other.example.com", 22, "ssh-rsa", "other-hostname"))
        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-ed25519", "other-algo"))
        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-rsa", key.decodeToString()))

        repository.removeKnownHost(hostId, "example.com", 22, "ssh-rsa", key)

        assertThat(knownHostDao.getByHostId(hostId).map { it.hostKey.decodeToString() })
            .containsExactlyInAnyOrder("old-rsa", "other-port", "other-hostname", "other-algo")
    }

    @Test
    fun getHostKeyAlgorithmsForEndpoint_filtersHostnameAndPort() = runTest {
        val hostId = hostDao.insert(Host(nickname = "server2", protocol = "ssh", hostname = "example.com"))
        knownHostDao.insert(knownHost(hostId, "example.com", 22, "ssh-rsa", "rsa"))
        knownHostDao.insert(knownHost(hostId, "example.com", 2222, "ssh-ed25519", "ed"))

        val algorithms = repository.getHostKeyAlgorithmsForEndpoint(hostId, "example.com", 22)

        assertThat(algorithms).containsExactly("ssh-rsa")
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
