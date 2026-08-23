# 04. Accounting Period

## WHAT
Discrete monthly or quarterly subsets of a Financial Year.

## WHY
Enables continuous monthly closes, tax returns (e.g. GSTR-1, GSTR-3B), and prevents backdated voucher creation in finalized periods.

## RULES
- Periods possess three states: `OPEN`, `LOCKED` (soft lock by controller), and `AUDIT_LOCKED` (permanent auditor lock).
- No vouchers can be created, posted, or deleted within a period whose status is not `OPEN`.

## WHAT MUST NOT CHANGE
- The prohibition of mutations in `LOCKED` and `AUDIT_LOCKED` periods.
