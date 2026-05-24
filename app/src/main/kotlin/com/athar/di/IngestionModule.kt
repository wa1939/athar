package com.athar.di

import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.ingestion.SmsBackfillService
import com.athar.ingestion.SmsIngestionPipeline
import com.athar.ingestion.smsparser.SmsParser
import com.athar.ingestion.smsparser.TemplateBasedSmsParser
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBalanceAlertTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferOutTemplate
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
        // Order matters: balance/OTP/marketing first so they short-circuit before purchase regex.
        templates = listOf(
            AlRajhiBalanceAlertTemplate(),
            AlRajhiPurchaseTemplate(),
            AlRajhiTransferOutTemplate(),
            AlRajhiDepositTemplate(),
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
