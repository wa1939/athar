package com.athar.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MerchantSpecificityTest {

    @Test
    fun `specific merchant key prefers normalized merchant`() {
        assertThat(
            specificMerchantKey(
                merchantNormalized = "jarir bookstore",
                merchant = "Ignored Display Name",
            ),
        ).isEqualTo("jarir bookstore")
    }

    @Test
    fun `specific merchant key falls back to merchant when normalized is blank`() {
        assertThat(
            specificMerchantKey(
                merchantNormalized = "",
                merchant = "Brew Lab",
            ),
        ).isEqualTo("brew lab")
    }

    @Test
    fun `generic merchant keys are not specific`() {
        val genericKeys = listOf(
            "bank",
            "cash",
            "merchant",
            "online purchase",
            "payment",
            "purchase",
            "bank transfer",
            "كاش",
            "شراء",
            "دفع",
        )

        genericKeys.forEach { key ->
            assertThat(key.isSpecificMerchantKey()).isFalse()
        }
    }

    @Test
    fun `digit only keys are not specific`() {
        assertThat("1234".isSpecificMerchantKey()).isFalse()
        assertThat("+966-123".isSpecificMerchantKey()).isFalse()
    }
}
