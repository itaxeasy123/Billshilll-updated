package com.example.accounting.domain.sandbox

/**
 * Whether a given Sandbox service is bundled into the account's base plan or billed separately -
 * purely informational (e.g. for a settings screen to show "no extra cost" vs. "pay-per-call"
 * next to a service toggle). Carries no amount; Sandbox's own dashboard is the source of truth for
 * actual pricing, this only records which bucket a service falls in.
 */
enum class PricingType { INCLUDED, PAID }

/**
 * Enablement state for one Sandbox service (e.g. GST verification, e-Invoice, e-Way Bill) within a
 * company's integration. A pure, credential-free status record - see [SandboxIntegrationConfiguration]'s
 * class doc for where actual API credentials belong.
 */
data class SandboxServiceStatus(
    val serviceName: String,
    val pricingType: PricingType,
    val isEnabled: Boolean
)

/**
 * A company's Sandbox.co.in integration configuration (Phase 7H, Module 1). **Deliberately carries
 * no raw credential field** - no API key, secret, access token, or client ID anywhere on this
 * type, and none should ever be added to it. Sandbox API credentials are secrets, not
 * configuration: they belong in the existing, already-Keystore-backed
 * [com.example.accounting.core.security.SecureStorage] (`getCompanySetting`/`setCompanySetting`,
 * company-scoped, so TEST and LIVE credentials for different companies never collide under one
 * key), never in a plain Room-persisted or in-memory data class that could end up in a log line, a
 * crash report, or an export. This model only records *which* environment and *which* services are
 * active for a company - never *how* to authenticate to them.
 */
data class SandboxIntegrationConfiguration(
    val companyId: String,
    val environment: SandboxEnvironment,
    val enabledServices: List<SandboxServiceStatus>,
    val isActive: Boolean
)
