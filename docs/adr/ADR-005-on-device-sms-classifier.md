# ADR-005: On-device SMS classifier for universal bank coverage

- **Status:** Proposed (scaffolded in v0.1.0-beta.2; production model in Phase 3)
- **Date:** 2026-05-25
- **Context:** A real user audit log showed 552 failed parses against only Al Rajhi templates. Many users have multiple banks/wallets (STC Pay, Alinma, D360, Barq, Saudi National Bank, etc.), and Athar's ambition is to work for non-Saudi users too — UAE (Emirates NBD, ADCB), Egypt (CIB, NBE), Pakistan (HBL, Meezan), Turkey, India, …
- **Decision:** Add a three-layer parsing stack: (1) bank-specific regex templates, (2) universal heuristic with multi-currency + multi-language detection, (3) on-device ML classifier (this ADR).

## What problem are we solving

A regex template per bank doesn't scale. Each bank has ≥ 4 SMS formats (purchase, transfer-out, transfer-in, deposit, refund, fee, balance alert). Banks rev their formats. Maintaining ~100 templates across ~25 banks across ~10 countries is a treadmill.

The right architecture: a tiny on-device model that does **structured information extraction** on any short financial SMS, with the templates as a fast cache for the known cases.

## Three-layer architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  Incoming RawIngestEvent (sender + body)                            │
│                          │                                          │
│                          ▼                                          │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ Layer 1: Bank-specific templates (regex)                    │   │
│   │  - AlRajhiPosPurchase, AlRajhiInternalTransfer, …           │   │
│   │  - StcPayOutgoing, AlinmaStructured, …                      │   │
│   │  - O(n) regex evaluation, <5ms                              │   │
│   │  - High confidence (0.85-0.95)                              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                          │  miss                                    │
│                          ▼                                          │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ Layer 2: Universal heuristic (in v0.1.0-beta.2)             │   │
│   │  - UniversalAmountTemplate                                  │   │
│   │  - Multi-currency, multi-language (Ar/En/Es/Fr/Tr/Ur/…)     │   │
│   │  - Low confidence (≤0.55) — user always confirms            │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                          │  miss                                    │
│                          ▼                                          │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ Layer 3: On-device ML classifier (PROPOSED — Phase 3)       │   │
│   │  - MobileBERT-NER fine-tuned for financial SMS              │   │
│   │  - INT8 quantized, ~25MB asset, <100ms inference            │   │
│   │  - Extracts: amount, currency, type, merchant, counterparty │   │
│   │  - Confidence learned from training data                    │   │
│   └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

Each layer enriches the result the previous layer couldn't produce. A Layer 1 hit short-circuits; a Layer 2 hit with low confidence triggers Layer 3 if available.

## Model design (Layer 3)

### Choice: MobileBERT-NER over generative LLM

| Option | Size on disk | Inference | Fit |
|---|---|---|---|
| Gemma 2B (Q4) | ~1.5 GB | 1-3 s | Overkill for a 200-char extraction task; battery hostile |
| Phi-3.5 mini (Q4) | ~2 GB | 1-2 s | Same problem |
| **MobileBERT-NER (INT8)** | **~25 MB** | **<100 ms** | Exactly the right tool |
| DistilBERT-NER (INT8) | ~65 MB | <150 ms | Acceptable backup |
| LSTM/CRF custom | ~5 MB | <50 ms | Cheap but lower F1 vs transformer-NER |

We pick MobileBERT-NER because it's transformer-grade quality at TFLite-friendly size. Generative LLMs are wrong for this — we don't need text generation, we need token-level classification.

### Token labels

Standard BIO scheme:

```
B-AMT  I-AMT     amount tokens (e.g. "1,350.00")
B-CCY            currency token (e.g. "SAR")
B-MER  I-MER     merchant tokens (e.g. "ALDREES", "STARBUCKS")
B-CPY  I-CPY     counterparty tokens (recipient/sender of transfer)
B-TYP            type cue ("purchase", "deposit", …)
B-CARD I-CARD    card/account last-4 tokens
O                everything else
```

### Training data plan

| Source | Volume | Languages |
|---|---|---|
| Synthetic generator (template + faker) | 50,000 | Ar, En, Es, Fr, Tr, Ur, Hi |
| Public SMS-spam datasets (relabeled) | ~5,000 | En |
| Anonymized user contributions | grows over time | dependent on user base |
| Adversarial: marketing/OTP look-alikes (negatives) | 5,000 | all |

