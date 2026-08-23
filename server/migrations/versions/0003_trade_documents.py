"""Phase 7B - Document/Voucher Lifecycle Architecture. Purely additive: two new tables plus one
new nullable column on `invoices` - no existing row is rewritten, no existing column altered,
dropped, or renamed.

Revision ID: 0003
Revises: 0002
Create Date: 2026-08-22

"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("invoices", sa.Column("source_trade_document_id", sa.String, nullable=True))
    op.create_index("ix_invoices_source_trade_document_id", "invoices", ["source_trade_document_id"])

    op.create_table(
        "trade_documents",
        sa.Column("trade_document_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("financial_year_id", sa.String, nullable=False),
        sa.Column("document_type", sa.String, nullable=False),
        sa.Column("document_number", sa.String, nullable=False),
        sa.Column("party_id", sa.String, sa.ForeignKey("parties.party_id"), nullable=False, index=True),
        sa.Column("date", sa.String, nullable=False),
        sa.Column("status", sa.String, nullable=False, server_default="DRAFT"),
        sa.Column("source_trade_document_id", sa.String, nullable=True, index=True),
        sa.Column("narration", sa.String, nullable=False, server_default=""),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "trade_document_lines",
        sa.Column("line_id", sa.String, primary_key=True),
        sa.Column("trade_document_id", sa.String, sa.ForeignKey("trade_documents.trade_document_id"), nullable=False, index=True),
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
    op.drop_table("trade_document_lines")
    op.drop_table("trade_documents")
    op.drop_index("ix_invoices_source_trade_document_id", table_name="invoices")
    op.drop_column("invoices", "source_trade_document_id")
