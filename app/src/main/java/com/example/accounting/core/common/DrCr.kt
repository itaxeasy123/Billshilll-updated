package com.example.accounting.core.common

enum class DrCr {
    DEBIT,
    CREDIT;

    val symbol: String get() = if (this == DEBIT) "Dr" else "Cr"
    val code: String get() = symbol
    val opposite: DrCr get() = if (this == DEBIT) CREDIT else DEBIT

    fun signMultiplier(): Int = if (this == DEBIT) 1 else -1

    companion object {
        fun fromString(value: String): DrCr {
            return when (value.uppercase()) {
                "DR", "DEBIT" -> DEBIT
                "CR", "CREDIT" -> CREDIT
                else -> DEBIT
            }
        }
    }
}
