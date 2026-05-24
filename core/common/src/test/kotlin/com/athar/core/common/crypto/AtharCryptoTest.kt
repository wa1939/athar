package com.athar.core.common.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.AEADBadTagException

class AtharCryptoTest {

    @Test
    fun `roundtrips a simple payload`() {
        val payload = "hello أثر 2026".toByteArray()
        val container = AtharCrypto.encrypt(payload, "correct horse battery staple".toCharArray())
        val decrypted = AtharCrypto.decrypt(container, "correct horse battery staple".toCharArray())
        assertThat(String(decrypted)).isEqualTo("hello أثر 2026")
    }

    @Test
    fun `wrong passphrase fails with tag mismatch`() {
        val container = AtharCrypto.encrypt("data".toByteArray(), "right".toCharArray())
        assertThrows<AEADBadTagException> {
            AtharCrypto.decrypt(container, "wrong".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext fails`() {
        val container = AtharCrypto.encrypt("data".toByteArray(), "pass".toCharArray())
        // Flip the last byte (inside the GCM tag).
        container[container.lastIndex] = (container[container.lastIndex].toInt() xor 0xFF).toByte()
        assertThrows<AEADBadTagException> {
            AtharCrypto.decrypt(container, "pass".toCharArray())
        }
    }

    @Test
    fun `bad magic throws`() {
        val container = AtharCrypto.encrypt("data".toByteArray(), "pass".toCharArray())
        container[0] = 'X'.code.toByte()
        assertThrows<IllegalArgumentException> {
            AtharCrypto.decrypt(container, "pass".toCharArray())
        }
    }

    @Test
    fun `two encryptions of same input differ`() {
        // Salt + IV are random → ciphertexts must not match (no deterministic encryption).
        val a = AtharCrypto.encrypt("x".toByteArray(), "p".toCharArray())
        val b = AtharCrypto.encrypt("x".toByteArray(), "p".toCharArray())
        assertThat(a.toList()).isNotEqualTo(b.toList())
    }
}
