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

package io.shellpilot.app.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptManagerの応答待ち直列化を検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptManagerTest {

    @Test
    fun concurrentPrompts_areSerializedWithoutOverwritingDeferred() = runTest {
        val manager = PromptManager()

        val first = async {
            manager.requestStringPrompt(
                instructions = null,
                hint = "first",
                isPassword = false
            )
        }
        advanceUntilIdle()

        val second = async {
            manager.requestBooleanPrompt(
                instructions = null,
                message = "second"
            )
        }
        advanceUntilIdle()

        assertTrue(manager.promptState.value is PromptRequest.StringPrompt)

        manager.respond(PromptResponse.StringResponse("ok"))
        advanceUntilIdle()

        assertEquals("ok", first.await())
        assertTrue(manager.promptState.value is PromptRequest.BooleanPrompt)

        manager.respond(PromptResponse.BooleanResponse(true))
        advanceUntilIdle()

        assertEquals(true, second.await())
        assertEquals(null, manager.promptState.value)
    }
}
