package com.example.accounting.core.common

import java.text.DecimalFormat
import java.util.Locale

/**
 * Immutable quantity value object with unit of measurement support and 3 decimal places precision (stored as thousandths).
 */
data class Quantity(
    val rawValue: Long = 0L, // 1.000 = 1000L
    val unit: String = "Nos"
) : Comparable<Quantity> {

    val doubleValue: Double get() = rawValue / 1000.0

    operator fun plus(other: Quantity): Quantity {
        require(unit.equals(other.unit, ignoreCase = true)) { "Cannot add different units: $unit and ${other.unit}" }
        return Quantity(this.rawValue + other.rawValue, unit)
    }

    operator fun minus(other: Quantity): Quantity {
        require(unit.equals(other.unit, ignoreCase = true)) { "Cannot subtract different units: $unit and ${other.unit}" }
        return Quantity(this.rawValue - other.rawValue, unit)
    }

    fun format(): String {
        val df = DecimalFormat("#,##0.###")
        return "${df.format(doubleValue)} $unit"
    }

    override fun compareTo(other: Quantity): Int = this.rawValue.compareTo(other.rawValue)

    companion object {
        val ZERO = Quantity(0L, "Nos")

        fun fromDouble(value: Double, unit: String = "Nos"): Quantity {
            return Quantity(Math.round(value * 1000.0), unit)
        }

        fun fromLong(value: Long, unit: String = "Nos"): Quantity {
            return Quantity(value * 1000L, unit)
        }
    }
}
