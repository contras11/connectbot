/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package io.shellpilot.app.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseStringsTest {
    @Test
    fun valuesJa_containsAllTranslatableDefaultStrings() {
        val defaultStrings = readStrings(resourceFile("values/strings.xml"))
            .filterValues { it.translatable }
        val japaneseStrings = readStrings(resourceFile("values-ja/strings.xml"))

        val missingKeys = defaultStrings.keys - japaneseStrings.keys

        assertTrue(
            "values-ja に不足している翻訳キー: ${missingKeys.sorted()}",
            missingKeys.isEmpty()
        )
    }

    @Test
    fun valuesJa_usesNaturalJapaneseForReleaseVisibleStrings() {
        val strings = readStrings(resourceFile("values-ja/strings.xml"))

        assertEquals("戻る", strings.getValue("button_back").text)
        assertEquals("戻る", strings.getValue("button_navigate_up").text)
        assertEquals("保存済みパスワードで認証しています…", strings.getValue("terminal_auth_saved_password").text)
        assertEquals("ポート転送が無効です", strings.getValue("portforward_disabled").text)
        assertEquals(
            "%1\$s のポート転送を読み込めませんでした: %2\$s",
            strings.getValue("error_port_forward_load_failed").text
        )
    }

    private fun readStrings(file: File): Map<String, StringEntry> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")

        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val attributes = node.attributes
                val name = attributes.getNamedItem("name").nodeValue
                val translatable = attributes.getNamedItem("translatable")?.nodeValue != "false"
                put(name, StringEntry(node.textContent, translatable))
            }
        }
    }

    private fun resourceFile(path: String): File {
        val candidates = listOf(
            File("src/main/res/$path"),
            File("app/src/main/res/$path")
        )
        return candidates.first { it.isFile }
    }

    private data class StringEntry(
        val text: String,
        val translatable: Boolean
    )
}
