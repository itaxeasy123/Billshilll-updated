package com.example.accounting.domain.reports

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.core.common.DrCr
import com.example.accounting.domain.accounting.AccountGroup
import com.example.accounting.domain.accounting.PrimaryGroup

/**
 * A group node with recursively (bottom-up) aggregated ledger balances - itself plus every
 * descendant, counted exactly once. Read model only; never persisted.
 */
data class GroupBalanceNode(
    val groupId: String,
    val groupName: String,
    val primaryGroup: PrimaryGroup,
    val parentGroupId: String?,
    val totalDebitPaise: Long,
    val totalCreditPaise: Long,
    val netBalancePaise: Long,
    val balanceType: DrCr,
    val children: List<GroupBalanceNode>
)

/**
 * Builds the recursive group hierarchy (Section 21/22 of the Phase 3 spec) that financial
 * statements aggregate through, instead of classifying ledgers by name/string matching.
 *
 * Ledger -> groupId -> Group -> PrimaryGroup: every ledger contributes its debit/credit total to
 * its OWN direct group only; parent groups pick that up automatically because aggregation walks
 * bottom-up (each group's total = its own direct ledgers + the sum of its already-aggregated
 * children), so nothing is ever double-counted between a parent and a child.
 */
object GroupAggregationEngine {

    /** A ledger's debit/credit contribution, attributed to its direct (immediate) groupId. */
    data class LedgerContribution(val groupId: String, val debitPaise: Long, val creditPaise: Long)

    /**
     * Returns the root nodes (groups with no parent) of the fully aggregated tree, or
     * [AppError.GroupHierarchyInvalid] if a cyclic parentGroupId relationship is detected -
     * a genuine data-integrity condition that must never be silently recursed through.
     */
    fun aggregate(groups: List<AccountGroup>, contributions: List<LedgerContribution>): AccountingResult<List<GroupBalanceNode>> {
        val groupsById = groups.associateBy { it.groupId }
        val childrenByParent = groups.filter { it.parentGroupId != null }.groupBy { it.parentGroupId }
        val directTotals: Map<String, Pair<Long, Long>> = contributions.groupBy { it.groupId }.mapValues { (_, list) ->
            list.sumOf { it.debitPaise } to list.sumOf { it.creditPaise }
        }

        for (group in groups) {
            val visited = mutableSetOf<String>()
            var current: AccountGroup? = group
            while (current != null) {
                if (!visited.add(current.groupId)) {
                    return AccountingResult.Failure(
                        AppError.GroupHierarchyInvalid("Cyclic group relationship detected involving group '${current.groupId}'.")
                    )
                }
                current = current.parentGroupId?.let { groupsById[it] }
            }
        }

        fun buildNode(group: AccountGroup): GroupBalanceNode {
            val children = (childrenByParent[group.groupId] ?: emptyList()).map { buildNode(it) }
            val (directDebit, directCredit) = directTotals[group.groupId] ?: (0L to 0L)
            val totalDebit = directDebit + children.sumOf { it.totalDebitPaise }
            val totalCredit = directCredit + children.sumOf { it.totalCreditPaise }
            val netSigned = totalDebit - totalCredit
            return GroupBalanceNode(
                groupId = group.groupId,
                groupName = group.name,
                primaryGroup = group.primaryGroup,
                parentGroupId = group.parentGroupId,
                totalDebitPaise = totalDebit,
                totalCreditPaise = totalCredit,
                netBalancePaise = kotlin.math.abs(netSigned),
                balanceType = if (netSigned >= 0) DrCr.DEBIT else DrCr.CREDIT,
                children = children
            )
        }

        val roots = groups.filter { it.parentGroupId == null }.map { buildNode(it) }
        return AccountingResult.Success(roots)
    }

    /** Depth-first search for a node by exact groupId anywhere in the aggregated forest. */
    fun findNode(roots: List<GroupBalanceNode>, groupId: String): GroupBalanceNode? {
        for (root in roots) {
            if (root.groupId == groupId) return root
            findNode(root.children, groupId)?.let { return it }
        }
        return null
    }
}
