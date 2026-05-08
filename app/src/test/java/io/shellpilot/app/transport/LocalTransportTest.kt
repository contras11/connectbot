package io.shellpilot.app.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTransportTest {

    @Test
    fun close_doesNotKillWhenPidIsZero() {
        val killer = RecordingKiller()
        val local = Local(killer)

        local.close()

        assertEquals(emptyList<Int>(), killer.killedPids)
        assertEquals(0, local.privateField<Int>("shellPid"))
        assertNull(local.privateField<Any?>("shellFd"))
    }

    @Test
    fun close_killsPositivePidAndClearsState() {
        val killer = RecordingKiller()
        val local = Local(killer)
        local.setPrivateField("shellPid", 1234)

        local.close()

        assertEquals(listOf(1234), killer.killedPids)
        assertEquals(0, local.privateField<Int>("shellPid"))
        assertNull(local.privateField<Any?>("shellFd"))
    }

    private class RecordingKiller : Local.Killer {
        val killedPids = mutableListOf<Int>()

        override fun killProcess(pid: Int) {
            killedPids.add(pid)
        }
    }

    private fun Any.setPrivateField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.privateField(name: String): T =
        javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.get(this) as T
        }
}
