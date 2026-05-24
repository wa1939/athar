package com.athar.core.common.money

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A money amount in a single currency.
 *
 * Always uses [BigDecimal] internally. Never use [Float]/[Double] for money — IEEE 754
 * rounding errors silently destroy financial accuracy. See Master Brief §5.1.
 *
 * Construct via [Money.of] (typed inputs) — the public ctor is internal to enforce that.
 */
@Serializable
@ConsistentCopyVisibility
data class Money internal constructor(
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
) : Comparable<Money> {

    init {
        require(currency.length == 3) { "Currency must be ISO-4217 3-letter code, got '$currency'" }
    }

    operator fun plus(other: Money): Money {
        sameCurrency(other)
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        sameCurrency(other)
        return Money(amount - other.amount, currency)
    }

    operator fun unaryMinus(): Money = Money(amount.negate(), currency)

    operator fun times(scalar: Int): Money = Money(amount.multiply(BigDecimal(scalar)), currency)

    operator fun times(scalar: BigDecimal): Money = Money(amount.multiply(scalar), currency)

    override fun compareTo(other: Money): Int {
        sameCurrency(other)
        return amount.compareTo(other.amount)
    }

    /** Round to two fractional digits (SAR halalas / USD cents). */
    fun rounded(scale: Int = 2, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(amount.setScale(scale, mode), currency)

    fun isPositive(): Boolean = amount.signum() > 0
    fun isNegative(): Boolean = amount.signum() < 0
    fun isZero(): Boolean = amount.signum() == 0

    private fun sameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}"
        }
    }

    companion object {
        const val SAR = "SAR"

        fun zero(currency: String = SAR): Money = Money(BigDecimal.ZERO, currency)

        fun of(amount: BigDecimal, currency: String = SAR): Money =
            Money(amount, currency)

        fun of(amount: String, currency: String = SAR): Money =
            Money(BigDecimal(amount), currency)

        fun of(amount: Long, currency: String = SAR): Money =
            Money(BigDecimal.valueOf(amount), currency)

        /**
         * Construct from minor units (e.g., halalas, cents). Use only when parsing
         * persisted minor-unit data; UI/business code should use the major-unit forms above.
         */
        fun ofMinor(minor: Long, currency: String = SAR, fractionDigits: Int = 2): Money =
            Money(BigDecimal.valueOf(minor, fractionDigits), currency)
    }
}
