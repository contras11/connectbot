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

package io.shellpilot.app.session

import io.shellpilot.app.data.entity.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShortcutExpanderのプレースホルダ展開ロジックをテストする。
 */
class ShortcutExpanderTest {

    private val testHost = Host(
        id = 1L,
        nickname = "My Server",
        protocol = "ssh",
        username = "admin",
        hostname = "example.com",
        port = 2222
    )

    // テスト用固定日時: 2026-02-24 15:30:45 UTC
    private val fixedDate = Date(1771948245000L)

    @Test
    fun expand_hostname_replacesWithHostname() {
        val result = ShortcutExpander.expand("ping {{hostname}}", testHost, fixedDate)
        assertEquals("ping example.com", result)
    }

    @Test
    fun expand_username_replacesWithUsername() {
        val result = ShortcutExpander.expand("su {{username}}", testHost, fixedDate)
        assertEquals("su admin", result)
    }

    @Test
    fun expand_port_replacesWithPort() {
        val result = ShortcutExpander.expand("echo {{port}}", testHost, fixedDate)
        assertEquals("echo 2222", result)
    }

    @Test
    fun expand_nickname_replacesWithNickname() {
        val result = ShortcutExpander.expand("echo {{nickname}}", testHost, fixedDate)
        assertEquals("echo My Server", result)
    }

    @Test
    fun expand_protocol_replacesWithProtocol() {
        val result = ShortcutExpander.expand("echo {{protocol}}", testHost, fixedDate)
        assertEquals("echo ssh", result)
    }

    @Test
    fun expand_date_replacesWithFormattedDate() {
        val result = ShortcutExpander.expand("echo {{date}}", testHost, fixedDate)
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fixedDate)
        assertEquals("echo $expected", result)
    }

    @Test
    fun expand_time_replacesWithFormattedTime() {
        val result = ShortcutExpander.expand("echo {{time}}", testHost, fixedDate)
        val expected = SimpleDateFormat("HH:mm:ss", Locale.US).format(fixedDate)
        assertEquals("echo $expected", result)
    }

    @Test
    fun expand_timestamp_replacesWithUnixTimestamp() {
        val result = ShortcutExpander.expand("echo {{timestamp}}", testHost, fixedDate)
        assertEquals("echo ${fixedDate.time / 1000}", result)
    }

    @Test
    fun expand_multiplePlaceholders_replacesAll() {
        val template = "ssh {{username}}@{{hostname}} -p {{port}}"
        val result = ShortcutExpander.expand(template, testHost, fixedDate)
        assertEquals("ssh admin@example.com -p 2222", result)
    }

    @Test
    fun expand_unknownPlaceholder_leavesAsIs() {
        val result = ShortcutExpander.expand("echo {{unknown}}", testHost, fixedDate)
        assertEquals("echo {{unknown}}", result)
    }

    @Test
    fun expand_noPlaceholders_returnsUnchanged() {
        val result = ShortcutExpander.expand("ls -la\n", testHost, fixedDate)
        assertEquals("ls -la\n", result)
    }

    @Test
    fun expand_emptyString_returnsEmpty() {
        val result = ShortcutExpander.expand("", testHost, fixedDate)
        assertEquals("", result)
    }

    @Test
    fun expand_multilineCommand_expandsInAllLines() {
        val template = "cd /home/{{username}}\nls -la\npwd\n"
        val result = ShortcutExpander.expand(template, testHost, fixedDate)
        assertEquals("cd /home/admin\nls -la\npwd\n", result)
    }

    @Test
    fun expand_duplicatePlaceholders_expandsAll() {
        val template = "{{hostname}} {{hostname}} {{hostname}}"
        val result = ShortcutExpander.expand(template, testHost, fixedDate)
        assertEquals("example.com example.com example.com", result)
    }

    @Test
    fun expand_mixedKnownAndUnknown_expandsOnlyKnown() {
        val template = "{{username}}@{{hostname}}:{{unknown_path}}"
        val result = ShortcutExpander.expand(template, testHost, fixedDate)
        assertEquals("admin@example.com:{{unknown_path}}", result)
    }

    @Test
    fun expand_defaultDateIsNotNull() {
        // now引数なしで呼び出した場合、現在時刻が使われることを確認
        val result = ShortcutExpander.expand("{{date}}", testHost)
        assertTrue("Date should be in yyyy-MM-dd format", result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
