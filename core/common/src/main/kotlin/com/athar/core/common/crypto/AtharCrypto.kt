package com.athar.core.common.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.
 *
 * Note: Master Brief §5.3 specifies Argon2id for KDF. We ship PBKDF2 in v1 because it's
 * in the JDK and adds no dependency; bumping to Argon2id is tracked as a follow-up to
 * lift cost-to-attacker without changing the on-disk format.
 *
 * On-disk format (all big-endian):
 *   [magic 4B "ATHR"] [version 1B = 0x01] [salt 16B] [iv 12B] [ciphertext N B] [tag 16B inside ciphertext per GCM]
 *
 * The tag is the last 16 bytes of ciphertext (GCM convention).
 */
object AtharCrypto {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12         // GCM recommended IV size
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 600_000  // OWASP 2024 recommendation for PBKDF2-HMAC-SHA256
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"

    private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'H'.code.toByte(), 'R'.code.toByte())
    private const val VERSION: Byte = 0x01

    /** Encrypt [plaintext] under [passphrase]. Returns a self-contained byte array. */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val ciphertext = Cipher.getInstance(AES_GCM).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(plaintext)

        // [magic 4][version 1][salt 16][iv 12][ciphertext]
        val out = ByteArray(MAGIC.size + 1 + SALT_BYTES + IV_BYTES + ciphertext.size)
        var off = 0
        System.arraycopy(MAGIC, 0, out, off, MAGIC.size); off += MAGIC.size
        out[off++] = VERSION
        System.arraycopy(salt, 0, out, off, SALT_BYTES); off += SALT_BYTES
        System.arraycopy(iv, 0, out, off, IV_BYTES); off += IV_BYTES
        System.arraycopy(ciphertext, 0, out, off, ciphertext.size)
        return out
    }

    /**
     * Decrypt [container] using [passphrase]. Throws on tampered ciphertext (GCM tag mismatch),
     * wrong passphrase (same: tag mismatch), or malformed header.
     */
    fun decrypt(container: ByteArray, passphrase: CharArray): ByteArray {
        require(container.size > MAGIC.size + 1 + SALT_BYTES + IV_BYTES) {
            "container too small to be a valid Athar backup"
        }
        for (i in MAGIC.indices) require(container[i] == MAGIC[i]) { "bad magic at byte $i" }
        require(container[MAGIC.size] == VERSION) {
            "unsupported backup version ${container[MAGIC.size].toInt()}"
        }
        var off = MAGIC.size + 1
        val salt = container.copyOfRange(off, off + SALT_BYTES); off += SALT_BYTES
        val iv = container.copyOfRange(off, off + IV_BYTES); off += IV_BYTES
        val ciphertext = container.copyOfRange(off, container.size)

        val key = deriveKey(passphrase, salt)
        return Cipher.getInstance(AES_GCM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
