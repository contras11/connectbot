package io.shellpilot.app.service

import io.shellpilot.app.data.entity.Host
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TerminalManagerTest {

    @Test
    fun bridgeKey_usesPersistentHostIdBeforeMutableFields() {
        val first = host(id = 10L, nickname = "old")
        val renamed = host(id = 10L, nickname = "new")

        assertThat(TerminalManager.bridgeKey(first))
            .isEqualTo(TerminalManager.bridgeKey(renamed))
    }

    @Test
    fun bridgeKey_usesTemporaryNegativeIdBeforeNickname() {
        val first = host(id = -3L, nickname = "same")
        val second = host(id = -4L, nickname = "same")

        assertThat(TerminalManager.bridgeKey(first))
            .isNotEqualTo(TerminalManager.bridgeKey(second))
    }

    @Test
    fun bridgeKey_fallsBackToNicknameBeforeTemporaryIdAssignment() {
        val first = host(id = 0L, nickname = "quick")
        val second = host(id = 0L, nickname = "quick")

        assertThat(TerminalManager.bridgeKey(first))
            .isEqualTo(TerminalManager.bridgeKey(second))
    }

    private fun host(id: Long, nickname: String) = Host(
        id = id,
        nickname = nickname,
        protocol = "ssh",
        username = "user",
        hostname = "example.com",
        port = 22
    )
}
