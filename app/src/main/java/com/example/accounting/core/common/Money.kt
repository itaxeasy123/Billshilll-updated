package com.example.accounting.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Immutable value object representing monetary amounts in lowest currency unit (Paise / Cents).
 * Guarantees zero floating-point rounding errors across all accounting calculations with BigDecimal backing.
 */
data class Money(val paise: Long = 0L) : Comparable<Money> {

    val isZero: Boolean get() = paise == 0L
    val isPositive: Boolean get() = paise > 0L
    val isNegative: Boolean get() = paise < 0L

    val toBigDecimal: BigDecimal get() = BigDecimal(paise).divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN)

    operator fun plus(other: Money): Money = Money(this.paise + other.paise)
    operator fun minus(other: Money): Money = Money(this.paise - other.paise)
    operator fun unaryMinus(): Money = Money(-this.paise)

    operator fun times(multiplier: Long): Money = Money(this.paise * multiplier)
    operator fun times(multiplier: Double): Money {
        val bd = BigDecimal(paise).multiply(BigDecimal.valueOf(multiplier))
        return Money(bd.setScale(0, RoundingMode.HALF_EVEN).toLong())
    }
    operator fun times(multiplier: BigDecimal): Money {
        val bd = BigDecimal(paise).multiply(multiplier)
        return Money(bd.setScale(0, RoundingMode.HALF_EVEN).toLong())
    }

    operator fun div(divisor: Long): Money = Money(if (divisor != 0L) this.paise / divisor else 0L)
    operator fun div(divisor: Double): Money {
        if (divisor == 0.0) return ZERO
        val bd = BigDecimal(paise).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_EVEN)
        return Money(bd.toLong())
    }
    operator fun div(divisor: BigDecimal): Money {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) return ZERO
        val bd = BigDecimal(paise).divide(divisor, 0, RoundingMode.HALF_EVEN)
        return Money(bd.toLong())
    }

    fun percentage(percent: Double): Money {
        val bd = BigDecimal(paise).multiply(BigDecimal.valueOf(percent)).divide(BigDecimal(100), 0, RoundingMode.HALF_EVEN)
        return Money(bd.toLong())
    }

    fun percentage(percent: BigDecimal): Money {
        val bd = BigDecimal(paise).multiply(percent).divide(BigDecimal(100), 0, RoundingMode.HALF_EVEN)
        return Money(bd.toLong())
    }

    fun abs(): Money = Money(kotlin.math.abs(this.paise))

    override operator fun compareTo(other: Money): Int = this.paise.compareTo(other.paise)

    /**
     * Formats amount in Indian numbering system: ₹ ##,##,###.##
     */
    fun format(includeSymbol: Boolean = true): String {
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val rupeeString = formatIndianNumber(rupees)
        val paiseString = String.format(Locale.US, "%02d", remainingPaise)
        val sign = if (paise < 0) "-" else ""
        val symbol = if (includeSymbol) "₹" else ""

        return "$sign$symbol$rupeeString.$paiseString"
    }

    fun toRupeesDouble(): Double = paise / 100.0

    /**
     * Plain decimal accounting display: no currency symbol, no Indian (or any) digit grouping -
     * e.g. "500000.15", "-1250000.50", "0.00". Same paise precision as [format]; this is a
     * presentation-only alternative (Phase 4.5, Section D.1) - the authoritative paise Long and
     * its HALF_EVEN rounding are unchanged.
     */
    fun formatPlain(): String {
        val sign = if (paise < 0) "-" else ""
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100
        return "$sign$rupees.${String.format(Locale.US, "%02d", remainingPaise)}"
    }

    private fun formatIndianNumber(number: Long): String {
        if (number == 0L) return "0"
        val numStr = number.toString()
        if (numStr.length <= 3) return numStr

        val lastThree = numStr.substring(numStr.length - 3)
        val rest = numStr.substring(0, numStr.length - 3)

        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i > 0) {
                sb.append(",")
                count = 0
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0L)

        fun fromRupees(rupees: Double): Money {
            return Money(Math.round(rupees * 100.0))
        }

        fun fromRupees(rupees: Long): Money {
            return Money(rupees * 100L)
        }

        fun fromPaise(paise: Long): Money {
            return Money(paise)
        }

        fun parse(input: String): Money {
            val sanitized = input.replace("₹", "").replace(",", "").trim()
            if (sanitized.isEmpty()) return ZERO
            return try {
                val value = sanitized.toDouble()
                fromRupees(value)
            } catch (e: Exception) {
                ZERO
            }
        }
    }
}
