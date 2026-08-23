"""Phase 6 initial schema - mirrors app/infrastructure/database/models.py table-for-table.

Revision ID: 0001
Revises:
Create Date: 2026-08-22

"""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("user_id", sa.String, primary_key=True),
        sa.Column("email", sa.String, unique=True, index=True, nullable=False),
        sa.Column("hashed_password", sa.String, nullable=False),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "companies",
        sa.Column("company_id", sa.String, primary_key=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("trade_name", sa.String, nullable=False, server_default=""),
        sa.Column("gstin", sa.String, nullable=False, server_default=""),
        sa.Column("pan", sa.String, nullable=False, server_default=""),
        sa.Column("state_code", sa.String, nullable=False, server_default=""),
        sa.Column("state_name", sa.String, nullable=False, server_default=""),
        sa.Column("email", sa.String, nullable=False, server_default=""),
        sa.Column("phone", sa.String, nullable=False, server_default=""),
        sa.Column("address", sa.String, nullable=False, server_default=""),
        sa.Column("currency", sa.String, nullable=False, server_default="INR"),
        sa.Column("financial_year_start_month", sa.Integer, nullable=False, server_default="4"),
        sa.Column("accounting_mode", sa.String, nullable=False, server_default="ACCOUNT_ONLY"),
        sa.Column("business_type", sa.String, nullable=False, server_default="TRADING"),
        sa.Column("is_default", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "user_company_roles",
        sa.Column("id", sa.String, primary_key=True),
        sa.Column("user_id", sa.String, sa.ForeignKey("users.user_id"), nullable=False, index=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("role", sa.String, nullable=False),
        sa.UniqueConstraint("user_id", "company_id", name="uq_user_company"),
    )

    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.String, primary_key=True),
        sa.Column("user_id", sa.String, sa.ForeignKey("users.user_id"), nullable=False, index=True),
        sa.Column("token_hash", sa.String, unique=True, index=True, nullable=False),
        sa.Column("expires_at", sa.Integer, nullable=False),
        sa.Column("revoked", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "idempotency_keys",
        sa.Column("id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("idempotency_key", sa.String, nullable=False, index=True),
        sa.Column("response_json", sa.String, nullable=False),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.UniqueConstraint("company_id", "idempotency_key", name="uq_company_idempotency_key"),
    )

    op.create_table(
        "financial_years",
        sa.Column("financial_year_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("fy_code", sa.String, nullable=False),
        sa.Column("start_date", sa.String, nullable=False),
        sa.Column("end_date", sa.String, nullable=False),
        sa.Column("is_current", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("is_locked", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("locked_at", sa.Integer, nullable=True),
        sa.Column("locked_by", sa.String, nullable=True),
    )

    op.create_table(
        "accounting_periods",
        sa.Column("period_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, sa.ForeignKey("financial_years.financial_year_id"), nullable=False, index=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("start_date", sa.String, nullable=False),
        sa.Column("end_date", sa.String, nullable=False),
        sa.Column("status", sa.String, nullable=False, server_default="OPEN"),
        sa.Column("locked_at", sa.Integer, nullable=True),
        sa.Column("locked_by", sa.String, nullable=True),
    )

    op.create_table(
        "account_groups",
        sa.Column("group_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("primary_group", sa.String, nullable=False),
        sa.Column("parent_group_id", sa.String, nullable=True),
        sa.Column("is_system", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("affects_gross_profit", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("display_order", sa.Integer, nullable=False, server_default="0"),
    )

    op.create_table(
        "ledgers",
        sa.Column("ledger_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("group_id", sa.String, sa.ForeignKey("account_groups.group_id"), nullable=False, index=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("code", sa.String, nullable=False, server_default=""),
        sa.Column("opening_balance_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("opening_balance_type", sa.String, nullable=False, server_default="DEBIT"),
        sa.Column("current_balance_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("current_balance_type", sa.String, nullable=False, server_default="DEBIT"),
        sa.Column("gstin", sa.String, nullable=False, server_default=""),
        sa.Column("pan", sa.String, nullable=False, server_default=""),
        sa.Column("state_code", sa.String, nullable=False, server_default=""),
        sa.Column("email", sa.String, nullable=False, server_default=""),
        sa.Column("phone", sa.String, nullable=False, server_default=""),
        sa.Column("address", sa.String, nullable=False, server_default=""),
        sa.Column("bank_account_number", sa.String, nullable=False, server_default=""),
        sa.Column("bank_ifsc", sa.String, nullable=False, server_default=""),
        sa.Column("is_system", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("hsn_sac_code", sa.String, nullable=False, server_default=""),
        sa.Column("default_tax_rate", sa.Float, nullable=False, server_default="0"),
    )

    op.create_table(
        "vouchers",
        sa.Column("voucher_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, sa.ForeignKey("financial_years.financial_year_id"), nullable=False, index=True),
        sa.Column("voucher_number", sa.String, nullable=False),
        sa.Column("voucher_type", sa.String, nullable=False),
        sa.Column("date", sa.String, nullable=False),
        sa.Column("reference_number", sa.String, nullable=False, server_default=""),
        sa.Column("narration", sa.String, nullable=False, server_default=""),
        sa.Column("total_amount_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("is_posted", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("is_cancelled", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
        sa.Column("created_by", sa.String, nullable=False, server_default=""),
        sa.Column("party_gstin", sa.String, nullable=False, server_default=""),
        sa.Column("is_gst_applicable", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("reference_voucher_id", sa.String, nullable=True),
        sa.Column("payment_mode", sa.String, nullable=False, server_default=""),
        sa.UniqueConstraint("company_id", "financial_year_id", "voucher_number", name="uq_voucher_number"),
    )

    op.create_table(
        "journal_items",
        sa.Column("item_id", sa.String, primary_key=True),
        sa.Column("voucher_id", sa.String, sa.ForeignKey("vouchers.voucher_id"), nullable=False, index=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False),
        sa.Column("ledger_id", sa.String, sa.ForeignKey("ledgers.ledger_id"), nullable=False, index=True),
        sa.Column("type", sa.String, nullable=False),
        sa.Column("amount_paise", sa.Integer, nullable=False),
        sa.Column("narration", sa.String, nullable=False, server_default=""),
        sa.Column("line_order", sa.Integer, nullable=False, server_default="0"),
    )

    op.create_table(
        "stock_items",
        sa.Column("item_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("sku", sa.String, nullable=False, server_default=""),
        sa.Column("hsn_code", sa.String, nullable=False, server_default=""),
        sa.Column("unit", sa.String, nullable=False, server_default="Nos"),
        sa.Column("gst_rate_percent", sa.Float, nullable=False, server_default="0"),
        sa.Column("opening_quantity", sa.Integer, nullable=False, server_default="0"),
        sa.Column("opening_rate_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("current_quantity", sa.Integer, nullable=False, server_default="0"),
        sa.Column("standard_cost_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("standard_selling_price_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("current_avg_cost_paise", sa.Integer, nullable=False, server_default="0"),
    )

    op.create_table(
        "voucher_stock_lines",
        sa.Column("line_id", sa.String, primary_key=True),
        sa.Column("voucher_id", sa.String, sa.ForeignKey("vouchers.voucher_id"), nullable=False, index=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False),
        sa.Column("item_id", sa.String, sa.ForeignKey("stock_items.item_id"), nullable=False, index=True),
        sa.Column("direction", sa.String, nullable=False),
        sa.Column("quantity_raw", sa.Integer, nullable=False),
        sa.Column("rate_paise", sa.Integer, nullable=False),
        sa.Column("amount_paise", sa.Integer, nullable=False),
        sa.Column("line_order", sa.Integer, nullable=False, server_default="0"),
    )

    op.create_table(
        "stock_movements",
        sa.Column("movement_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False, index=True),
        sa.Column("item_id", sa.String, sa.ForeignKey("stock_items.item_id"), nullable=False, index=True),
        sa.Column("voucher_id", sa.String, sa.ForeignKey("vouchers.voucher_id"), nullable=True, index=True),
        sa.Column("date", sa.String, nullable=False, index=True),
        sa.Column("direction", sa.String, nullable=False),
        sa.Column("movement_type", sa.String, nullable=False),
        sa.Column("quantity_raw", sa.Integer, nullable=False),
        sa.Column("rate_paise", sa.Integer, nullable=False),
        sa.Column("amount_paise", sa.Integer, nullable=False),
        sa.Column("running_avg_cost_after_paise", sa.Integer, nullable=False),
        sa.Column("reference", sa.String, nullable=False, server_default=""),
        sa.Column("narration", sa.String, nullable=False, server_default=""),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("created_by", sa.String, nullable=False, server_default=""),
    )

    op.create_table(
        "gst_transactions",
        sa.Column("gst_transaction_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False, index=True),
        sa.Column("voucher_id", sa.String, sa.ForeignKey("vouchers.voucher_id"), nullable=False, index=True),
        sa.Column("voucher_type", sa.String, nullable=False),
        sa.Column("party_ledger_id", sa.String, nullable=False, index=True),
        sa.Column("party_gstin", sa.String, nullable=False, server_default=""),
        sa.Column("place_of_supply", sa.String, nullable=False, server_default=""),
        sa.Column("supply_type", sa.String, nullable=False),
        sa.Column("item_id", sa.String, nullable=True),
        sa.Column("hsn_sac_code", sa.String, nullable=False, server_default=""),
        sa.Column("quantity_raw", sa.Integer, nullable=True),
        sa.Column("taxable_amount_paise", sa.Integer, nullable=False),
        sa.Column("gst_rate_percent", sa.Float, nullable=False),
        sa.Column("cgst_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("sgst_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("igst_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("cess_paise", sa.Integer, nullable=False, server_default="0"),
        sa.Column("direction", sa.String, nullable=False, index=True),
        sa.Column("line_order", sa.Integer, nullable=False, server_default="0"),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "settlement_allocations",
        sa.Column("allocation_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False),
        sa.Column("settlement_voucher_id", sa.String, sa.ForeignKey("vouchers.voucher_id"), nullable=False, index=True),
        sa.Column("invoice_voucher_id", sa.String, nullable=True, index=True),
        sa.Column("allocated_amount_paise", sa.Integer, nullable=False),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "gst_filing_periods",
        sa.Column("filing_period_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("period_label", sa.String, nullable=False),
        sa.Column("start_date", sa.String, nullable=False),
        sa.Column("end_date", sa.String, nullable=False),
        sa.Column("is_locked", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("locked_at", sa.Integer, nullable=True),
        sa.Column("locked_by", sa.String, nullable=True),
    )

    op.create_table(
        "audit_logs",
        sa.Column("log_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False, server_default=""),
        sa.Column("action", sa.String, nullable=False),
        sa.Column("entity_type", sa.String, nullable=False),
        sa.Column("entity_id", sa.String, nullable=False),
        sa.Column("description", sa.String, nullable=False, server_default=""),
        sa.Column("performed_by", sa.String, nullable=False, server_default=""),
        sa.Column("timestamp", sa.Integer, nullable=False, index=True),
        sa.Column("payload_json", sa.String, nullable=False, server_default="{}"),
    )


def downgrade() -> None:
    for table in [
        "audit_logs", "gst_filing_periods", "settlement_allocations", "gst_transactions",
        "stock_movements", "voucher_stock_lines", "stock_items", "journal_items", "vouchers",
        "ledgers", "account_groups", "accounting_periods", "financial_years",
        "idempotency_keys", "refresh_tokens", "user_company_roles", "companies", "users",
    ]:
        op.drop_table(table)
