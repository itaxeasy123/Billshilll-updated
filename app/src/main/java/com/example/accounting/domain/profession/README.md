# Business/Profession Master (Phase 7J)

## Status: architecture and models only

`BusinessProfession.kt` - `BusinessProfession` (an open, extensible data record, not a closed
enum) + `BusinessProfessionCategory` + an illustrative `StandardBusinessProfessions` catalog
(Retailer, Wholesaler, Doctor, Engineer, Goldsmith, Contractor). No wiring into `Company` or
`BusinessProfile`, no UI, no persistence.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this came out of.

## Boundary

Carries no GST rate or tax field of any kind - a profession is classification context for a
future GST/ITR module, never a source of tax truth. Extensible by construction: a new profession
is just a new `BusinessProfession(...)` value, never a change to this file.
