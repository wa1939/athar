package com.athar.core.designsystem.display

/**
 * ISO-4217 currency code → user-facing symbol. Curated for Athar's supported markets
 * (Gulf, MENA, India/Pakistan/Turkey, plus the global big four). Anything not in the
 * map falls back to the 3-letter ISO code itself.
 *
 * Symbols are intentionally short. For currencies whose script doesn't have a single
 * glyph (e.g., Kuwaiti dinar = "د.ك"), we use the standard 2- or 3-character Arabic
 * abbreviation. Latin-script symbols ($/€/£/₹/₺) use the canonical character.
 */
object CurrencyCatalog {
    data class Entry(val code: String, val symbol: String, val labelEn: String, val labelAr: String)

    val supported: List<Entry> = listOf(
        // Gulf + Saudi-region
        Entry("SAR", "ر.س", "Saudi Riyal", "ريال سعودي"),
        Entry("AED", "د.إ", "UAE Dirham", "درهم إماراتي"),
        Entry("KWD", "د.ك", "Kuwaiti Dinar", "دينار كويتي"),
        Entry("QAR", "ر.ق", "Qatari Riyal", "ريال قطري"),
        Entry("BHD", ".د.ب", "Bahraini Dinar", "دينار بحريني"),
        Entry("OMR", "ر.ع", "Omani Rial", "ريال عماني"),
        Entry("JOD", "د.أ", "Jordanian Dinar", "دينار أردني"),
        Entry("EGP", "ج.م", "Egyptian Pound", "جنيه مصري"),
        // South + West Asia
        Entry("INR", "₹", "Indian Rupee", "روبية هندية"),
        Entry("PKR", "₨", "Pakistani Rupee", "روبية باكستانية"),
        Entry("TRY", "₺", "Turkish Lira", "ليرة تركية"),
        // Global big-four
        Entry("USD", "$", "US Dollar", "دولار أمريكي"),
        Entry("EUR", "€", "Euro", "يورو"),
        Entry("GBP", "£", "British Pound", "جنيه إسترليني"),
        Entry("CAD", "CA$", "Canadian Dollar", "دولار كندي"),
        Entry("AUD", "A$", "Australian Dollar", "دولار أسترالي"),
        Entry("CHF", "CHF", "Swiss Franc", "فرنك سويسري"),
        Entry("JPY", "¥", "Japanese Yen", "ين ياباني"),
    )

    private val byCode: Map<String, Entry> = supported.associateBy { it.code }

    fun symbolOf(code: String): String = byCode[code]?.symbol ?: code

    fun entryOf(code: String): Entry? = byCode[code]
}
