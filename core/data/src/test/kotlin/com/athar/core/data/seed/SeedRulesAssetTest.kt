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

        assertThat(rules).hasSize(644)
        assertThat(rules.map { it.categoryId }.filterNot { it in categoryIds }).isEmpty()

        val duplicates = rules
            .groupBy { it.pattern.trim().lowercase() }
            .filterValues { it.size > 1 }
            .keys
        assertThat(duplicates).isEmpty()
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
        )
    }
}
