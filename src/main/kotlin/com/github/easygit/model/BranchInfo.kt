package com.github.easygit.model

import java.time.LocalDateTime

/**
 * 分支信息数据模型
 */
data class BranchInfo(
    /** 分支名称（不含 origin/ 前缀） */
    val name: String,
    /** 远程分支全名（如 origin/feature-xxx） */
    val remoteName: String,
    /** 最后提交时间 */
    val lastCommitDate: LocalDateTime,
    /** 最后提交作者 */
    val author: String,
    /** 是否已合并到各个保护分支 */
    val mergedTo: Map<String, Boolean> = emptyMap(),
    /** 最近的提交记录 */
    val lastCommits: List<CommitInfo> = emptyList(),
    /** 是否是受保护分支 */
    val isProtected: Boolean = false
) {
    /**
     * 是否已合并到任意保护分支
     */
    fun isMergedToAny(): Boolean = mergedTo.values.any { it }

    /**
     * 获取已合并到的分支列表
     */
    fun getMergedBranches(): List<String> = mergedTo.filter { it.value }.keys.toList()

    /**
     * 获取距今天数
     */
    fun getDaysAgo(): Long {
        val now = LocalDateTime.now()
        return java.time.Duration.between(lastCommitDate, now).toDays()
    }
}

/**
 * 提交信息
 */
data class CommitInfo(
    /** 提交哈希（短格式） */
    val hash: String,
    /** 提交信息 */
    val message: String,
    /** 作者 */
    val author: String,
    /** 相对时间（如 "3 days ago"） */
    val relativeDate: String
)

/**
 * 分支删除结果
 */
sealed class BranchDeleteResult {
    /** 删除成功 */
    data class Success(
        val branchName: String,
        val deletedLocal: Boolean,
        val deletedRemote: Boolean
    ) : BranchDeleteResult()

    /** 删除失败 */
    data class Error(
        val branchName: String,
        val errorMessage: String
    ) : BranchDeleteResult()

    /** 试运行（未实际删除） */
    data class DryRun(
        val branchName: String
    ) : BranchDeleteResult()
}

/**
 * 分支筛选配置
 */
data class BranchFilterConfig(
    /** 时间范围 - 开始日期 */
    val startDate: LocalDateTime? = null,
    /** 时间范围 - 结束日期 */
    val endDate: LocalDateTime? = null,
    /** 过期月数（N 个月前的分支） */
    val monthsOld: Int? = null,
    /** 只显示已合并的分支 */
    val mergedOnly: Boolean = false,
    /** 只显示未合并的分支 */
    val unmergedOnly: Boolean = false,
    /** 按作者筛选 */
    val author: String? = null,
    /** 搜索关键词（分支名） */
    val searchKeyword: String? = null
)
