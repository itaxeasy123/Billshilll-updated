package com.example.accounting.automation.tasks

import java.util.UUID

enum class TaskFrequency {
    DAILY,
    MONTHLY,
    YEARLY,
    EVENT_DRIVEN
}

enum class TaskExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    SKIPPED
}

data class AutomationTaskResult(
    val taskId: String = UUID.randomUUID().toString(),
    val taskName: String,
    val frequency: TaskFrequency,
    val status: TaskExecutionStatus,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val itemsProcessed: Int = 0,
    val details: Map<String, String> = emptyMap()
)

interface AutomationTask {
    val taskId: String
    val name: String
    val frequency: TaskFrequency
    val isIdempotent: Boolean get() = true

    suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult
}
