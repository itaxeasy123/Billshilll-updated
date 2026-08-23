package com.example.accounting.core.sync

import android.content.Context
import android.util.Log
import com.example.accounting.core.network.ApiClient
import com.example.accounting.core.network.NetworkMonitor
import com.example.accounting.core.network.OutboxSyncItemDto
import com.example.accounting.core.network.SyncBatchRequest
import com.example.accounting.core.security.SecureStorage
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.AuditLogEntity
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.audit.AuditAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

sealed class SyncProcessStatus {
    object Idle : SyncProcessStatus()
    data class InProgress(val pendingCount: Int, val message: String) : SyncProcessStatus()
    data class Success(val processedCount: Int, val timestamp: Long) : SyncProcessStatus()
    data class Error(val message: String, val lastAttemptTime: Long) : SyncProcessStatus()
}

/**
 * OutboxProcessor manages the local SQLite outbox table for accounting actions,
 * ensuring eventual consistency and idempotent synchronization with the server-side Python API.
 */
class OutboxProcessor(
    private val context: Context,
    private val dao: AccountingDao,
    private val apiClient: ApiClient = ApiClient.getInstance(context),
    private val networkMonitor: NetworkMonitor = NetworkMonitor.getInstance(context),
    private val secureStorage: SecureStorage = SecureStorage.getInstance(context),
    private val syncEngine: SyncEngine = SyncEngine(dao)
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow<SyncProcessStatus>(SyncProcessStatus.Idle)
    val syncStatus: StateFlow<SyncProcessStatus> = _syncStatus.asStateFlow()

    init {
        // Auto-trigger sync when network reconnects
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    val activeCompId = secureStorage.getActiveCompanyId()
                    if (!activeCompId.isNullOrBlank()) {
                        processPendingOutbox(activeCompId)
                    }
                }
            }
        }
    }

    /**
     * Triggers asynchronous synchronization of pending outbox queue
     */
    fun triggerSync(companyId: String) {
        scope.launch {
            processPendingOutbox(companyId)
        }
    }

    /**
     * Real timed exponential backoff (Phase 6, Priority 6.3) - previously the only re-trigger was
     * network reconnect or a manual [triggerSync] call, so a batch that failed while still online
     * (e.g. a transient 5xx) never retried on its own. `2^retryCount x 1000ms`, capped at 60s,
     * matching the formula `docs/21_SYNC.md` already documented but no code implemented.
     */
    private fun scheduleBackoffRetry(companyId: String, retryCount: Int) {
        val delayMs = min(2.0.pow(retryCount).toLong() * 1000L, 60_000L)
        scope.launch {
            delay(delayMs)
            if (networkMonitor.checkCurrentNetworkState()) {
                processPendingOutbox(companyId)
            }
        }
    }

    /**
     * Executes outbox synchronization batch
     */
    suspend fun processPendingOutbox(companyId: String): Result<Int> {
        if (!networkMonitor.checkCurrentNetworkState()) {
            _syncStatus.value = SyncProcessStatus.Error("Device is offline. Changes are saved locally.", System.currentTimeMillis())
            return Result.failure(IllegalStateException("Device is offline"))
        }

        val pendingBatch = dao.getPendingOutboxBatch(companyId, batchSize = 30)
        if (pendingBatch.isEmpty()) {
            _syncStatus.value = SyncProcessStatus.Success(0, System.currentTimeMillis())
            return Result.success(0)
        }

        _syncStatus.value = SyncProcessStatus.InProgress(pendingBatch.size, "Synchronizing ${pendingBatch.size} pending accounting records...")

        return try {
            val dtoList = pendingBatch.map { entity ->
                OutboxSyncItemDto(
                    syncId = entity.syncId,
                    entityType = entity.entityType,
                    entityId = entity.entityId,
                    operation = entity.operation,
                    payloadJson = entity.payloadJson,
                    idempotencyKey = entity.idempotencyKey,
                    version = entity.version,
                    clientTimestamp = entity.createdAt
                )
            }

            val request = SyncBatchRequest(
                companyId = companyId,
                deviceId = secureStorage.getDeviceId(),
                items = dtoList
            )

            val batchIdempotencyKey = UUID.randomUUID().toString()
            val response = apiClient.apiService.syncOutboxBatch(batchIdempotencyKey, request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val processedSet = body.processedSyncIds.toSet()

                // Mark processed items as SYNCED
                for (item in pendingBatch) {
                    if (item.syncId in processedSet) {
                        dao.updateOutboxStatus(
                            syncId = item.syncId,
                            state = SyncState.SYNCED,
                            retries = item.retryCount,
                            error = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                // Handle rejections / conflicts
                for (rejection in body.rejections) {
                    val target = pendingBatch.find { it.syncId == rejection.syncId }
                    if (target != null) {
                        val isConflict = rejection.conflictCode != null
                        dao.updateOutboxStatus(
                            syncId = target.syncId,
                            state = if (isConflict) SyncState.CONFLICT else SyncState.FAILED,
                            retries = target.retryCount + 1,
                            error = rejection.reason,
                            updatedAt = System.currentTimeMillis()
                        )

                        // Audit sync conflict
                        dao.insertAuditLog(
                            AuditLogEntity(
                                logId = UUID.randomUUID().toString(),
                                companyId = companyId,
                                financialYearId = "",
                                action = AuditAction.UPDATE,
                                entityType = "OUTBOX_SYNC",
                                entityId = target.syncId,
                                description = "Sync conflict on ${target.entityType} ${target.entityId}: ${rejection.reason}",
                                performedBy = "OUTBOX_PROCESSOR",
                                timestamp = System.currentTimeMillis(),
                                payloadJson = "{\"idempotencyKey\":\"${target.idempotencyKey}\",\"reason\":\"${rejection.reason}\"}"
                            )
                        )
                    }
                }

                _syncStatus.value = SyncProcessStatus.Success(body.processedCount, System.currentTimeMillis())
                Result.success(body.processedCount)
            } else {
                val errorMsg = "Server sync failed: ${response.code()} ${response.message()}"
                Log.e("OutboxProcessor", errorMsg)

                // Reconnected to SyncEngine's retry-cap logic (Phase 6) - previously this
                // incremented retryCount unconditionally with no cap, so a permanently-broken item
                // would retry forever; recordFailure() marks it FAILED (stops retrying) once it
                // hits 5 attempts, matching what docs/21_SYNC.md already claimed but no code did.
                for (item in pendingBatch) {
                    syncEngine.recordFailure(item.syncId, item.retryCount, errorMsg)
                }
                scheduleBackoffRetry(companyId, pendingBatch.maxOf { it.retryCount } + 1)

                _syncStatus.value = SyncProcessStatus.Error(errorMsg, System.currentTimeMillis())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Network or serialization error during sync"
            Log.e("OutboxProcessor", "Sync exception: $errorMsg", e)

            for (item in pendingBatch) {
                syncEngine.recordFailure(item.syncId, item.retryCount, errorMsg)
            }
            scheduleBackoffRetry(companyId, pendingBatch.maxOf { it.retryCount } + 1)

            _syncStatus.value = SyncProcessStatus.Error(errorMsg, System.currentTimeMillis())
            Result.failure(e)
        }
    }
}
