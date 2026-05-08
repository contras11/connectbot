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

package io.shellpilot.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun selectedSettingsCategoryFromSavedName_UnknownName_FallsBackToTop() {
        assertNull(selectedSettingsCategoryFromSavedName("REMOVED_CATEGORY"))
    }

    @Test
    fun selectedSettingsCategoryFromSavedName_KnownName_RestoresCategory() {
        assertEquals(
            SettingsCategory.SHORTCUTS,
            selectedSettingsCategoryFromSavedName("SHORTCUTS")
        )
    }
}
