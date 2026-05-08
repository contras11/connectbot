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

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileOrderRepositoryTest {

    private lateinit var repository: ProfileOrderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        repository = ProfileOrderRepository(context)
    }

    @Test
    fun saveOrder_updatesOrderFlow() {
        repository.saveOrder(listOf("git", null, "codex"))

        assertEquals("git", repository.order.value.first())
        assertEquals(repository.getOrder(), repository.order.value)
        assertTrue(repository.order.value.contains(null))
        assertTrue(repository.order.value.contains("codex"))
    }

    @Test
    fun setProfileVisible_updatesHiddenFlow() {
        repository.setProfileVisible("docker", visible = false)

        assertTrue(repository.hiddenProfileIds.value.contains("docker"))
        assertEquals(repository.getHiddenProfileIds(), repository.hiddenProfileIds.value)

        repository.setProfileVisible("docker", visible = true)

        assertTrue(repository.hiddenProfileIds.value.none { it == "docker" })
    }
}
