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
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.google.android.apps.nbu.paisa.user")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.samsung.android.spay")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("uk.co.hsbc.hsbcukmobilebanking")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.barclays.android.barclaysmobilebanking")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.lloydsbank.mobilebank")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.natwest.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.usbank.mobilebanking")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.pnc.ecommerce.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.sofi.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.transferwise.android")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.payoneer.android")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.remitly.androidapp")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.anb.mobile.prod")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.bunq.android")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.nu.production.nubank")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.bbva.bbvacontigo")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.emiratesnbd.android")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.adcb.bank")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.bankfab.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.discoverfinancial.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.truist.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.commbank.netbank")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("au.com.nab.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.anz.android.gomoney")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.dbsmbanking.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.hdfcbank.mobilebanking")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.icici.bank.imobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.axisbank.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.cibc.android.mobi")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.bmo.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.bmoharris.digital")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.desjardins.mobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("ca.tangerine.clients.banking.app")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.wealthsimple.trade")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.eqbank.eqbank")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("ca.koho")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.db.pwcc.dbmobile")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("de.ingdiba.bankingapp")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.starfinanz.smob.android.sfinanzstatus")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.sabb.mobilebanking")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.urpay.consumer")).isTrue()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.es.mobily")).isTrue()
    }

    @Test
    fun `does not match unrelated packages`() {
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.random.shopping")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.social.chat")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.citymapper.app")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.random.cashback")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.random.wallet")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.alaeat.customer.android.kohokoreanbbqhouse"))
            .isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.cryart.sabbathschool")).isFalse()
        assertThat(BankNotificationPackageMatcher.isBankPackage("com.mobily.activity")).isFalse()
    }
}
