package io.shellpilot.app.transport

import androidx.core.net.toUri
import io.shellpilot.app.service.TerminalManager
import io.shellpilot.app.util.HostConstants
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator

@RunWith(RobolectricTestRunner::class)
class SSHTransportTest {

    @Test
    fun createHost_stripsPasswordFromUserInfo() {
        val host = SSH().createHost("ssh://alice:secret@example.com:2222".toUri())

        assertThat(host.username).isEqualTo("alice")
        assertThat(host.nickname).isEqualTo("alice@example.com:2222")
        assertThat(host.nickname).doesNotContain("secret")
    }

    @Test
    fun getSelectionArgs_stripsPasswordFromUserInfo() {
        val selection = mutableMapOf<String, String>()

        SSH().getSelectionArgs("ssh://alice:secret@example.com".toUri(), selection)

        assertThat(selection[HostConstants.FIELD_HOST_USERNAME]).isEqualTo("alice")
        assertThat(selection.values).doesNotContain("alice:secret")
    }

    @Test
    fun agentForwardingMutationOps_doNotChangeLoadedKeys() {
        val manager = TerminalManager()
        val keyBlob = "ssh-key".toByteArray()
        manager.loadedKeypairs["existing"] = TerminalManager.KeyHolder().apply {
            openSSHPubkey = keyBlob
        }
        val ssh = SSH().apply {
            this.manager = manager
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(512)
        }.generateKeyPair()

        assertThat(ssh.addIdentity(pair, "remote", false, 0)).isFalse()
        assertThat(ssh.removeIdentity(keyBlob)).isFalse()
        assertThat(ssh.removeAllIdentities()).isFalse()
        assertThat(manager.loadedKeypairs).containsOnlyKeys("existing")
    }
}
