# 09. Voucher & Journal Entry Anatomy

## WHAT
The atomic transaction container containing header metadata and an array of balanced double-entry lines (`JournalEntry`).

## STRUCTURE
```kotlin
data class Voucher(
    val voucherId: String,
    val companyId: String,
    val financialYearId: String,
    val voucherNumber: String,
    val voucherType: VoucherType,
    val date: LocalDate,
    val narration: String,
    val isPosted: Boolean = true,
    val isCancelled: Boolean = false,
    val items: List<JournalItem>
)

data class JournalEntry(
    val journalEntryId: String,
    val voucherId: String,
    val companyId: String,
    val financialYearId: String,
    val ledgerId: String,
    val debit: Money = Money.ZERO,
    val credit: Money = Money.ZERO,
    val lineNumber: Int = 0,
    val narration: String = ""
)
```

## INVARIANTS
- A `JournalEntry` cannot have both `debit > 0` and `credit > 0`.
- $\sum \text{debit} == \sum \text{credit}$ across all lines of a voucher.
