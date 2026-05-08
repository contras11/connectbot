package io.shellpilot.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.util.HostConstants
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PubkeyRepositoryTest {

    private lateinit var database: ShellPilotDatabase
    private lateinit var repository: PubkeyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShellPilotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            database.profileDao().insert(Profile(id = 1, name = "Default"))
        }
        repository = PubkeyRepository(database.pubkeyDao()).also {
            it.database = database
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun delete_clearsHostsUsingDeletedPubkey() = runTest {
        val pubkey = repository.save(pubkey("deploy-key"))
        val hostId = database.hostDao().insert(
            Host(
                nickname = "server",
                protocol = "ssh",
                hostname = "server.example.com",
                pubkeyId = pubkey.id
            )
        )

        repository.delete(pubkey)

        assertThat(database.pubkeyDao().getById(pubkey.id)).isNull()
        assertThat(database.hostDao().getById(hostId)?.pubkeyId).isEqualTo(HostConstants.PUBKEYID_NEVER)
    }

    @Test
    fun deleteById_returnsFalseForMissingPubkey() = runTest {
        assertThat(repository.deleteById(9999L)).isFalse()
    }

    private fun pubkey(nickname: String) = Pubkey(
        nickname = nickname,
        type = "ssh-rsa",
        privateKey = "private".toByteArray(),
        publicKey = "public".toByteArray(),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 1L
    )
}
