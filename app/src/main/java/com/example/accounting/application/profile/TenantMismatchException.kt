package com.example.accounting.application.profile

/**
 * Thrown by [ProfileApplicationService] when a caller's authenticated `contextCompanyId` does not
 * match the `companyId` embedded in the profile resource it's operating on. Every DAO query this
 * service delegates to already scopes its `WHERE` clause by `companyId` (so a real cross-tenant
 * row can never be *returned* - see the Phase 7D/7E tenant-isolation precedent), but this
 * exception is the explicit, fail-loud defense-in-depth layer above that: a caller passing a
 * mismatched context/resource pair is a programming error, not a "not found," and must never be
 * silently swallowed into a null/empty result.
 */
class TenantMismatchException(
    val contextCompanyId: String,
    val resourceCompanyId: String,
    message: String = "Company '$contextCompanyId' attempted to access a profile belonging to company '$resourceCompanyId'."
) : Exception(message)
