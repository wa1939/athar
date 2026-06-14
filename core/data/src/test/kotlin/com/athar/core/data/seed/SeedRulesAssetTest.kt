package com.athar.core.data.seed

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class SeedRulesAssetTest {

    @Test
    fun `seed rules reference existing categories and have unique patterns`() {
        val categoryIds = seedCategories()
        val rules = seedRules()

        assertThat(rules).hasSize(703)
        assertThat(rules.map { it.categoryId }.filterNot { it in categoryIds }).isEmpty()

        val duplicates = rules
            .groupBy { it.pattern.trim().lowercase() }
            .filterValues { it.size > 1 }
            .keys
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `side income seeds stay public or institution backed`() {
        val unsupportedSideIncomeRules = seedRules()
            .filter { it.categoryId == "cat-side-income" && it.priority < 90 }
            .filterNot { publicSideIncomeCues.any(it.pattern.lowercase()::contains) }
            .map { it.pattern }

        assertThat(unsupportedSideIncomeRules).isEmpty()
    }

    @Test
    fun `curated merchant catalog batch is active below hand curated rules`() {
        val rulesByPattern = seedRules().associateBy { it.pattern }

        curatedBatch.forEach { (pattern, categoryId) ->
            val rule = rulesByPattern[pattern]
            assertThat(rule).isNotNull()
            assertThat(rule!!.categoryId).isEqualTo(categoryId)
            assertThat(rule.priority).isEqualTo(90)
        }
    }

    private fun seedRules(): List<SeedRule> {
        val root = readJson("seed_rules.json")
        return root.jsonObject.getValue("rules").jsonArray.map { element ->
            val obj = element.jsonObject
            SeedRule(
                pattern = obj.getValue("pattern").jsonPrimitive.content,
                categoryId = obj.getValue("categoryId").jsonPrimitive.content,
                priority = obj.getValue("priority").jsonPrimitive.int,
            )
        }
    }

    private fun seedCategories(): Set<String> {
        val root = readJson("seed_categories.json")
        return root.jsonObject.getValue("categories").jsonArray.mapTo(mutableSetOf()) { element ->
            element.jsonObject.getValue("id").jsonPrimitive.content
        }
    }

    private fun readJson(fileName: String) =
        Json.parseToJsonElement(assetPath(fileName).readText())

    private fun assetPath(fileName: String): Path {
        val modulePath = Path.of("src/main/assets", fileName)
        if (Files.exists(modulePath)) return modulePath
        return Path.of("core/data/src/main/assets", fileName)
    }

    private data class SeedRule(
        val pattern: String,
        val categoryId: String,
        val priority: Int,
    )

    private companion object {
        val curatedBatch = mapOf(
            "%arabica" to "cat-coffee",
            "agoda" to "cat-travel",
            "al baik" to "cat-restaurant",
            "aldi" to "cat-groceries",
            "alrazi" to "cat-medical",
            "anthropic" to "cat-subscriptions",
            "apple.com" to "cat-subscriptions",
            "applebee" to "cat-restaurant",
            "appstore" to "cat-subscriptions",
            "bindawood" to "cat-groceries",
            "burger & lobster" to "cat-restaurant",
            "burger king" to "cat-restaurant",
            "cafe nero" to "cat-coffee",
            "centrepoint" to "cat-clothing",
            "coursera" to "cat-education",
            "emirates" to "cat-travel",
            "etihad" to "cat-travel",
            "expedia" to "cat-travel",
            "fitness time" to "cat-gym",
            "flat iron" to "cat-restaurant",
            "google play" to "cat-subscriptions",
            "h & m" to "cat-clothing",
            "h&m" to "cat-clothing",
            "hello bike" to "cat-public-transport",
            "home centre" to "cat-home-maintenance",
            "homecentre" to "cat-home-maintenance",
            "icloud" to "cat-subscriptions",
            "joe & the juice" to "cat-coffee",
            "jollychic" to "cat-clothing",
            "kudu" to "cat-restaurant",
            "lebara" to "cat-telecom",
            "lidl" to "cat-groceries",
            "longchamp" to "cat-clothing",
            "lufthansa" to "cat-travel",
            "max fashion" to "cat-clothing",
            "nando" to "cat-restaurant",
            "national water" to "cat-utilities",
            "nesto" to "cat-groceries",
            "netflix" to "cat-subscriptions",
            "openai" to "cat-subscriptions",
            "operation falafel" to "cat-restaurant",
            "papa john" to "cat-restaurant",
            "penny market" to "cat-groceries",
            "petromin" to "cat-gas",
            "pizza hut" to "cat-restaurant",
            "qatar airways" to "cat-travel",
            "sainsbury" to "cat-groceries",
            "sarawat" to "cat-groceries",
            "saudi aramco" to "cat-gas",
            "shahid" to "cat-subscriptions",
            "shake shack" to "cat-restaurant",
            "shein" to "cat-clothing",
            "spotify" to "cat-subscriptions",
            "starzplay" to "cat-subscriptions",
            "tesco" to "cat-groceries",
            "texas roadhouse" to "cat-restaurant",
            "tfl" to "cat-public-transport",
            "tgi fri" to "cat-restaurant",
            "the cheesecake" to "cat-restaurant",
            "tim hortons" to "cat-coffee",
            "turkish airlines" to "cat-travel",
            "udemy" to "cat-education",
            "united pharmacy" to "cat-medical",
            "virgin mobile" to "cat-telecom",
            "waitrose" to "cat-groceries",
            "water company" to "cat-utilities",
            "youtube premium" to "cat-subscriptions",
            "herfy" to "cat-restaurant",
            "luckin" to "cat-coffee",
            "manner coffee" to "cat-coffee",
            "aldrees" to "cat-gas",
            "didi taxi" to "cat-public-transport",
            "hellobike" to "cat-public-transport",
            "alnahdi" to "cat-medical",
            "saudi electric" to "cat-utilities",
            "electric company" to "cat-utilities",
            "zara" to "cat-clothing",
            "uniqlo" to "cat-clothing",
            "airbnb" to "cat-travel",
            "booking.com" to "cat-travel",
            "credit card payment" to "cat-debt",
            "mortgage payment" to "cat-mortgage",
            "car payment" to "cat-car-payment",
            "loan instalment" to "cat-debt",
            "xtra" to "cat-electronics",
            "fit time" to "cat-gym",
            "github" to "cat-subscriptions",
            "duty free" to "cat-travel",
            "camel step" to "cat-coffee",
            "manuel" to "cat-groceries",
            "riyadh parking" to "cat-public-transport",
            "cursor" to "cat-subscriptions",
            "boots" to "cat-medical",
            "section b" to "cat-restaurant",
            "splash" to "cat-clothing",
            "tabby" to "cat-debt",
            "farm supe" to "cat-groceries",
            "petrofas" to "cat-gas",
            "fuelax" to "cat-gas",
            "gathern" to "cat-travel",
            "giordano" to "cat-clothing",
            "ryanair" to "cat-travel",
            "wizz air" to "cat-travel",
            "saco" to "cat-electronics",
            "airalo" to "cat-telecom",
            "woqoof" to "cat-public-transport",
            "المخالفات المرورية" to "cat-utilities",
            "خدمات المقيمين" to "cat-utilities",
            "atm withdrawal" to "cat-other-expense",
            "bank fees" to "cat-other-expense",
            "mcdo" to "cat-restaurant",
            "andalusi" to "cat-medical",
            "seoudi market" to "cat-groceries",
            "hofer" to "cat-groceries",
            "tap*tamee" to "cat-insurance",
            "tamwinat" to "cat-groceries",
            "temu" to "cat-other-expense",
            "ejar" to "cat-rent",
            "منصة ايجار" to "cat-rent",
            "منصة إيجار" to "cat-rent",
            "موبايلي" to "cat-telecom",
            "جونشور" to "cat-insurance",
            "تأميني" to "cat-insurance",
            "marjane" to "cat-groceries",
            "migros" to "cat-groceries",
            "nova coop" to "cat-groceries",
            "seoudi" to "cat-groceries",
            "knpc" to "cat-gas",
            "cottonil" to "cat-clothing",
            "kunest cafe" to "cat-coffee",
            "okaz phar" to "cat-medical",
            "orange al" to "cat-telecom",
            "marks & spencer" to "cat-groceries",
            "aliexpress" to "cat-other-expense",
            "spar" to "cat-groceries",
            "panificio" to "cat-restaurant",
            "regoli" to "cat-restaurant",
            "chili's" to "cat-restaurant",
            "chilis" to "cat-restaurant",
            "buffet" to "cat-restaurant",
            "claude" to "cat-subscriptions",
            "airside" to "cat-travel",
            "harvard" to "cat-education",
            "chatgpt" to "cat-subscriptions",
            "notion" to "cat-subscriptions",
            "canva" to "cat-subscriptions",
            "figma" to "cat-subscriptions",
            "dropbox" to "cat-subscriptions",
            "hulu" to "cat-subscriptions",
            "disney+" to "cat-subscriptions",
            "disneyplus" to "cat-subscriptions",
            "paramount+" to "cat-subscriptions",
            "prime video" to "cat-subscriptions",
            "audible" to "cat-subscriptions",
            "deliveroo" to "cat-restaurant",
            "instacart" to "cat-groceries",
            "costco" to "cat-groceries",
            "whole foods" to "cat-groceries",
            "liter m" to "cat-gas",
            "albayan station" to "cat-gas",
            "united pharmacies" to "cat-medical",
            "subscription payment" to "cat-subscriptions",
            "insurance premium" to "cat-insurance",
            "rent payment" to "cat-rent",
            "mobile recharge" to "cat-telecom",
            "traffic fine payment" to "cat-utilities",
            "government service payment" to "cat-utilities",
            "parking payment" to "cat-public-transport",
            "toll payment" to "cat-public-transport",
            "transit fare" to "cat-public-transport",
            "medical payment" to "cat-medical",
            "pharmacy payment" to "cat-medical",
            "education payment" to "cat-education",
            "charity donation" to "cat-charity",
            "zakat payment" to "cat-charity",
            "gift purchase" to "cat-gifts",
            "gift card purchase" to "cat-gifts",
            "flower delivery" to "cat-gifts",
            "furniture purchase" to "cat-home-maintenance",
            "home goods purchase" to "cat-home-maintenance",
            "appliance purchase" to "cat-electronics",
            "home service payment" to "cat-home-maintenance",
            "gym membership" to "cat-gym",
            "childcare payment" to "cat-childcare",
            "car service payment" to "cat-car-maintenance",
            "oil change payment" to "cat-car-maintenance",
            "car wash payment" to "cat-car-maintenance",
            "tire service payment" to "cat-car-maintenance",
            "fuel purchase" to "cat-gas",
            "grocery purchase" to "cat-groceries",
            "restaurant payment" to "cat-restaurant",
            "coffee payment" to "cat-coffee",
            "food delivery payment" to "cat-restaurant",
            "taxi ride payment" to "cat-public-transport",
            "flight ticket" to "cat-travel",
            "hotel payment" to "cat-travel",
            "travel booking" to "cat-travel",
            "car rental payment" to "cat-travel",
            "clothing purchase" to "cat-clothing",
            "electronics purchase" to "cat-electronics",
            "online shopping purchase" to "cat-other-expense",
            "cinema ticket" to "cat-entertainment",
            "event ticket" to "cat-going-out",
            "game purchase" to "cat-entertainment",
        )

        val publicSideIncomeCues = listOf(
            "account",
            "airbnb",
            "capital",
            "cash",
            "co.",
            "company",
            "cooperative",
            "cruises",
            "dhamen",
            "google",
            "insurance",
            "profit",
            "surplus",
            "trading",
            "اليكترون",
            "شركة",
            "كاش باك",
            "وزارة",
            "يورباي",
        )
    }
}
