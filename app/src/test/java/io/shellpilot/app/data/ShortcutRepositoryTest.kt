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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import io.shellpilot.app.data.entity.Shortcut
import io.shellpilot.app.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ShortcutRepositoryのJSON保存/読込ロジックをテストする。
 * org.json (Android SDK) を使用するためRobolectricで実行する。
 * テンポラリディレクトリを使用して実際のファイルI/Oを検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShortcutRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var repository: ShortcutRepository

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "shortcut_test_${System.nanoTime()}")
        tempDir.mkdirs()

        context = mock()
        whenever(context.filesDir).thenReturn(tempDir)

        repository = ShortcutRepository(context, dispatchers)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun loadAll_firstRun_returnsDefaults() = runTest(testDispatcher) {
        val shortcuts = repository.loadAll()

        assertTrue("Should return default shortcuts", shortcuts.isNotEmpty())
        assertTrue("Default 'ls -la' should exist", shortcuts.any { it.label == "ls -la" })
        assertEquals("Control shortcuts should be first", "Ctrl+C", shortcuts[0].label)
    }

    @Test
    fun loadAll_firstRun_createsJsonFile() = runTest(testDispatcher) {
        repository.loadAll()

        val jsonFile = File(tempDir, ShortcutRepository.FILE_NAME)
        assertTrue("JSON file should be created", jsonFile.exists())

        val content = jsonFile.readText()
        assertTrue("File should contain 'shortcuts' key", content.contains("\"shortcuts\""))
        assertTrue("File should contain 'version' key", content.contains("\"version\""))
    }

    @Test
    fun save_addsNewShortcut() = runTest(testDispatcher) {
        repository.loadAll()

        val newShortcut = Shortcut(
            label = "deploy",
            command = "make deploy\n",
            hostId = null,
            order = 10
        )
        repository.save(newShortcut)

        val all = repository.shortcuts.value
        assertTrue("New shortcut should be added", all.any { it.label == "deploy" })
    }

    @Test
    fun save_updatesExistingShortcut() = runTest(testDispatcher) {
        repository.loadAll()

        val original = repository.shortcuts.value.first()
        val updated = original.copy(label = "updated_label")
        repository.save(updated)

        val all = repository.shortcuts.value
        assertEquals("Label should be updated", "updated_label",
            all.first { it.id == original.id }.label)
    }

    @Test
    fun save_persistsToFile() = runTest(testDispatcher) {
        repository.loadAll()

        val shortcut = Shortcut(label = "persist_test", command = "echo test\n")
        repository.save(shortcut)

        // 新しいリポジトリインスタンスでファイルから再読込
        val repository2 = ShortcutRepository(context, dispatchers)
        val reloaded = repository2.loadAll()

        assertTrue("Persisted shortcut should be found after reload",
            reloaded.any { it.label == "persist_test" })
    }

    @Test
    fun delete_removesShortcut() = runTest(testDispatcher) {
        repository.loadAll()

        val first = repository.shortcuts.value.first()
        val sizeBefore = repository.shortcuts.value.size

        repository.delete(first.id)

        assertEquals("Size should decrease by 1", sizeBefore - 1,
            repository.shortcuts.value.size)
        assertTrue("Deleted shortcut should not exist",
            repository.shortcuts.value.none { it.id == first.id })
    }

    @Test
    fun getForHost_returnsGlobalAndHostSpecific() = runTest(testDispatcher) {
        repository.loadAll()

        val hostShortcut = Shortcut(
            label = "host_specific",
            command = "systemctl restart app\n",
            hostId = 42L,
            order = 0
        )
        repository.save(hostShortcut)

        val forHost = repository.getForHost(42L)

        assertTrue("Should include global shortcuts",
            forHost.any { it.hostId == null })
        assertTrue("Should include host-specific shortcut",
            forHost.any { it.hostId == 42L })
    }

    @Test
    fun getForHost_excludesOtherHostShortcuts() = runTest(testDispatcher) {
        repository.loadAll()

        val hostShortcut = Shortcut(
            label = "other_host",
            command = "echo other\n",
            hostId = 99L,
            order = 0
        )
        repository.save(hostShortcut)

        val forHost = repository.getForHost(42L)

        assertTrue("Should not include shortcuts for other hosts",
            forHost.none { it.hostId == 99L })
    }

    @Test
    fun getForHost_ordersGlobalFirst() = runTest(testDispatcher) {
        repository.replaceAll(emptyList())

        repository.save(Shortcut(label = "host", command = "a\n", hostId = 1L, order = 0))
        repository.save(Shortcut(label = "global", command = "b\n", hostId = null, order = 0))

        val forHost = repository.getForHost(1L)

        assertEquals("Global should come first", "global", forHost.first().label)
        assertEquals("Host-specific should come second", "host", forHost.last().label)
    }

    @Test
    fun replaceAll_replacesEntireList() = runTest(testDispatcher) {
        repository.loadAll()

        val newList = listOf(
            Shortcut(label = "a", command = "a\n", order = 0),
            Shortcut(label = "b", command = "b\n", order = 1)
        )
        repository.replaceAll(newList)

        assertEquals("Should have exactly 2 shortcuts", 2,
            repository.shortcuts.value.size)
        assertEquals("First should be 'a'", "a", repository.shortcuts.value[0].label)
        assertEquals("Second should be 'b'", "b", repository.shortcuts.value[1].label)
    }

    @Test
    fun shortcutJsonRoundTrip_preservesAllFields() {
        val original = Shortcut(
            id = "test-uuid-123",
            label = "Test Label",
            command = "echo {{hostname}}\nls -la\n",
            hostId = 42L,
            order = 5
        )

        val json = original.toJson()
        val restored = Shortcut.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.label, restored.label)
        assertEquals(original.command, restored.command)
        assertEquals(original.hostId, restored.hostId)
        assertEquals(original.templateKey, restored.templateKey)
        assertEquals(original.order, restored.order)
    }

    @Test
    fun shortcutJsonRoundTrip_templateKey_preserved() {
        val original = Shortcut(
            label = "Ctrl+C",
            command = "\u0003",
            category = "control",
            templateKey = "control:ctrl-c"
        )

        val restored = Shortcut.fromJson(original.toJson())

        assertEquals("control:ctrl-c", restored.templateKey)
        assertEquals("control", restored.category)
    }

    @Test
    fun shortcutJsonRoundTrip_nullHostId_preserved() {
        val original = Shortcut(
            label = "Global",
            command = "pwd\n",
            hostId = null
        )

        val json = original.toJson()
        val restored = Shortcut.fromJson(json)

        assertEquals("hostId should remain null", null, restored.hostId)
    }

    @Test
    fun shortcutJsonFromLegacyWithoutTemplateKey_keepsTemplateKeyNull() {
        val json = Shortcut(
            label = "legacy",
            command = "echo legacy\n",
            category = "general"
        ).toJson().apply {
            remove("templateKey")
        }

        val restored = Shortcut.fromJson(json)

        assertNull(restored.templateKey)
    }

    @Test
    fun syncOfficialTemplates_addsMissingTemplatesAndKeepsCustom() = runTest(testDispatcher) {
        val custom = Shortcut(label = "custom deploy", command = "make deploy\n")
        repository.replaceAll(listOf(custom))

        val result = repository.syncOfficialTemplates()

        val all = repository.shortcuts.value
        assertTrue("Official templates should be added", result.added > 0)
        assertTrue("Custom shortcut should be preserved", all.any { it.id == custom.id })
        assertTrue("Ctrl+C template should exist", all.any { it.templateKey == "control:ctrl-c" })
        assertTrue("Codex exec template should exist", all.any { it.templateKey == "codex:exec" })
    }

    @Test
    fun syncOfficialTemplates_updatesKnownTemplateWithoutChangingId() = runTest(testDispatcher) {
        val stale = Shortcut(
            id = "fixed-id",
            label = "codex" + " -q",
            command = "codex" + " -q \"",
            category = "codex",
            templateKey = "codex:exec",
            order = 7
        )
        repository.replaceAll(listOf(stale))

        val result = repository.syncOfficialTemplates()

        val updated = repository.shortcuts.value.first { it.id == "fixed-id" }
        assertTrue("Known template should be updated", result.updated > 0)
        assertEquals("codex exec", updated.label)
        assertEquals("codex exec \"", updated.command)
        assertEquals(7, updated.order)
    }

    @Test
    fun syncOfficialTemplates_mapsLegacyTemplateAndRemovesDuplicateOfficialTemplate() = runTest(testDispatcher) {
        val legacy = Shortcut(
            id = "legacy-id",
            label = "codex" + " -q",
            command = "codex" + " -q \"",
            category = "codex",
            order = 1
        )
        val duplicate = Shortcut(
            id = "duplicate-id",
            label = "codex exec",
            command = "codex exec \"",
            category = "codex",
            templateKey = "codex:exec",
            order = 2
        )
        repository.replaceAll(listOf(legacy, duplicate))

        repository.syncOfficialTemplates()

        val codexExec = repository.shortcuts.value.filter { it.templateKey == "codex:exec" }
        assertEquals("Duplicate official template should be removed", 1, codexExec.size)
        assertEquals("legacy-id", codexExec.single().id)
        assertEquals("codex exec", codexExec.single().label)
    }
}
