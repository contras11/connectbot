/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.shellpilot.app.util

import timber.log.Timber
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import io.shellpilot.app.data.entity.Pubkey
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PubkeyUtils {
    init {
        Ed25519Provider.insertIfNeeded()
    }

    private const val TAG = "CB.PubkeyUtils"

    const val PKCS8_START: String = "-----BEGIN PRIVATE KEY-----"
    const val PKCS8_END: String = "-----END PRIVATE KEY-----"

    // Size in bytes of salt to use.
    private const val SALT_SIZE = 8

    // Number of iterations for password hashing. PKCS#5 recommends 1000
    private const val ITERATIONS = 1000

    private val ENVELOPE_MAGIC = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 1)
    private const val ENVELOPE_SALT_SIZE = 16
    private const val ENVELOPE_IV_SIZE = 12
    private const val ENVELOPE_ITERATIONS = 210_000
    private const val ENVELOPE_KEY_BITS = 256
    private const val ENVELOPE_GCM_TAG_BITS = 128
    private const val ENVELOPE_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENVELOPE_KEY_ALGORITHM = "AES"
    private val ENVELOPE_KDFS = listOf(
        EnvelopeKdf(1.toByte(), "PBKDF2WithHmacSHA256"),
        EnvelopeKdf(2.toByte(), "PBKDF2WithHmacSHA1")
    )

    private data class EnvelopeKdf(
        val id: Byte,
        val algorithm: String
    )

    fun formatKey(key: Key): String {
        val algo = key.algorithm
        val fmt = key.format
        val encoded = key.encoded
        return "Key[algorithm=" + algo + ", format=" + fmt +
                ", bytes=" + encoded.size + "]"
    }

    @Throws(Exception::class)
    private fun encryptVersionedEnvelope(cleartext: ByteArray, secret: String): ByteArray {
        val salt = ByteArray(ENVELOPE_SALT_SIZE)
        val iv = ByteArray(ENVELOPE_IV_SIZE)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val kdf = selectEnvelopeKdf()
        val key = deriveEnvelopeKey(secret, salt, ENVELOPE_ITERATIONS, kdf)
        val cipher = Cipher.getInstance(ENVELOPE_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(ENVELOPE_GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(cleartext)

        val complete = ByteBuffer.allocate(
            ENVELOPE_MAGIC.size + 1 + Int.SIZE_BYTES + salt.size + iv.size + ciphertext.size
        )
            .put(ENVELOPE_MAGIC)
            .put(kdf.id)
            .putInt(ENVELOPE_ITERATIONS)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()

        Arrays.fill(salt, 0x00.toByte())
        Arrays.fill(iv, 0x00.toByte())
        Arrays.fill(ciphertext, 0x00.toByte())

        return complete
    }

    @Throws(Exception::class)
    private fun decryptLegacy(saltAndCiphertext: ByteArray, secret: String): ByteArray? {
        val salt = ByteArray(SALT_SIZE)
        val ciphertext = ByteArray(saltAndCiphertext.size - salt.size)

        System.arraycopy(saltAndCiphertext, 0, salt, 0, salt.size)
        System.arraycopy(saltAndCiphertext, salt.size, ciphertext, 0, ciphertext.size)

        return Encryptor.decrypt(salt, ITERATIONS, secret, ciphertext)
    }

    @Throws(Exception::class)
    private fun decryptVersionedEnvelope(envelope: ByteArray, secret: String): ByteArray {
        if (!isVersionedEnvelope(envelope)) {
            throw GeneralSecurityException("Unknown private key envelope")
        }
        val minimumSize = ENVELOPE_MAGIC.size + 1 + Int.SIZE_BYTES + ENVELOPE_SALT_SIZE + ENVELOPE_IV_SIZE + 1
        if (envelope.size < minimumSize) {
            throw GeneralSecurityException("Private key envelope is truncated")
        }

        val buffer = ByteBuffer.wrap(envelope)
        val magic = ByteArray(ENVELOPE_MAGIC.size)
        buffer.get(magic)
        val kdf = envelopeKdfById(buffer.get())
        val iterations = buffer.int
        if (iterations <= 0) {
            throw GeneralSecurityException("Invalid private key envelope KDF")
        }
        val salt = ByteArray(ENVELOPE_SALT_SIZE)
        val iv = ByteArray(ENVELOPE_IV_SIZE)
        buffer.get(salt)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        try {
            val key = deriveEnvelopeKey(secret, salt, iterations, kdf)
            val cipher = Cipher.getInstance(ENVELOPE_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(ENVELOPE_GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } finally {
            Arrays.fill(salt, 0x00.toByte())
            Arrays.fill(iv, 0x00.toByte())
            Arrays.fill(ciphertext, 0x00.toByte())
        }
    }

    private fun isVersionedEnvelope(encoded: ByteArray): Boolean {
        if (encoded.size < ENVELOPE_MAGIC.size) {
            return false
        }
        return ENVELOPE_MAGIC.indices.all { encoded[it] == ENVELOPE_MAGIC[it] }
    }

    private fun selectEnvelopeKdf(): EnvelopeKdf {
        return ENVELOPE_KDFS.firstOrNull { kdf ->
            runCatching { SecretKeyFactory.getInstance(kdf.algorithm) }.isSuccess
        } ?: throw GeneralSecurityException("No supported private key KDF")
    }

    private fun envelopeKdfById(id: Byte): EnvelopeKdf {
        return ENVELOPE_KDFS.firstOrNull { it.id == id }
            ?: throw GeneralSecurityException("Unsupported private key KDF")
    }

    private fun deriveEnvelopeKey(
        secret: String,
        salt: ByteArray,
        iterations: Int,
        kdf: EnvelopeKdf
    ): SecretKeySpec {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, ENVELOPE_KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance(kdf.algorithm).generateSecret(spec).encoded
        return try {
            SecretKeySpec(keyBytes, ENVELOPE_KEY_ALGORITHM)
        } finally {
            spec.clearPassword()
            Arrays.fill(keyBytes, 0x00.toByte())
        }
    }

    @Throws(Exception::class)
    fun getEncodedPrivate(pk: PrivateKey, secret: String?): ByteArray {
        val encoded = pk.encoded
        if (secret == null || secret.isEmpty()) {
            return encoded
        }
        // 新規保存は改ざん検出できる AEAD envelope に寄せ、旧形式は読み取り互換だけ残す。
        return try {
            encryptVersionedEnvelope(encoded, secret)
        } finally {
            Arrays.fill(encoded, 0x00.toByte())
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePrivate(encoded: ByteArray?, keyType: String?): PrivateKey? {
        val privKeySpec = PKCS8EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePrivate(privKeySpec)
    }

    @Throws(Exception::class)
    fun decodePrivate(encoded: ByteArray, keyType: String?, secret: String?): PrivateKey? {
        return if (secret != null && secret.isNotEmpty()) {
            val cleartext = if (isVersionedEnvelope(encoded)) {
                decryptVersionedEnvelope(encoded, secret)
            } else {
                decryptLegacy(encoded, secret)
            }
            try {
                decodePrivate(cleartext, keyType)
            } finally {
                if (cleartext != null) {
                    Arrays.fill(cleartext, 0x00.toByte())
                }
            }
        } else {
            decodePrivate(encoded, keyType)
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePublic(encoded: ByteArray?, keyType: String?): PublicKey {
        val pubKeySpec = X509EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePublic(pubKeySpec)
    }

    @Throws(BadPasswordException::class)
    fun convertToKeyPair(pubkey: Pubkey, password: String?): KeyPair? {
        if ("IMPORTED" == pubkey.type) {
            // load specific key using pem format
            try {
                return PEMDecoder.decode(
                    String(pubkey.privateKey!!, charset("UTF-8")).toCharArray(),
                    password
                )
            } catch (e: Exception) {
                Timber.e(e, "Cannot decode imported key")
                throw BadPasswordException()
            }
        } else {
            // load using internal generated format
            try {
                val privKey = decodePrivate(pubkey.privateKey!!, pubkey.type, password)
                val pubKey = decodePublic(pubkey.publicKey, pubkey.type)
                Timber.d("Unlocked key " + formatKey(pubKey))

                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Timber.e(e, "Cannot decode pubkey from database")
                throw BadPasswordException()
            }
        }
    }

    class BadPasswordException : Exception()
}
