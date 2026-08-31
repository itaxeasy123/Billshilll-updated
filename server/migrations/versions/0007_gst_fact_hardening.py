"""D1b (GST-Only Purchase + Sales/Purchase Return + GST Fact Hardening). Four new columns on
`gst_transactions`, mirroring the Android Room migration (18 -> 19) column-for-column and
backfill-for-backfill:

- `charge_type` (NOT NULL, default 'FORWARD_CHARGE'): every pre-existing row really was
  forward-charge (RCM did not exist for this table before this), so the default is a genuine fact.
- `supply_nature` (NOT NULL, default 'NORMAL'): backfilled from each row's own existing
  `supply_type` where unambiguous (INTRA_STATE/INTER_STATE -> NORMAL, EXPORT -> EXPORT, EXEMPT ->
  EXEMPT). A disclosed limitation: `supply_type` has always collapsed EXEMPT and NIL_RATED into one
  value, so a pre-existing EXEMPT row cannot be distinguished from a pre-existing NIL_RATED one
  after the fact - this backfill is the closest honest approximation available, not a guess
  presented as certain.
- `transaction_group_id` (NOT NULL, default ''): backfilled to `COALESCE(voucher_id,
  gst_transaction_id)` - the real voucher_id for every accounting-integrated row, or the row's own
  id as a single-row group for any pre-existing GST-only row (none in production per the D1a-era
  audit; still the honest fallback).
- `transaction_date` (nullable TEXT, ISO-8601): left NULL for every existing row - already
  accounting-integrated and already has an unambiguous date via `vouchers.date`.
- `party_gst_registration_status` (nullable TEXT): left NULL (UNKNOWN) for every existing row - no
  historical snapshot of this ever existed before this migration.

`op.batch_alter_table` used for parity with `0006_gst_only_sync.py` (SQLite dev/test database has
no in-place `ALTER COLUMN`/multi-statement `ADD COLUMN` guarantees some drivers need batched). No
data is deleted, dropped, or reinterpreted.

Revision ID: 0007
Revises: 0006
Create Date: 2026-08-30

"""
from alembic import op
import sqlalchemy as sa

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("gst_transactions") as batch_op:
        batch_op.add_column(sa.Column("charge_type", sa.String, nullable=False, server_default="FORWARD_CHARGE"))
        batch_op.add_column(sa.Column("supply_nature", sa.String, nullable=False, server_default="NORMAL"))
        batch_op.add_column(sa.Column("transaction_group_id", sa.String, nullable=False, server_default=""))
        batch_op.add_column(sa.Column("transaction_date", sa.String, nullable=True))
        batch_op.add_column(sa.Column("party_gst_registration_status", sa.String, nullable=True))

    op.execute("UPDATE gst_transactions SET supply_nature = 'EXPORT' WHERE supply_type = 'EXPORT'")
    op.execute("UPDATE gst_transactions SET supply_nature = 'EXEMPT' WHERE supply_type = 'EXEMPT'")
    op.execute(
        "UPDATE gst_transactions SET transaction_group_id = COALESCE(voucher_id, gst_transaction_id) "
        "WHERE transaction_group_id = ''"
    )
    with op.batch_alter_table("gst_transactions") as batch_op:
        batch_op.create_index("ix_gst_transactions_transaction_group_id", ["transaction_group_id"])


def downgrade() -> None:
    with op.batch_alter_table("gst_transactions") as batch_op:
        batch_op.drop_index("ix_gst_transactions_transaction_group_id")
        batch_op.drop_column("party_gst_registration_status")
        batch_op.drop_column("transaction_date")
        batch_op.drop_column("transaction_group_id")
        batch_op.drop_column("supply_nature")
        batch_op.drop_column("charge_type")
