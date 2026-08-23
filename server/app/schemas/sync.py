"""
Pydantic schemas mirroring the Kotlin `SyncEvent` envelope 1:1
(app/src/main/java/com/example/accounting/domain/sync/SyncEvent.kt) - field-for-field, so the JSON
Android serializes via Moshi deserializes here without any translation layer. `schemaVersion` is
carried through so a future incompatible change can be detected and rejected cleanly rather than
silently misinterpreted (Phase 6, Priority 6.15 - `/api/v1/` + payload schema versions).
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict


class SyncJournalLineDto(BaseModel):
    itemId: str
    ledgerId: str
    ledgerName: str = ""
    type: str
    amountPaise: int
    narration: str = ""
    lineOrder: int = 0


class SyncStockLineDto(BaseModel):
    lineId: str
    itemId: str
    direction: str
    quantityRaw: int
    ratePaise: int
    amountPaise: int
    lineOrder: int = 0


class SyncGstTransactionDto(BaseModel):
    gstTransactionId: str
    partyLedgerId: str
    partyGstin: str = ""
    placeOfSupply: str = ""
    supplyType: str
    itemId: str | None = None
    hsnSacCode: str = ""
    quantityRaw: int | None = None
    taxableAmountPaise: int
    gstRatePercent: float
    cgstPaise: int = 0
    sgstPaise: int = 0
    igstPaise: int = 0
    cessPaise: int = 0
    direction: str
    lineOrder: int = 0


class SyncSettlementDto(BaseModel):
    allocationId: str
    settlementVoucherId: str
    invoiceVoucherId: str | None = None
    allocatedAmountPaise: int


class SyncVoucherDto(BaseModel):
    voucherId: str
    voucherNumber: str
    voucherType: str
    date: str
    referenceNumber: str = ""
    narration: str = ""
    totalAmountPaise: int
    isCancelled: bool = False
    createdBy: str = ""
    partyGstin: str = ""
    isGstApplicable: bool = False
    referenceVoucherId: str | None = None
    paymentMode: str = ""


class SyncLedgerDto(BaseModel):
    ledgerId: str
    groupId: str
    name: str
    code: str = ""
    openingBalancePaise: int = 0
    openingBalanceType: str = "DEBIT"
    gstin: str = ""
    pan: str = ""
    stateCode: str = ""
    hsnSacCode: str = ""
    defaultTaxRate: float = 0.0


class SyncPartyDto(BaseModel):
    """Phase 7A - Party + Invoice Domain Foundation."""
    partyId: str
    ledgerId: str
    role: str
    entityType: str
    displayName: str
    contactName: str = ""
    creditLimitPaise: int | None = None
    paymentTermsType: str = "DUE_ON_RECEIPT"
    paymentTermsCustomDays: int | None = None
    isActive: bool = True


class SyncInvoiceLineDto(BaseModel):
    lineId: str
    itemId: str
    itemName: str
    hsnSacCode: str
    quantityRaw: int
    ratePaise: int
    gstRatePercent: float
    cessRatePercent: float
    lineOrder: int


class SyncInvoiceDto(BaseModel):
    """Fields beyond invoiceId/voucherId/invoiceNumber default blank so the same DTO covers both a
    full CREATE_DRAFT_INVOICE payload and the minimal LINK_INVOICE_VOUCHER follow-up event."""
    invoiceId: str
    invoiceType: str = ""
    invoiceNumber: str | None = None
    partyId: str = ""
    date: str = ""
    dueDate: str | None = None
    voucherId: str | None = None
    referenceInvoiceId: str | None = None
    sourceTradeDocumentId: str | None = None
    narration: str = ""
    lines: list[SyncInvoiceLineDto] = []


class SyncTradeDocumentLineDto(BaseModel):
    """Phase 7B - Document/Voucher Lifecycle Architecture."""
    lineId: str
    itemId: str
    itemName: str
    hsnSacCode: str
    quantityRaw: int
    ratePaise: int
    gstRatePercent: float
    cessRatePercent: float
    lineOrder: int


class SyncTradeDocumentDto(BaseModel):
    """Fields beyond tradeDocumentId/status default blank so the same DTO covers CREATE/ISSUE/
    CONVERT/CANCEL events, mirroring SyncInvoiceDto's pattern."""
    tradeDocumentId: str
    documentType: str = ""
    documentNumber: str | None = None
    partyId: str = ""
    date: str = ""
    status: str = "DRAFT"
    sourceTradeDocumentId: str | None = None
    narration: str = ""
    lines: list[SyncTradeDocumentLineDto] = []


class SyncEvent(BaseModel):
    model_config = ConfigDict(extra="ignore")

    schemaVersion: int = 1
    eventId: str
    idempotencyKey: str
    companyId: str
    financialYearId: str = ""
    operation: str
    aggregateType: str
    aggregateId: str
    voucher: SyncVoucherDto | None = None
    journalLines: list[SyncJournalLineDto] = []
    stockLines: list[SyncStockLineDto] = []
    gstTransactions: list[SyncGstTransactionDto] = []
    settlements: list[SyncSettlementDto] = []
    ledger: SyncLedgerDto | None = None
    party: SyncPartyDto | None = None
    invoice: SyncInvoiceDto | None = None
    tradeDocument: SyncTradeDocumentDto | None = None


class SyncBatchRequest(BaseModel):
    companyId: str
    deviceId: str
    items: list["SyncEventEnvelope"]


class SyncEventEnvelope(BaseModel):
    """Matches Android's `OutboxSyncItemDto` - the outer transport wrapper. `payloadJson` inside it
    is the `SyncEvent` above, serialized as a string (exactly how OutboxSyncEntity.payloadJson is
    stored on-device) - decoded server-side rather than nested directly, so a malformed/older-schema
    payload for one item never breaks parsing of the rest of the batch."""
    syncId: str
    entityType: str
    entityId: str
    operation: str
    payloadJson: str
    idempotencyKey: str
    version: int = 1
    clientTimestamp: int


class SyncRejectionDto(BaseModel):
    syncId: str
    idempotencyKey: str
    reason: str
    conflictCode: str | None = None
    serverVersion: int | None = None


class SyncBatchResponse(BaseModel):
    success: bool
    processedCount: int
    processedSyncIds: list[str]
    rejections: list[SyncRejectionDto] = []
    serverTimestamp: int


SyncBatchRequest.model_rebuild()
