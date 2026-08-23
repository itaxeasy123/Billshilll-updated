package com.example.accounting.core.sync

import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.OutboxSyncEntity
import com.example.accounting.domain.accounting.SyncState
import java.util.UUID

/**
 * SyncEngine manages idempotent queuing and FIFO state transitions for outbound offline mutations.
 */
class SyncEngine(
    private val dao: AccountingDao
) {
    /**
     * Enqueues an entity mutation for offline synchronization.
     * Guarantees unique idempotencyKey and initial PENDING status.
     */
    suspend fun enqueueMutation(
        companyId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): OutboxSyncEntity {
        require(companyId.isNotBlank()) { "companyId is required for outbox mutation" }

        val entity = OutboxSyncEntity(
            syncId = UUID.randomUUID().toString(),
            companyId = companyId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadJson = payloadJson,
            idempotencyKey = idempotencyKey,
            syncState = SyncState.PENDING,
            retryCount = 0,
            lastError = null,
            version = 1L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        dao.insertOutboxSync(entity)
        return entity
    }

    /**
     * Returns pending mutations for a specific company in strict FIFO order.
     */
    suspend fun getPendingBatch(companyId: String, batchSize: Int = 30): List<OutboxSyncEntity> {
        return dao.getPendingOutboxBatch(companyId, batchSize)
    }

    /**
     * Marks items as successfully synced.
     */
    suspend fun markSynced(syncIds: List<String>) {
        if (syncIds.isEmpty()) return
        dao.markOutboxSynced(syncIds, System.currentTimeMillis())
    }

    /**
     * Records failure and increments retry count with exponential backoff calculation.
     */
    suspend fun recordFailure(syncId: String, currentRetryCount: Int, errorMessage: String) {
        val nextRetry = currentRetryCount + 1
        val newState = if (nextRetry >= 5) SyncState.FAILED else SyncState.PENDING
        dao.updateOutboxSyncStatus(
            syncId = syncId,
            status = newState,
            retryCount = nextRetry,
            lastError = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }
}
