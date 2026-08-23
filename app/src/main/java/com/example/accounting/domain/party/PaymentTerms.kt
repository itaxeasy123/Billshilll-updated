package com.example.accounting.domain.party

import java.time.LocalDate

enum class PaymentTermsType {
    DUE_ON_RECEIPT,
    NET_7,
    NET_15,
    NET_30,
    NET_45,
    NET_60,
    CUSTOM
}

/**
 * Pure due-date computation (Phase 7A) - the sole authority for turning an invoice date into a
 * due date. Never computed inline in the UI/ViewModel; every Invoice snapshots its own resolved
 * due date at creation time (see [com.example.accounting.domain.invoice.Invoice.dueDate]) so a
 * later change to a party's terms never retroactively alters an already-issued invoice.
 */
data class PaymentTerms(
    val type: PaymentTermsType = PaymentTermsType.DUE_ON_RECEIPT,
    val customDays: Int? = null
) {
    fun dueDate(invoiceDate: LocalDate): LocalDate = when (type) {
        PaymentTermsType.DUE_ON_RECEIPT -> invoiceDate
        PaymentTermsType.NET_7 -> invoiceDate.plusDays(7)
        PaymentTermsType.NET_15 -> invoiceDate.plusDays(15)
        PaymentTermsType.NET_30 -> invoiceDate.plusDays(30)
        PaymentTermsType.NET_45 -> invoiceDate.plusDays(45)
        PaymentTermsType.NET_60 -> invoiceDate.plusDays(60)
        PaymentTermsType.CUSTOM -> invoiceDate.plusDays((customDays ?: 0).toLong())
    }

    companion object {
        val DUE_ON_RECEIPT = PaymentTerms(PaymentTermsType.DUE_ON_RECEIPT)
    }
}
