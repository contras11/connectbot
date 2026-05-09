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

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shellpilot.app.data.entity.Host
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android実機/エミュレータ上のRegex実装でプレースホルダ展開が落ちないことを確認する。
 */
@RunWith(AndroidJUnit4::class)
class ShortcutExpanderAndroidTest {

    @Test
    fun expandPlaceholder_worksOnAndroidRegexEngine() {
        val host = Host(
            id = 1L,
            nickname = "Review Host",
            protocol = "ssh",
            username = "contras11",
            hostname = "100.80.83.120",
            port = 22
        )

        val result = ShortcutExpander.expand("ssh {{username}}@{{hostname}} -p {{port}}", host)

        assertEquals("ssh contras11@100.80.83.120 -p 22", result)
    }
}
