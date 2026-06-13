package com.athar.ingestion.notificationlistener

/**
 * Default package allow-list for the Play-Store-safe notification ingestion path.
 *
 * Exact package names are used where they are well-known. Brand-keyword matching covers package
 * suffix variants across Saudi banks and common global finance apps without exposing notification
 * ingestion to arbitrary apps.
 */
object BankNotificationPackageMatcher : BankPackageFilter {
    private val exactPackages = setOf(
        "com.alrajhibank.AlRajhiMobile",
        "com.alrajhibank.alrajhimobile",
        "com.google.android.apps.walletnfcrel",
    )

    private val packageKeywords = listOf(
        "alrajhi",
        "stcpay",
        "stcbank",
        "d360",
        "barq",
        "alinma",
        "riyad",
        "snb",
        "alahli",
        "anb",
        "albilad",
        "bsf",
        "saib",
        "jazira",
        "wise",
        "revolut",
        "chase",
        "capitalone",
        "mercury",
        "monzo",
        "n26",
        "starling",
    )

    override fun isBankPackage(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed in exactPackages) return true
        val lower = trimmed.lowercase()
        return packageKeywords.any { lower.contains(it) }
    }
}
