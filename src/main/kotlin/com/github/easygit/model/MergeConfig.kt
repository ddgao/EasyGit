package com.github.easygit.model

/**
 * 合并配置
 */
data class MergeConfig(
    val sourceBranch: String,
    val targetBranch: String,
    val autoPush: Boolean = false,
    val fetchBeforeMerge: Boolean = true
)

/**
 * 合并目标环境
 */
enum class MergeTarget(val branchName: String, val displayName: String) {
    DEV("dev", "开发环境"),
    TEST("test", "测试环境"),
    MAIN("main", "生产环境")
}

/**
 * 合并结果
 */
sealed class MergeResult {
    data class Success(val message: String = "合并成功") : MergeResult()
    data class Conflict(val conflictFiles: List<String>) : MergeResult()
    data class Error(val message: String) : MergeResult()

    fun isSuccess(): Boolean = this is Success
}

/**
 * 批量操作结果
 */
data class BatchOperationResult(
    val repositoryName: String,
    val repositoryPath: String,
    val success: Boolean,
    val message: String,
    val tagName: String? = null
)
