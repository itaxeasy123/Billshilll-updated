# Sandbox e-Invoice Services (future)

## Status: not implemented

`SandboxProviderAdapter.requestEInvoiceIrn` (in the parent `sandbox/` package) is the only
e-Invoice contract that exists today - given an existing, already-posted
`domain.invoice.Invoice` (by `invoiceId`), it defines what requesting an IRN would return
(`EInvoiceIrnResult`: `irn`, `ackNumber`, `ackDate`, `signedQrCode`, `status`). No actual IRP
(Invoice Registration Portal) call, payload assembly, or storage of a returned IRN is implemented.

## Future scope

- Assembling the actual e-Invoice JSON payload from an `Invoice`'s existing lines/GST facts
  (`GstTransaction`) - the interface deliberately does not model that schema itself.
- Persisting a generated IRN/QR code against its Invoice (would need a new, additive
  field/table - e.g. mirroring how `RenderedDocumentRecord` tracks a document render without
  touching the Invoice/Voucher tables themselves).
- Feeding the signed QR code into Phase 7D's existing document-rendering pipeline so it can be
  printed on the invoice.
- IRN cancellation (the government-side action `EInvoiceIrnStatus.CANCELLED` records the result
  of, not a contract that exists yet).

## Boundary

Requesting or receiving an IRN must never change the underlying `Invoice`'s accounting effect -
the `Voucher` it's linked to was already posted through the normal path before an IRN is ever
requested. An IRN is metadata *about* an already-final invoice, never a trigger for creating or
altering one.
