"""Phase 7A - Party + Invoice Domain Foundation. Purely additive: three new tables only, none of
which alter, drop, or rename any existing column or row from 0001.

Revision ID: 0002
Revises: 0001
Create Date: 2026-08-22

"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "parties",
        sa.Column("party_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("ledger_id", sa.String, sa.ForeignKey("ledgers.ledger_id"), nullable=False, unique=True, index=True),
        sa.Column("role", sa.String, nullable=False),
        sa.Column("entity_type", sa.String, nullable=False),
        sa.Column("display_name", sa.String, nullable=False),
        sa.Column("contact_name", sa.String, nullable=False, server_default=""),
        sa.Column("credit_limit_paise", sa.Integer, nullable=True),
        sa.Column("payment_terms_type", sa.String, nullable=False, server_default="DUE_ON_RECEIPT"),
        sa.Column("payment_terms_custom_days", sa.Integer, nullable=True),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
    )

    op.create_table(
        "invoices",
        sa.Column("invoice_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False),
        sa.Column("invoice_type", sa.String, nullable=False),
        sa.Column("invoice_number", sa.String, nullable=True),
        sa.Column("party_id", sa.String, sa.ForeignKey("parties.party_id"), nullable=False, index=True),
        sa.Column("date", sa.String, nullable=False),
        sa.Column("due_date", sa.String, nullable=True),
        sa.Column("voucher_id", sa.String, nullable=True, unique=True, index=True),
        sa.Column("reference_invoice_id", sa.String, nullable=True, index=True),
        sa.Column("narration", sa.String, nullable=False, server_default=""),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "invoice_lines",
        sa.Column("line_id", sa.String, primary_key=True),
        sa.Column("invoice_id", sa.String, sa.ForeignKey("invoices.invoice_id"), nullable=False, index=True),
        sa.Column("item_id", sa.String, nullable=False),
        sa.Column("item_name", sa.String, nullable=False),
        sa.Column("hsn_sac_code", sa.String, nullable=False, server_default=""),
        sa.Column("quantity_raw", sa.Integer, nullable=False),
        sa.Column("rate_paise", sa.Integer, nullable=False),
        sa.Column("gst_rate_percent", sa.Float, nullable=False, server_default="0"),
        sa.Column("cess_rate_percent", sa.Float, nullable=False, server_default="0"),
        sa.Column("line_order", sa.Integer, nullable=False, server_default="0"),
    )


def downgrade() -> None:
    for table in ["invoice_lines", "invoices", "parties"]:
        op.drop_table(table)
