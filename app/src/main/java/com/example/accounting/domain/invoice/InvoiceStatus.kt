package com.example.accounting.domain.invoice

enum class InvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}
