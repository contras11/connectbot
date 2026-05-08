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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Modern prompt manager using Kotlin coroutines instead of semaphores and blocking.
 * Manages prompts for password, host verification, etc.
 */
class PromptManager {
    private val _promptState = MutableStateFlow<PromptRequest?>(null)
    val promptState: StateFlow<PromptRequest?> = _promptState.asStateFlow()

    private val promptMutex = Mutex()
    private val deferredLock = Any()
    private var currentDeferred: CompletableDeferred<PromptResponse>? = null

    /**
     * Request a boolean prompt (yes/no dialog)
     */
    suspend fun requestBooleanPrompt(
        instructions: String?,
        message: String
    ): Boolean {
        return awaitPrompt(
            request = PromptRequest.BooleanPrompt(
                instructions = instructions,
                message = message
            )
        ) { response ->
            (response as? PromptResponse.BooleanResponse)?.value ?: false
        }
    }

    /**
     * Request a string prompt (text input dialog)
     */
    suspend fun requestStringPrompt(
        instructions: String?,
        hint: String?,
        isPassword: Boolean = false
    ): String? {
        return awaitPrompt(
            request = PromptRequest.StringPrompt(
                instructions = instructions,
                hint = hint,
                isPassword = isPassword
            )
        ) { response ->
            (response as? PromptResponse.StringResponse)?.value
        }
    }

    /**
     * Request biometric authentication for a key stored in Android Keystore
     */
    suspend fun requestBiometricAuth(
        keyNickname: String,
        keystoreAlias: String
    ): Boolean {
        return awaitPrompt(
            request = PromptRequest.BiometricPrompt(
                keyNickname = keyNickname,
                keystoreAlias = keystoreAlias
            )
        ) { response ->
            (response as? PromptResponse.BiometricResponse)?.success ?: false
        }
    }

    /**
     * Request host key fingerprint verification prompt
     */
    suspend fun requestHostKeyFingerprintPrompt(
        hostname: String,
        keyType: String,
        keySize: Int,
        serverHostKey: ByteArray,
        randomArt: String,
        bubblebabble: String,
        sha256: String,
        md5: String
    ): Boolean {
        return awaitPrompt(
            request = PromptRequest.HostKeyFingerprintPrompt(
                hostname = hostname,
                keyType = keyType,
                keySize = keySize,
                serverHostKey = serverHostKey,
                randomArt = randomArt,
                bubblebabble = bubblebabble,
                sha256 = sha256,
                md5 = md5
            )
        ) { response ->
            (response as? PromptResponse.BooleanResponse)?.value ?: false
        }
    }

    /**
     * 変更理由: 同時promptでcurrentDeferredが上書きされないよう、表示と応答を直列化する。
     */
    private suspend fun <T> awaitPrompt(
        request: PromptRequest,
        mapResponse: (PromptResponse) -> T
    ): T = promptMutex.withLock {
        val deferred = CompletableDeferred<PromptResponse>()
        synchronized(deferredLock) {
            currentDeferred?.cancel()
            currentDeferred = deferred
        }

        _promptState.update { request }

        try {
            mapResponse(deferred.await())
        } finally {
            synchronized(deferredLock) {
                if (currentDeferred == deferred) {
                    currentDeferred = null
                }
            }
            _promptState.update { null }
        }
    }

    /**
     * Respond to the current prompt
     */
    fun respond(response: PromptResponse) {
        synchronized(deferredLock) {
            currentDeferred?.complete(response)
            currentDeferred = null
        }
    }

    /**
     * Cancel the current prompt
     */
    fun cancelPrompt() {
        synchronized(deferredLock) {
            currentDeferred?.cancel()
            currentDeferred = null
        }
        _promptState.update { null }
    }
}

/**
 * Represents a prompt request
 */
sealed class PromptRequest {
    data class BooleanPrompt(
        val instructions: String?,
        val message: String
    ) : PromptRequest()

    data class StringPrompt(
        val instructions: String?,
        val hint: String?,
        val isPassword: Boolean
    ) : PromptRequest()

    data class BiometricPrompt(
        val keyNickname: String,
        val keystoreAlias: String
    ) : PromptRequest()

    data class HostKeyFingerprintPrompt(
        val hostname: String,
        val keyType: String,
        val keySize: Int,
        val serverHostKey: ByteArray,
        val randomArt: String,
        val bubblebabble: String,
        val sha256: String,
        val md5: String
    ) : PromptRequest() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HostKeyFingerprintPrompt) return false
            return hostname == other.hostname &&
                keyType == other.keyType &&
                keySize == other.keySize &&
                serverHostKey.contentEquals(other.serverHostKey) &&
                randomArt == other.randomArt &&
                bubblebabble == other.bubblebabble &&
                sha256 == other.sha256 &&
                md5 == other.md5
        }

        override fun hashCode(): Int {
            var result = hostname.hashCode()
            result = 31 * result + keyType.hashCode()
            result = 31 * result + keySize
            result = 31 * result + serverHostKey.contentHashCode()
            result = 31 * result + randomArt.hashCode()
            result = 31 * result + bubblebabble.hashCode()
            result = 31 * result + sha256.hashCode()
            result = 31 * result + md5.hashCode()
            return result
        }
    }
}

/**
 * Represents a prompt response
 */
sealed class PromptResponse {
    data class BooleanResponse(val value: Boolean) : PromptResponse()
    data class StringResponse(val value: String?) : PromptResponse()
    data class BiometricResponse(val success: Boolean) : PromptResponse()
}
