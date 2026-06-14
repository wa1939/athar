package com.athar.core.domain.repo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MerchantBulkAiPromptTest {

    @Test
    fun `prompt preserves importer-required columns and output contract`() {
        assertThat(merchantBulkAiPrompt).contains(
            "id, stable_key, source_ref_id, merchant, merchant_normalized, merchant_group_count, category_options",
        )
        assertThat(merchantBulkAiPrompt).contains("amount, currency, type, status, date, raw_body, category_id")
        assertThat(merchantBulkAiPrompt).contains("same headers and rows in the same order")
        assertThat(merchantBulkAiPrompt).contains("Do not change category_options or any other column")
        assertThat(merchantBulkAiPrompt).contains("Private exports may leave raw_body blank")
        assertThat(merchantBulkAiPrompt).contains("cat-coffee=Coffee / قهوة")
    }

    @Test
    fun `prompt tells ai to avoid unsafe category guesses`() {
        assertThat(merchantBulkAiPrompt).contains("compatible with the row's type")
        assertThat(merchantBulkAiPrompt).contains("type=TRANSFER")
        assertThat(merchantBulkAiPrompt).contains("leave category_id blank")
        assertThat(merchantBulkAiPrompt).contains("Do not guess")
    }
}
