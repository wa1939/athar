package com.athar.ingestion.smsparser

/**
 * The single source of truth for which SMS senders Athar treats as "a bank or wallet".
 *
 * Any sender NOT in this set is invisible to the parser pipeline — promotional SMS
 * from random shortcodes ("Earn SAR 10,000 today!") cannot accidentally become
 * transactions, because the registry filters them out before any template runs.
 *
 * This list is intentionally exact-match (case-sensitive) on the sender string the
 * SMS provider hands to the BroadcastReceiver. Sender labels are stable in KSA —
 * banks register their alphanumeric sender IDs with CITC and rarely change them.
 *
 * If a user receives transaction SMS from an unrecognised bank, they can add it
 * via Settings → User Templates; that path bypasses this allow-list because the
 * user is explicitly opting in to that sender.
 */
object KnownBankSenders {

    /** Saudi banks and wallets we ship native templates for. Sender strings exactly as they arrive. */
    val builtIn: Set<String> = setOf(
        // Al Rajhi
        "AlRajhiBank", "AlRajhi Bank", "AlRajhi", "ALRAJHIBANK", "Al Rajhi Bank",
        // STC Bank / STC Pay
        "STC Bank", "STCBank", "STCPay", "STC Pay", "STCpay", "STC-Pay",
        // D360 (Riyad-affiliated digital bank)
        "D360 Bank", "D360", "D360Bank",
        // Barq
        "barq app", "Barq", "BarqApp", "BARQ",
        // Other major SA banks
        "Alinma", "AlinmaBank", "Alinma Bank", "ALINMA",
        "Riyad Bank", "RiyadBank", "RIYADBANK",
        "SNB", "SNB Bank", "NCB", "ALAHLI", "AlAhliBank",
        "ANB", "ANBBank", "AlArabi",
        "SAMBA", "SambaBank",
        "Albilad", "BankAlbilad", "ALBILAD",
        "BSF", "Banque Saudi Fransi", "BSFBank",
        "GIB", "Saudi Investment Bank", "SAIB",
        "AlJazira", "BankAlJazira", "BAJ",
        // Wallets
        "urpay", "Urpay",
        "Mobily Pay", "MobilyPay",
        "Halalah", "HALALAH",
    )

    /**
     * Hard-block list. These senders are KNOWN to be marketing affiliates of banks,
     * loyalty programs, or promotional shortcodes. Even though the strings look bank-ish,
     * the body is *always* an offer, never a transaction confirmation.
     *
     * Important Saudi telecom convention: shortcodes ending in `-AD` or `-Ad` are
     * registered with CITC specifically as advertising channels. We rely on this
     * suffix as a structural signal — see [hasAdSuffix].
     */
    val hardBlocked: Set<String> = setOf(
        "mokafaa",          // Al Rajhi loyalty program — sends point offers
        "AlRajhiB-AD",      // Al Rajhi marketing affiliate
        "stc play",         // STC prize contests
        "stcplay-AD",
        "Almosafer-AD",     // travel marketing
        "FoodicsOTP",       // OTP-only — not a transaction sender
        "monymoon-AD",      // financing pitches
    )

    /**
     * The `-AD` / `-Ad` / `-ad` suffix on Saudi shortcodes is the CITC-registered
     * marker for advertising channels. Any sender ending with this suffix is, by
     * Saudi telecom convention, a marketing-only sender. Hard-block on principle.
     */
    fun hasAdSuffix(sender: String): Boolean {
        val trimmed = sender.trim()
        return trimmed.endsWith("-AD", ignoreCase = true) ||
            trimmed.endsWith("-Ad", ignoreCase = false) ||
            trimmed.endsWith(" AD", ignoreCase = true)
    }

    /**
     * Matches a sender string against the allow-list. Trims and case-normalizes so
     * trivial variations ("alrajhibank" vs "AlRajhiBank") still match.
     *
     * Even if a sender's prefix matches a known bank ("AlRajhiB-AD" looks like
     * Al Rajhi), the `-AD` suffix forces a NO match — these are marketing-only
     * senders that should never reach the parser.
     */
    fun isKnown(sender: String): Boolean {
        val trimmed = sender.trim()
        if (hasAdSuffix(trimmed)) return false
        if (trimmed in hardBlocked) return false
        if (trimmed in builtIn) return true
        val lower = trimmed.lowercase()
        if (hardBlocked.any { it.lowercase() == lower }) return false
        return builtIn.any { it.lowercase() == lower }
    }
}
