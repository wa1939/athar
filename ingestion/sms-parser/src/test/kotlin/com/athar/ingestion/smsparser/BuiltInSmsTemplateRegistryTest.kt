package com.athar.ingestion.smsparser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuiltInSmsTemplateRegistryTest {

    @Test
    fun `registry pins ignore first notification early and universal fallback last`() {
        val ids = BuiltInSmsTemplateRegistry.templates().map { it.id }

        assertThat(ids.first()).isEqualTo("global-bank-ignore")
        assertThat(ids[1]).isEqualTo("generic-bank-notification")
        assertThat(ids.last()).isEqualTo("universal-amount")
    }

    @Test
    fun `registry includes wallet and structured-bank templates used by production audits`() {
        val ids = BuiltInSmsTemplateRegistry.templates().map { it.id }

        assertThat(ids).contains("stcpay-ignore")
        assertThat(ids).contains("stcpay-outgoing")
        assertThat(ids).contains("stcpay-incoming")
        assertThat(ids).contains("riyad-bank-structured")
        assertThat(ids).contains("anb-structured")
        assertThat(ids).contains("barq-structured")
    }
}
