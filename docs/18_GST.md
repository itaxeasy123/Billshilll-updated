# 18. Indian Goods and Services Tax (GST) Architecture

## Statutory Rules
- **Intra-State Supply** ($\text{State}_{\text{Supplier}} == \text{State}_{\text{PlaceOfSupply}}$):
  - Split equally into **CGST** and **SGST** (e.g. 18% -> 9% CGST + 9% SGST).
- **Inter-State Supply** ($\text{State}_{\text{Supplier}} \neq \text{State}_{\text{PlaceOfSupply}}$):
  - 100% charged to **IGST** (e.g. 18% IGST).

## GSTIN Validation
- 15-character statutory format: `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`.

## Reports & Compliance
- GSTR-1 (Outward Supplies Register).
- GSTR-3B (Summary Monthly Tax Returns).
- Reverse Charge Mechanism (RCM) support for unregistered vendor expenses.
