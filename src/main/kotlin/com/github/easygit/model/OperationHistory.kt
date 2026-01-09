package com.github.easygit.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 操作类型
 */
enum class OperationType(val displayName: String) {
    MERGE_TO_DEV("合并到 Dev"),
    MERGE_TO_TEST("合并到 Test"),
    MERGE_TO_MAIN("合并到 Main"),
    ONE_CLICK_MERGE("一键合并"),
    CREATE_TAG("打 Tag")
}

/**
 * 操作历史记录
 */
data class OperationHistory(
    val id: Long = System.currentTimeMillis(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val operationType: OperationType,
    val repositoryName: String,
    val repositoryPath: String,
    val sourceBranch: String,
    val targetBranch: String = "",
    val tagName: String = "",
    val success: Boolean,
    val message: String
) {
    fun getFormattedTime(): String {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    fun getDisplayText(): String {
        return buildString {
            append("[${getFormattedTime()}] ")
            append("$repositoryName - ")
            append("${operationType.displayName}: ")
            if (tagName.isNotEmpty()) {
                append(tagName)
            } else {
                append("$sourceBranch → $targetBranch")
            }
            append(if (success) " ✓" else " ✗")
        }
    }
}

/**
 * 操作历史状态（用于持久化）
 */
data class OperationHistoryState(
    var histories: MutableList<OperationHistoryEntry> = mutableListOf()
) {
    companion object {
        const val MAX_HISTORY_SIZE = 500
    }
}

/**
 * 操作历史条目（用于持久化，避免序列化问题）
 */
data class OperationHistoryEntry(
    var id: Long = 0,
    var timestamp: String = "",
    var operationType: String = "",
    var repositoryName: String = "",
    var repositoryPath: String = "",
    var sourceBranch: String = "",
    var targetBranch: String = "",
    var tagName: String = "",
    var success: Boolean = false,
    var message: String = ""
) {
    fun toOperationHistory(): OperationHistory {
        return OperationHistory(
            id = id,
            timestamp = LocalDateTime.parse(timestamp),
            operationType = OperationType.valueOf(operationType),
            repositoryName = repositoryName,
            repositoryPath = repositoryPath,
            sourceBranch = sourceBranch,
            targetBranch = targetBranch,
            tagName = tagName,
            success = success,
            message = message
        )
    }

    companion object {
        fun fromOperationHistory(history: OperationHistory): OperationHistoryEntry {
            return OperationHistoryEntry(
                id = history.id,
                timestamp = history.timestamp.toString(),
                operationType = history.operationType.name,
                repositoryName = history.repositoryName,
                repositoryPath = history.repositoryPath,
                sourceBranch = history.sourceBranch,
                targetBranch = history.targetBranch,
                tagName = history.tagName,
                success = history.success,
                message = history.message
            )
        }
    }
}
