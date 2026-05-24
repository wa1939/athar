package com.athar.ingestion.smsparser.ml

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import kotlinx.datetime.Instant

/**
 * On-device ML classifier interface for financial SMS — Layer 3 of the parsing stack.
 *
 * See `docs/adr/ADR-005-on-device-sms-classifier.md` for the architecture, model
 * choice (MobileBERT-NER INT8), and training plan.
 *
 * The implementation that ships in v0.1.0-beta.2 is [NoOpSmsMlClassifier] — the
 * interface is in place so Phase 3 can swap in a TFLite-backed implementation
 * without touching the parser registry or pipeline.
 */
interface SmsMlClassifier {
    /**
     * Run the model over [body] and extract financial fields. Returns null when
     * the model isn't available (e.g., classifier not bundled in this build)
     * or when nothing was extracted with sufficient confidence.
     *
     * Must be safe to call from any thread; implementations decide their own
     * dispatcher.
     */
    suspend fun classify(body: String, receivedAt: Instant): MlExtractionResult?
}

/**
 * Structured output of the ML model. Mirrors [com.athar.ingestion.smsparser.ParseResult.Success]
 * but is decoupled so the ML module can evolve independently from the parser interface.
 */
data class MlExtractionResult(
    val type: TxType,
    val amount: Money,
    val currency: String?,
    val merchant: String?,
    val counterparty: String?,
    val cardLast4: String?,
    /** Model's softmax confidence for the overall extraction. */
    val confidence: Float,
)

/**
 * Default binding when no model is bundled. Always returns null so the parser
 * falls through to template/heuristic results untouched.
 */
class NoOpSmsMlClassifier : SmsMlClassifier {
    override suspend fun classify(body: String, receivedAt: Instant): MlExtractionResult? = null
}
