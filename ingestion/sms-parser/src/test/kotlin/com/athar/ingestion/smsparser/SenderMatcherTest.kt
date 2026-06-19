package com.athar.ingestion.smsparser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SenderMatcherTest {

    @Test
    fun `exact sender matching ignores case and edge whitespace`() {
        val matcher = SenderMatcher.Exact("AlRajhiBank")

        assertThat(matcher.matches(" alrajhibank ")).isTrue()
    }

    @Test
    fun `any-of sender matching ignores case and edge whitespace`() {
        val matcher = SenderMatcher.AnyOf(setOf("Alinma", "STCPay"))

        assertThat(matcher.matches(" alinma ")).isTrue()
        assertThat(matcher.matches("STCPAY")).isTrue()
    }

    @Test
    fun `regex sender matching trims edge whitespace`() {
        val matcher = SenderMatcher.Regex(Regex("""notification:.+"""))

        assertThat(matcher.matches(" notification:com.bank.app ")).isTrue()
    }
}
