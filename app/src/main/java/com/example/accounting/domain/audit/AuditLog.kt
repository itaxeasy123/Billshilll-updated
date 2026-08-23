package com.example.accounting.domain.audit

enum class AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    CREATE_COMPANY,
    SWITCH_COMPANY,
    CREATE_BRANCH,
    CREATE_LEDGER,
    UPDATE_LEDGER,
    DELETE_LEDGER,
    POST_VOUCHER,
    CANCEL_VOUCHER,
    LOCK_PERIOD,
    UNLOCK_PERIOD,
    SYNC_TRIGGERED,
    CONFLICT_RESOLVED
}

data class AuditLog(
    val logId: String,
    val companyId: String,
    val financialYearId: String = "",
    val action: AuditAction,
    val entityType: String,
    val entityId: String,
    val description: String,
    val performedBy: String = "ADMIN",
    val timestamp: Long = System.currentTimeMillis(),
    val payloadJson: String = ""
)
