# 45. Document Branding (Phase 7D)

## BusinessProfile / IndividualProfile

One `BusinessProfile` and (optionally) one `IndividualProfile` per companyId - document-branding
identity, structurally separate from `Company`. `Company`'s `gstin`/`pan`/`address`/etc. remain the
sole authoritative statutory source; a profile's own copies (`businessName`, `address`, `gstin`,
`pan`, ...) are independently editable *rendering* preferences - e.g. a shorter trading name on
invoices than the registered legal name - never a correction to Company's data. `assembleDocumentData`
falls back to the bare `Company` fields whenever a `BusinessProfile` field is blank or the profile
doesn't exist at all, so a document is always renderable even before a company sets one up.

`IndividualProfile` exists purely so a proprietor can render documents under a personal identity
distinct from an incorporated business identity - it does **not** change the accounting
capital-account naming rule (Section 7), which stays fully independent of both profiles. Which
profile a given render should prefer when both exist is a selection concern explicitly deferred to
Phase 7G/7J; `assembleDocumentData` defaults to `BusinessProfile` in this phase.

## DocumentAsset - a reference, never the image bytes

`DocumentAsset` (`assetId`, `companyId`, `type` - `LOGO`/`SIGNATURE`/`QR_CODE`, `storageReference`,
`checksum`, `mimeType`, `sizeBytes`) stores only a pointer to an app-private file (Android) - the
accounting database never holds a logo/signature/QR image's actual bytes (Section 8: "do not store
huge binary assets directly inside accounting tables"). `BusinessProfile.logoAssetId`/
`signatureAssetId`/`qrCodeAssetId` are nullable references into this table, resolved once at
document-assembly time into a `DocumentBrandingSnapshot` (`logoStorageReference`/
`signatureStorageReference`/`qrCodeStorageReference`) - a renderer never looks up an asset itself.

Uploading/storing the actual binary image bytes (camera capture, gallery picker, on-device file
storage wiring) is UI/Phase 7J territory - this phase builds the reference/metadata model only.
Syncing an asset's *binary content* across devices is a separate, larger concern than this phase's
scope cut already covers (see below) and is left as a documented future extension.

## Bank details / UPI / QR

`BusinessProfile.bankName`/`bankAccountNumber`/`bankIfsc`/`bankBranch`/`upiId` feed
`DocumentPaymentInfo` directly - plain strings, no validation beyond non-blank business name at
the profile level. A QR code image (e.g. a UPI payment QR) is just another `DocumentAsset` of type
`QR_CODE`, referenced the same way a logo is.

## Terms & Conditions

`BusinessProfile.termsAndConditions`/`IndividualProfile.termsAndConditions` are a distinct field
from `Invoice`/`Voucher.narration` - the structural audit found no existing "Terms and Conditions"
concept anywhere in the codebase, and `narration` is an established internal accounting-note
convention (also used on plain Journal/Payment/Receipt vouchers) that isn't the same thing as a
customer-facing boilerplate block. `assembleDocumentData` reads the profile's own field into
`DocumentData.terms`, never `narration`.

## Deliberately not decided here: the 7D vs. 7G boundary

The Phase 7 roadmap lists "Business/Individual Profile branding" as **Phase 7G**, separately from
7D's "Document Template Engine." This phase's `BusinessProfile`/`IndividualProfile` are scoped
narrowly to what document *rendering* needs (name, address, bank/UPI, logo/signature/QR references,
terms) - not a full business-identity model (constitution, registered filings address, multi-branch
identity, etc., which may be 7G's actual territory). Whether 7G extends these same tables
additively, or introduces a broader profile concept that *this* phase's profiles become a facet of,
is an open decision left for whoever scopes 7G - deliberately not resolved here, to avoid two
phases each adding a competing "logo"/"business name" field independently.

## Deliberately not decided here: sync

Per `docs/42_DOCUMENT_TEMPLATE_ARCHITECTURE.md`, `BusinessProfile`/`IndividualProfile`/
`DocumentAsset`/`DocumentTemplate` are not synced via the Outbox in this phase. On Android, a
company's branding/templates live only on the device that created them until a later phase wires
up `CREATE_BUSINESS_PROFILE`/`CREATE_DOCUMENT_TEMPLATE`/etc. `SyncOperation`s following the exact
pattern 7A/7B already established for Party/Invoice/TradeDocument. This is a scope cut made
explicitly to keep this already-large phase's surface area bounded to "get the rendering foundation
right" - not a claim that branding data should never sync.

## Security / isolation

Every `BusinessProfile`/`IndividualProfile`/`DocumentAsset`/`DocumentTemplate` row is company-scoped
and every read/write path filters by `companyId` - tested directly in both `Phase7DTestSuite.kt`
and `test_documents_7d.py` (a second company can neither read nor write another company's profile,
asset, or template; cross-tenant access at the API layer returns `403 TENANT_MISMATCH` when the
caller has no membership for the target company, and a plain "not found" when a genuinely-owned
different company is scoped away from another company's row).
