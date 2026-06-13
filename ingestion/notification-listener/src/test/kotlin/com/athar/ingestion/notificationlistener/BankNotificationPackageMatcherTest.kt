package com.athar.ingestion.notificationlistener

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BankNotificationPackageMatcherTest {

    @Test
    fun `matches known Saudi and global finance packages`() {
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.alrajhibank.alrajhimobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.google.android.apps.walletnfcrel")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.wise.android")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.revolut.revolut")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.paypal.android.p2pmobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.squareup.cash")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.wf.wellsfargomobile")).isTrue()
    }

    @Test
    fun `does not match unrelated packages`() {
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.random.shopping")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.social.chat")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.citymapper.app")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.random.cashback")).isFalse()
    }
}
