"""
Server-side port of the Android `GroupAggregationEngine` (Phase 7C) - the same recursive,
bottom-up group-hierarchy rollup (each group's total = its own direct ledgers + the sum of its
already-aggregated children, so nothing is ever double-counted between a parent and a child),
with the same cycle-detection guard. Pure function: takes plain (group, contribution) data the
caller already has on hand, never reads a database itself - same shape as
`domain/accounting/allocation.py`. P&L/Balance Sheet on the server cannot be built correctly
without this: Sales vs. Direct vs. Indirect Income can't be separated without walking the real
group tree, exactly as the Android side requires the Kotlin `GroupAggregationEngine` for the same
reason.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class LedgerContribution:
    group_id: str
    debit_paise: int
    credit_paise: int


@dataclass
class GroupBalanceNode:
    group_id: str
    group_name: str
    primary_group: str
    parent_group_id: str | None
    total_debit_paise: int
    total_credit_paise: int
    net_balance_paise: int
    balance_type: str  # "DEBIT" / "CREDIT"
    children: list["GroupBalanceNode"] = field(default_factory=list)


class GroupHierarchyInvalid(Exception):
    """Cyclic parentGroupId relationship - a genuine data-integrity condition, never silently
    recursed through. Mirrors the Kotlin side's `AppError.GroupHierarchyInvalid`."""


def aggregate(groups: list, contributions: list[LedgerContribution]) -> list[GroupBalanceNode]:
    """`groups` is any iterable of objects with `.group_id`/`.name`/`.primary_group`/
    `.parent_group_id` attributes (the SQLAlchemy `Group` model already has exactly these)."""
    groups_by_id = {g.group_id: g for g in groups}
    children_by_parent: dict[str, list] = {}
    for g in groups:
        if g.parent_group_id is not None:
            children_by_parent.setdefault(g.parent_group_id, []).append(g)

    direct_totals: dict[str, tuple[int, int]] = {}
    for c in contributions:
        d, cr = direct_totals.get(c.group_id, (0, 0))
        direct_totals[c.group_id] = (d + c.debit_paise, cr + c.credit_paise)

    for g in groups:
        visited: set[str] = set()
        current = g
        while current is not None:
            if current.group_id in visited:
                raise GroupHierarchyInvalid(f"Cyclic group relationship detected involving group '{current.group_id}'.")
            visited.add(current.group_id)
            current = groups_by_id.get(current.parent_group_id) if current.parent_group_id else None

    def build_node(group) -> GroupBalanceNode:
        child_nodes = [build_node(c) for c in children_by_parent.get(group.group_id, [])]
        direct_debit, direct_credit = direct_totals.get(group.group_id, (0, 0))
        total_debit = direct_debit + sum(c.total_debit_paise for c in child_nodes)
        total_credit = direct_credit + sum(c.total_credit_paise for c in child_nodes)
        net_signed = total_debit - total_credit
        return GroupBalanceNode(
            group_id=group.group_id,
            group_name=group.name,
            primary_group=group.primary_group,
            parent_group_id=group.parent_group_id,
            total_debit_paise=total_debit,
            total_credit_paise=total_credit,
            net_balance_paise=abs(net_signed),
            balance_type="DEBIT" if net_signed >= 0 else "CREDIT",
            children=child_nodes,
        )

    return [build_node(g) for g in groups if g.parent_group_id is None]


def find_node(roots: list[GroupBalanceNode], group_id: str) -> GroupBalanceNode | None:
    for root in roots:
        if root.group_id == group_id:
            return root
        found = find_node(root.children, group_id)
        if found is not None:
            return found
    return None
