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

package io.shellpilot.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Arrays

class PubkeyUtilsTest {

    @Test
    fun getEncodedPrivate_UsesVersionedEnvelopeAndDecrypts() {
        val keyPair = generateRsaKeyPair()

        val encrypted = PubkeyUtils.getEncodedPrivate(keyPair.private, "secret")
        val decrypted = PubkeyUtils.decodePrivate(encrypted, "RSA", "secret")

        assertFalse("新規暗号形式は平文PKCS#8と異なる必要があります", encrypted.contentEquals(keyPair.private.encoded))
        assertArrayEquals(keyPair.private.encoded, decrypted?.encoded)
    }

    @Test
    fun decodePrivate_ReadsLegacyAesCbcPayload() {
        val keyPair = generateRsaKeyPair()
        val legacyEncrypted = legacyEncrypt(keyPair.private.encoded, "secret")

        val decrypted = PubkeyUtils.decodePrivate(legacyEncrypted, "RSA", "secret")

        assertArrayEquals(keyPair.private.encoded, decrypted?.encoded)
    }

    @Test
    fun decodePrivate_DetectsTamperedVersionedEnvelope() {
        val keyPair = generateRsaKeyPair()
        val encrypted = PubkeyUtils.getEncodedPrivate(keyPair.private, "secret")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        try {
            PubkeyUtils.decodePrivate(encrypted, "RSA", "secret")
            fail("改ざんされた秘密鍵 envelope は復号に失敗する必要があります")
        } catch (_: Exception) {
            // 期待通り。AEADタグ検証または鍵復元で失敗する。
        }
    }

    private fun generateRsaKeyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun legacyEncrypt(cleartext: ByteArray, secret: String): ByteArray {
        val salt = ByteArray(8)
        val ciphertext = Encryptor.encrypt(salt, 1000, secret, cleartext)
            ?: error("legacy encryption failed")
        val complete = salt + ciphertext
        Arrays.fill(salt, 0x00.toByte())
        Arrays.fill(ciphertext, 0x00.toByte())
        return complete
    }
}
