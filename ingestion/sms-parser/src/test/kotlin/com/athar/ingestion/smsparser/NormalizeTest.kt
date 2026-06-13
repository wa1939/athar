package com.athar.ingestion.smsparser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

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

    @Test
    fun `parses US grouped amount`() {
        assertThat(Normalize.amount("1,234.56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `parses European grouped amount`() {
        assertThat(Normalize.amount("1.234,56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `parses comma decimal amount`() {
        assertThat(Normalize.amount("18,50")).isEqualTo(BigDecimal("18.50"))
    }

    @Test
    fun `parses space grouped comma decimal amount`() {
        assertThat(Normalize.amount("12 345,67")).isEqualTo(BigDecimal("12345.67"))
    }
}
