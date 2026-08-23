package com.example.accounting.domain.reconciliation

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.rendering.BusinessProfile
import java.time.LocalDate

/** Whether an imported bank-statement line represents money added to or removed from the bank
 * account, from the bank's own point of view. Deliberately NOT
 * [com.example.accounting.core.common.DrCr] - a bank statement's "credit" (money in) and this
 * app's ledger `DrCr.CREDIT` are opposite-perspective concepts for the same event (a deposit is a
 * bank-statement credit but a `DrCr.DEBIT` on the company's own Bank ledger); reusing `DrCr` here
 * would silently invite exactly that confusion. */
enum class BankLineDirection { DEPOSIT, WITHDRAWAL }

/** One line from an imported bank statement - plain, parsed data, not yet matched to anything. */
data class BankStatementLine(
    val lineId: String,
    val date: LocalDate,
    val description: String,
    val amount: Money,
    val direction: BankLineDirection
)

/** How confident a suggested match is - purely informational, never a threshold this adapter
 * itself acts on (e.g. auto-confirming HIGH-confidence matches is explicitly not something this
 * type enables). */
enum class ReconciliationMatchConfidence { HIGH, MEDIUM, LOW }

/** A *suggested* pairing between one imported bank-statement line and one already-posted
 * [com.example.accounting.domain.accounting.Voucher] - a proposal for a human to confirm, never an
 * instruction this or any other code acts on automatically. */
data class SuggestedVoucherMatch(
    val bankStatementLineId: String,
    val voucherId: String,
    val confidence: ReconciliationMatchConfidence,
    val reason: String
)

/** Full result of one reconciliation-suggestion pass: every imported line, whatever suggested
 * matches were found, and which lines matched nothing (also useful to a human - an unmatched line
 * might mean a voucher hasn't been entered yet, or was entered with a materially different date/
 * amount). */
data class ReconciliationSuggestionResult(
    val companyId: String,
    val statementLines: List<BankStatementLine>,
    val suggestedMatches: List<SuggestedVoucherMatch>,
    val unmatchedLineIds: List<String>
)

/**
 * Adapter boundary for bank-statement-import reconciliation *suggestions* (Phase 7I) - a pure
 * Kotlin interface only, no implementation. This directly fulfills the target architecture
 * Phase 7F's "Bank reconciliation = QUARANTINED" safety decision already promised but explicitly
 * did not build (`docs/50_AUTOMATION_ARCHITECTURE.md`):
 *
 * ```
 * Bank Data -> Imported Transaction -> Suggested Match -> Human Review -> Existing Posting Engine
 *
 * Never: Bank Feed -> Auto-match -> Direct Ledger Posting
 * ```
 *
 * [suggestMatches] takes [candidateVouchers] as an explicit parameter (reusing the existing
 * [com.example.accounting.domain.accounting.Voucher] domain type directly, never a duplicate
 * summary type) rather than querying `AccountingDao` itself - this interface has no DAO/repository
 * access at all, so it is structurally incapable of reading anything beyond what its caller
 * chooses to hand it, and structurally incapable of writing anything at all. It can only ever
 * *propose* pairings; nothing here creates, edits, matches, or posts a
 * [com.example.accounting.domain.accounting.Voucher] or touches a
 * [com.example.accounting.domain.accounting.Ledger] balance.
 */
interface ReconciliationAdapter {
    suspend fun suggestMatches(
        requestingCompany: BusinessProfile,
        statementLines: List<BankStatementLine>,
        candidateVouchers: List<Voucher>
    ): AccountingResult<ReconciliationSuggestionResult>
}
