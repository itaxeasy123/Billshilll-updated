"""GST-Only Sync Path. The only change is `gst_transactions.voucher_id` NOT NULL -> nullable, so
a GST-only company's transaction can be persisted with no accounting Voucher at all - mirrors the
Android Room migration (10 -> 11) that made the same relaxation. `op.batch_alter_table` is used
because SQLite (this project's dev/test database) has no `ALTER COLUMN`; Alembic's batch mode
handles the standard recreate-table procedure automatically on SQLite and emits a plain
`ALTER COLUMN` on dialects that support it directly (e.g. Postgres). No data is deleted, dropped,
or reinterpreted - every existing row (always a real voucher_id) is preserved unchanged.

Revision ID: 0006
Revises: 0005
Create Date: 2026-08-26

"""
from alembic import op
import sqlalchemy as sa

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("gst_transactions") as batch_op:
        batch_op.alter_column("voucher_id", existing_type=sa.String, nullable=True)


def downgrade() -> None:
    with op.batch_alter_table("gst_transactions") as batch_op:
        batch_op.alter_column("voucher_id", existing_type=sa.String, nullable=False)
