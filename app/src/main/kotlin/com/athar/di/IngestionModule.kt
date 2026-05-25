package com.athar.di

import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.ingestion.SmsBackfillService
import com.athar.ingestion.SmsIngestionPipeline
import com.athar.ingestion.smsparser.GlobalBankIgnoreTemplate
import com.athar.ingestion.smsparser.SmsParser
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBalanceAlertTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBillPaymentTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiCreditCardPaymentTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiCreditLocalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDebitInternalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDebitLocalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDeclinedTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositRealTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiGenericAmountTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiInternalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiLoanInstalmentTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiOnlinePurchaseRealTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPosPurchaseRealTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPosPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiReverseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferBetweenOwnTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferOutTemplate
import com.athar.ingestion.smsparser.barq.BarqAtmWithdrawalTemplate
import com.athar.ingestion.smsparser.barq.BarqCreditTransferTemplate
import com.athar.ingestion.smsparser.barq.BarqDebitTransferTemplate
import com.athar.ingestion.smsparser.barq.BarqOnlinePurchaseTemplate
import com.athar.ingestion.smsparser.barq.BarqPosInternationalTemplate
import com.athar.ingestion.smsparser.barq.BarqRejectedTemplate
import com.athar.ingestion.smsparser.d360.D360AccountFundingTemplate
import com.athar.ingestion.smsparser.d360.D360DeclinedTemplate
import com.athar.ingestion.smsparser.d360.D360IncomingTransferTemplate
import com.athar.ingestion.smsparser.d360.D360InternationalPurchaseTemplate
import com.athar.ingestion.smsparser.d360.D360InternationalTransferTemplate
import com.athar.ingestion.smsparser.d360.D360LocalPurchaseTemplate
import com.athar.ingestion.smsparser.d360.D360OnlinePurchaseTemplate
import com.athar.ingestion.smsparser.genericbank.AlinmaTemplate
import com.athar.ingestion.smsparser.genericbank.AnbTemplate
import com.athar.ingestion.smsparser.genericbank.BarqTemplate as GenericBarqTemplate
import com.athar.ingestion.smsparser.genericbank.D360Template as GenericD360Template
import com.athar.ingestion.smsparser.genericbank.RiyadBankTemplate
import com.athar.ingestion.smsparser.genericbank.SnbTemplate
import com.athar.ingestion.smsparser.stcbank.StcBankIncomingTransferTemplate
import com.athar.ingestion.smsparser.stcbank.StcBankOnlinePurchaseTemplate
import com.athar.ingestion.smsparser.stcbank.StcBankOutgoingTransferTemplate
import com.athar.ingestion.smsparser.stcbank.StcBankPayQattahTemplate
import com.athar.ingestion.smsparser.stcbank.StcBankSarieOutwardTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayIgnoreTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayIncomingTemplate
import com.athar.ingestion.smsparser.stcpay.StcPayOutgoingTemplate
import com.athar.ingestion.smsparser.universal.UniversalAmountTemplate
import com.athar.ingestion.smsparser.user.HybridSmsParser
import com.athar.core.domain.repo.UserTemplateRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    fun provideHybridSmsParser(
        userTemplateRepository: UserTemplateRepository,
    ): HybridSmsParser {
        val parser = HybridSmsParser(builtInTemplates())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        userTemplateRepository.observeAll()
            .onEach { templates -> parser.updateUserTemplates(templates) }
            .launchIn(scope)
        return parser
    }

    @Provides
    @Singleton
    fun provideSmsParser(hybrid: HybridSmsParser): SmsParser = hybrid

    // Order matters: ignore-patterns first so OTP/promo/beneficiary short-circuit before
    // any parse attempt. Real-format templates derived from the user corpus (May 2026)
    // come next; legacy synthetic templates remain as fallbacks for variants we haven't
    // seen yet. The universal template fires last and only for known bank senders.
    private fun builtInTemplates(): List<com.athar.ingestion.smsparser.BankTemplate> = listOf(
        // Global ignore for OTPs / beneficiary admin / marketing across ALL banks. First in line.
        GlobalBankIgnoreTemplate(),

        // Al Rajhi — declined first (returns Ignored, not a failed parse).
        AlRajhiDeclinedTemplate(),
        AlRajhiBalanceAlertTemplate(),
        // Real-format templates (corpus-derived May 2026).
        AlRajhiOnlinePurchaseRealTemplate(),
        AlRajhiPosPurchaseRealTemplate(),
        AlRajhiReverseTemplate(),
        AlRajhiBillPaymentTemplate(),
        AlRajhiLoanInstalmentTemplate(),
        AlRajhiCreditCardPaymentTemplate(),
        AlRajhiCreditLocalTransferTemplate(),
        AlRajhiDebitLocalTransferTemplate(),
        AlRajhiDebitInternalTransferTemplate(),
        AlRajhiTransferBetweenOwnTemplate(),
        AlRajhiDepositRealTemplate(),
        // Synthetic legacy templates kept as fallbacks for older message shapes.
        AlRajhiPosPurchaseTemplate(),
        AlRajhiInternalTransferTemplate(),
        AlRajhiPurchaseTemplate(),
        AlRajhiTransferOutTemplate(),
        AlRajhiDepositTemplate(),
        AlRajhiGenericAmountTemplate(),

        // STC Bank (distinct from STC Pay).
        StcBankIncomingTransferTemplate(),
        StcBankOutgoingTransferTemplate(),
        StcBankSarieOutwardTemplate(),
        StcBankOnlinePurchaseTemplate(),
        StcBankPayQattahTemplate(),

        // STC Pay wallet.
        StcPayIgnoreTemplate(),
        StcPayOutgoingTemplate(),
        StcPayIncomingTemplate(),

        // D360 digital bank.
        D360DeclinedTemplate(),
        D360OnlinePurchaseTemplate(),
        D360InternationalPurchaseTemplate(),
        D360LocalPurchaseTemplate(),
        D360AccountFundingTemplate(),
        D360IncomingTransferTemplate(),
        D360InternationalTransferTemplate(),

        // Barq wallet.
        BarqRejectedTemplate(),
        BarqOnlinePurchaseTemplate(),
        BarqPosInternationalTemplate(),
        BarqAtmWithdrawalTemplate(),
        BarqDebitTransferTemplate(),
        BarqCreditTransferTemplate(),

        // Other Saudi banks/wallets using a structured "Amount: / At: / From: / To:" format.
        AlinmaTemplate(),
        GenericD360Template(),
        GenericBarqTemplate(),
        RiyadBankTemplate(),
        SnbTemplate(),
        AnbTemplate(),

        // Universal last-resort — but RESTRICTED to known bank senders only (the
        // ".+" any-sender bug that promoted random promotional shortcodes is fixed).
        UniversalAmountTemplate(),
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