Generator outline (Python):
```python
def synthesize():
  amount = random_amount()
  ccy = choice(["SAR","AED","USD","EUR","GBP","INR","PKR","TRY",…])
  merchant = choice(merchant_list)
  template = choice(BANK_TEMPLATES)  # parameterized strings
  body = template.format(amount=amount, ccy=ccy, merchant=merchant)
  labels = align_labels(body, amount, ccy, merchant)
  return body, labels
```

Where `BANK_TEMPLATES` includes the structural variants observed across world banks (key:value lines, single-line action sentences, multilingual punctuation).

### Pipeline (Python → TFLite)

1. Fine-tune `google/mobilebert-uncased` on synthetic + real labelled data (≤ 10 epochs).
2. Evaluate held-out test set per language. Target ≥ 0.92 F1 on amount/currency, ≥ 0.85 on merchant.
3. Post-training INT8 quantization via TensorFlow's `optimize` flow.
4. Export to TFLite. Verify <100ms inference on Pixel 6 / Snapdragon 8 Gen 2.
5. Bundle as `ml/sms-classifier/src/main/assets/sms_classifier_int8.tflite`.

### Runtime integration

```kotlin
// ml:sms-classifier — new module
interface SmsMlClassifier {
  suspend fun classify(body: String): MlExtractionResult?
}

@Singleton
class TfliteSmsClassifier @Inject constructor(@ApplicationContext ctx: Context) : SmsMlClassifier {
  private val interpreter by lazy { loadInterpreter(ctx, "sms_classifier_int8.tflite") }
  override suspend fun classify(body: String): MlExtractionResult? = withContext(Dispatchers.Default) {
    val tokens = tokenize(body)
    val labels = runInterpreter(tokens)
    decodeBio(tokens, labels)  // → amount, ccy, type, merchant, counterparty
  }
}
```

The parser chain becomes:

```kotlin
// TemplateBasedSmsParser becomes HybridSmsParser
override fun parse(event: RawIngestEvent): ParseResult {
  val templateResult = tryTemplates(event)
  if (templateResult is Success && templateResult.confidence >= 0.7f) return templateResult
  val mlResult = mlClassifier.classify(event.body) ?: return templateResult
  return mergeResults(templateResult, mlResult)  // prefer ML for missing fields, regex for known structure
}
```

## Trade-offs accepted

1. **+25 MB APK size** — significant on low-end Android. Mitigation: ship as a "model pack" the user opts into ("Enable smart SMS for any bank") from Settings on first run. The bare APK stays small for users on known banks.
2. **First-launch latency** — loading the TFLite interpreter takes 200-400 ms. Mitigation: lazy-load on first SMS, warm the interpreter in a background thread on app start.
3. **Privacy implication:** the model runs entirely on-device — no transit, no telemetry — but we still need to be loud about that in the Settings UI ("AI analysis happens on your device only").
4. **Update cadence:** the model needs periodic retraining as banks rev formats. Solve via in-app model update over Wi-Fi only (encrypted, signed, user-approved). Out of scope for v1.

## What ships in v0.1.0-beta.2

Layers 1 (new bank-specific templates) and 2 (UniversalAmountTemplate) — both already added in `IngestionModule.kt`. Layer 3 is **scaffolded as a no-op `SmsMlClassifier` interface** with a stub implementation, so the codebase shape matches this ADR but no model is bundled yet.

## What's required to ship Layer 3

Estimated effort: **~3 weeks** (1 engineer)

- Week 1: data generator + label aligner; collect/clean public datasets
- Week 2: fine-tune MobileBERT-NER, evaluate, iterate; quantize + TFLite export
- Week 3: Android integration, runtime tuning, Settings toggle, telemetry-free metrics ("how many SMS in last 30 days hit Layer 3")

Tracking: see `Athar_Backlog.md` Phase 3 → ML-01..ML-08.

## References

- Tracking entry: `Athar_Backlog.md` Phase 3
- Related ADRs: `ADR-001-stack.md` (TFLite already in stack), `ADR-002-local-first.md` (no telemetry constraint)
- Audit log evidence: `docs/screenshots/sms_audit_v0.1.0-beta.1.png` — 552 failed parses
