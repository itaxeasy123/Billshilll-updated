"""Phase 7D - Document Template & Rendering Architecture. Purely additive: five new tables, none
of which alter, drop, or rename any existing column or row. None of these tables are read by
`apply_voucher_event` or any GST/inventory/settlement command - this migration cannot affect any
accounting calculation.

Revision ID: 0004
Revises: 0003
Create Date: 2026-08-22

"""
from alembic import op
import sqlalchemy as sa

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "document_templates",
        sa.Column("id", sa.String, primary_key=True),
        sa.Column("template_id", sa.String, nullable=False, index=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("document_type", sa.String, nullable=False, index=True),
        sa.Column("template_name", sa.String, nullable=False),
        sa.Column("version", sa.Integer, nullable=False),
        sa.Column("status", sa.String, nullable=False, server_default="ACTIVE"),
        sa.Column("is_default", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("config_json", sa.String, nullable=False),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "business_profiles",
        sa.Column("business_profile_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, unique=True, index=True),
        sa.Column("business_name", sa.String, nullable=False),
        sa.Column("legal_name", sa.String, nullable=False, server_default=""),
        sa.Column("address", sa.String, nullable=False, server_default=""),
        sa.Column("phone", sa.String, nullable=False, server_default=""),
        sa.Column("email", sa.String, nullable=False, server_default=""),
        sa.Column("website", sa.String, nullable=False, server_default=""),
        sa.Column("gstin", sa.String, nullable=False, server_default=""),
        sa.Column("pan", sa.String, nullable=False, server_default=""),
        sa.Column("logo_asset_id", sa.String, nullable=True),
        sa.Column("bank_name", sa.String, nullable=False, server_default=""),
        sa.Column("bank_account_number", sa.String, nullable=False, server_default=""),
        sa.Column("bank_ifsc", sa.String, nullable=False, server_default=""),
        sa.Column("bank_branch", sa.String, nullable=False, server_default=""),
        sa.Column("upi_id", sa.String, nullable=False, server_default=""),
        sa.Column("qr_code_asset_id", sa.String, nullable=True),
        sa.Column("signature_asset_id", sa.String, nullable=True),
        sa.Column("terms_and_conditions", sa.String, nullable=False, server_default=""),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "individual_profiles",
        sa.Column("individual_profile_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, unique=True, index=True),
        sa.Column("name", sa.String, nullable=False),
        sa.Column("address", sa.String, nullable=False, server_default=""),
        sa.Column("pan", sa.String, nullable=False, server_default=""),
        sa.Column("phone", sa.String, nullable=False, server_default=""),
        sa.Column("email", sa.String, nullable=False, server_default=""),
        sa.Column("signature_asset_id", sa.String, nullable=True),
        sa.Column("terms_and_conditions", sa.String, nullable=False, server_default=""),
        sa.Column("created_at", sa.Integer, nullable=False),
        sa.Column("updated_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "document_assets",
        sa.Column("asset_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("type", sa.String, nullable=False),
        sa.Column("storage_reference", sa.String, nullable=False),
        sa.Column("checksum", sa.String, nullable=False, server_default=""),
        sa.Column("mime_type", sa.String, nullable=False, server_default=""),
        sa.Column("size_bytes", sa.Integer, nullable=False, server_default="0"),
        sa.Column("created_at", sa.Integer, nullable=False),
    )

    op.create_table(
        "rendered_document_records",
        sa.Column("record_id", sa.String, primary_key=True),
        sa.Column("company_id", sa.String, sa.ForeignKey("companies.company_id"), nullable=False, index=True),
        sa.Column("document_id", sa.String, nullable=False, index=True),
        sa.Column("document_type", sa.String, nullable=False),
        sa.Column("template_id", sa.String, nullable=False),
        sa.Column("template_version", sa.Integer, nullable=False),
        sa.Column("format", sa.String, nullable=False),
        sa.Column("storage_reference", sa.String, nullable=True),
        sa.Column("generated_at", sa.Integer, nullable=False),
    )


def downgrade() -> None:
    op.drop_table("rendered_document_records")
    op.drop_table("document_assets")
    op.drop_table("individual_profiles")
    op.drop_table("business_profiles")
    op.drop_table("document_templates")
