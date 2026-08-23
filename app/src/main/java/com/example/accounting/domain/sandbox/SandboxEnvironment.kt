package com.example.accounting.domain.sandbox

/**
 * Which Sandbox.co.in API environment a company's integration talks to (Phase 7H). [TEST] and
 * [LIVE] use entirely separate credential sets and base URLs on Sandbox's side - this enum only
 * records the choice, it never resolves a URL or touches a credential itself. Every operation on
 * [SandboxProviderAdapter] takes this explicitly as a parameter; nothing in this package stores a
 * "current" environment as implicit state.
 */
enum class SandboxEnvironment { TEST, LIVE }
