package com.athar.ingestion.smsparser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NormalizeTest {

    @Test
    fun `converts Arabic-Indic digits to Latin`() {
        assertThat(Normalize.digits("شراء بمبلغ ٢٠٠٫٠٠")).isEqualTo("شراء بمبلغ 200.00")
    }

    @Test
    fun `strips POS noise from merchant`() {
        assertThat(Normalize.merchant("POS Purchase STARBUCKS 1234")).isEqualTo("starbucks 1234")
    }

    @Test
    fun `compresses whitespace`() {
        assertThat(Normalize.merchant("  STARBUCKS   1234  ")).isEqualTo("starbucks 1234")
    }
}
