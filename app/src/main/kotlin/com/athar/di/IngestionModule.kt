package com.athar.di

import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.ingestion.SmsBackfillService
import com.athar.ingestion.SmsIngestionPipeline
import com.athar.ingestion.smsparser.SmsParser
import com.athar.ingestion.smsparser.TemplateBasedSmsParser
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBalanceAlertTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiGenericAmountTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiInternalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPosPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferOutTemplate
import com.athar.ingestion.smsparser.genericbank.AlinmaTemplate
import com.athar.ingestion.smsparser.genericbank.AnbTemplate
import com.athar.ingestion.smsparser.genericbank.BarqTemplate
import com.athar.ingestion.smsparser.genericbank.D360Template
import com.athar.ingestion.smsparser.genericbank.RiyadBankTemplate
import com.athar.ingestion.smsparser.genericbank.SnbTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayIgnoreTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayIncomingTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayOutgoingTemplate
import com.athar.ingestion.smsparser.universal.UniversalAmountTemplate
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object IngestionModule {

    @Provides
    @Singleton
    fun provideSmsParser(): SmsParser = TemplateBasedSmsParser(
        // Order matters: balance/OTP/marketing first so they short-circuit before
        // the financial regex. The more specific structural templates
        // (PoS purchase, Internal Transfer) come before the older free-form ones.
        // The generic-amount Al Rajhi template is the LAST Al Rajhi attempt — a
        // low-confidence fallback so legitimate-but-unrecognized SMS still land
        // in the pending tray for the user to confirm/categorize.
        templates = listOf(
            // Al Rajhi — most users' primary bank, so first.
            AlRajhiBalanceAlertTemplate(),
            AlRajhiPosPurchaseTemplate(),         // real format: "PoS purchase / Amount:X SAR"
            AlRajhiInternalTransferTemplate(),    // real format: "Debit Internal Transfer / From / To"
            AlRajhiPurchaseTemplate(),            // legacy "Purchase SAR X" / Arabic equivalent
            AlRajhiTransferOutTemplate(),         // legacy "Transfer SAR X to Y"
            AlRajhiDepositTemplate(),             // legacy "Deposit SAR X from Y"
            AlRajhiGenericAmountTemplate(),       // last-resort: any "Amount: X SAR" from Al Rajhi
            // STC Pay wallet.
            StcPayIgnoreTemplate(),
            StcPayOutgoingTemplate(),
            StcPayIncomingTemplate(),
            // Other Saudi banks/wallets using a structured "Amount: / At: / From: / To:" format.
            // Real-world samples needed in `corpus/sms/<bank>.txt` to tighten patterns.
            AlinmaTemplate(),
            D360Template(),
            BarqTemplate(),
            RiyadBankTemplate(),
            SnbTemplate(),
            AnbTemplate(),
            // Universal currency-and-language-agnostic last resort. Triggers for
            // ANY sender so users with non-Saudi banks/wallets still see something
            // in the pending tray. Confidence is capped at 0.55 — user always confirms.
            // To be replaced by an on-device ML classifier (see ADR-005).
            UniversalAmountTemplate(),
        ),
    )

    /**
     * Wires SMS/notification receivers (in modules with Android deps) to the ingestion
     * pipeline (in app). The dispatcher runs on a singleton supervisor scope so a receiver
     * can return immediately while parsing/persistence happens off-thread.
     */
    @Provides
    @Singleton
    fun provideRawIngestDispatcher(pipeline: SmsIngestionPipeline): RawIngestDispatcher {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return RawIngestDispatcher { event ->
            scope.launch { pipeline.process(event) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class IngestionBindingsModule {
    @Binds @Singleton
    abstract fun bindSmsBackfillTrigger(impl: SmsBackfillService): SmsBackfillTrigger
}
