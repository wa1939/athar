package com.athar.di

import com.athar.ingestion.notificationlistener.BankPackageFilter
import com.athar.ingestion.notificationlistener.BankNotificationPackageMatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object StoreSafeIngestionModule {

    /**
     * Bank-package filter for the storeSafe flavor's NotificationListenerService.
     * Default set covers Saudi banks/wallets and common global finance apps; user-configurable
     * in Settings later.
     */
    @Provides
    @Singleton
    fun provideBankPackageFilter(): BankPackageFilter = BankNotificationPackageMatcher
}
