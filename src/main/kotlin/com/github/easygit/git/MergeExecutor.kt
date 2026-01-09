package com.github.easygit.git

import com.github.easygit.model.BatchOperationResult
import com.github.easygit.model.MergeResult
import com.github.easygit.model.MergeTarget
import com.github.easygit.model.OperationHistory
import com.github.easygit.model.OperationType
import com.github.easygit.service.HistoryService
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository

/**
 * 合并执行器
 * 负责执行分支合并流程
 */
class MergeExecutor(private val project: Project) {

    private val gitOps = GitOperations(project)
    private val settings = EasyGitSettings.getInstance()
    private val historyService = ApplicationManager.getApplication().getService(HistoryService::class.java)

    /**
     * 执行单次合并
     * @param repository 仓库
     * @param sourceBranch 源分支（功能分支）
     * @param targetBranch 目标分支（如 dev, test, main）
     * @param autoPush 是否自动推送（默认 true）
     * @param fetchFirst 是否先 fetch
     * @param switchBackAfterMerge 是否在合并后切换回源分支（默认 true）
     * @return 合并结果
     */
    fun executeMerge(
        repository: GitRepository,
        sourceBranch: String,
        targetBranch: String,
        autoPush: Boolean = true,
        fetchFirst: Boolean = settings.state.autoFetchBeforeMerge,
        switchBackAfterMerge: Boolean = true
    ): BatchOperationResult {
        val repoName = repository.root.name
        // 记录当前分支，用于合并后切换回来
        val originalBranch = gitOps.getCurrentBranch(repository) ?: sourceBranch

        try {
            // 1. 检查是否有未提交的更改
            if (gitOps.hasUncommittedChanges(repository)) {
                return BatchOperationResult(
                    repositoryName = repoName,
                    repositoryPath = repository.root.path,
                    success = false,
                    message = "有未提交的更改，请先提交或暂存"
                )
            }

            // 2. Fetch 最新代码
            if (fetchFirst) {
                val fetchResult = gitOps.fetch(repository)
                if (!fetchResult.success()) {
                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = false,
                        message = "Fetch 失败: ${fetchResult.errorOutputAsJoinedString}"
                    )
                }
            }

            // 3. 切换到目标分支
            val checkoutResult = gitOps.checkout(repository, targetBranch)
            if (!checkoutResult.success()) {
                // 尝试从远程检出
                val remoteCheckout = gitOps.checkout(repository, "origin/$targetBranch")
                if (!remoteCheckout.success()) {
                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = false,
                        message = "切换到 $targetBranch 分支失败: ${checkoutResult.errorOutputAsJoinedString}"
                    )
                }
            }

            // 4. 拉取目标分支最新代码
            val pullResult = gitOps.pull(repository, targetBranch)
            if (!pullResult.success() && !pullResult.errorOutputAsJoinedString.contains("Already up to date")) {
                // Pull 失败但不是因为已经是最新的
                if (!pullResult.errorOutputAsJoinedString.contains("There is no tracking information")) {
                    // 切换回原分支
                    if (switchBackAfterMerge) {
                        gitOps.checkout(repository, originalBranch)
                    }
                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = false,
                        message = "拉取 $targetBranch 最新代码失败: ${pullResult.errorOutputAsJoinedString}"
                    )
                }
            }

            // 5. 执行合并
            val mergeResult = gitOps.mergeWithResult(repository, sourceBranch)

            when (mergeResult) {
                is MergeResult.Success -> {
                    // 6. 推送（如果需要）
                    if (autoPush) {
                        val pushResult = gitOps.push(repository)
                        if (!pushResult.success()) {
                            // 切换回原分支
                            if (switchBackAfterMerge) {
                                gitOps.checkout(repository, originalBranch)
                            }
                            return BatchOperationResult(
                                repositoryName = repoName,
                                repositoryPath = repository.root.path,
                                success = false,
                                message = "合并成功但推送失败: ${pushResult.errorOutputAsJoinedString}"
                            )
                        }
                    }

                    // 记录历史
                    recordHistory(repository, sourceBranch, targetBranch, true, "合并成功")

                    // 7. 切换回原分支
                    if (switchBackAfterMerge) {
                        val switchBackResult = gitOps.checkout(repository, originalBranch)
                        val switchBackMsg = if (switchBackResult.success()) {
                            "，已切回 $originalBranch"
                        } else {
                            "，但切回 $originalBranch 失败"
                        }
                        return BatchOperationResult(
                            repositoryName = repoName,
                            repositoryPath = repository.root.path,
                            success = true,
                            message = if (autoPush) "合并并推送成功$switchBackMsg" else "合并成功$switchBackMsg"
                        )
                    }

                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = true,
                        message = if (autoPush) "合并并推送成功" else "合并成功"
                    )
                }

                is MergeResult.Conflict -> {
                    // 记录历史
                    recordHistory(repository, sourceBranch, targetBranch, false, "存在冲突")

                    // 冲突时不切换回原分支，让用户在目标分支解决冲突
                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = false,
                        message = "合并冲突，请在 $targetBranch 分支手动解决:\n${mergeResult.conflictFiles.joinToString("\n")}"
                    )
                }

                is MergeResult.Error -> {
                    // 记录历史
                    recordHistory(repository, sourceBranch, targetBranch, false, mergeResult.message)

                    // 切换回原分支
                    if (switchBackAfterMerge) {
                        gitOps.checkout(repository, originalBranch)
                    }

                    return BatchOperationResult(
                        repositoryName = repoName,
                        repositoryPath = repository.root.path,
                        success = false,
                        message = "合并失败: ${mergeResult.message}"
                    )
                }
            }
        } catch (e: Exception) {
            // 异常时尝试切换回原分支
            if (switchBackAfterMerge) {
                try {
                    gitOps.checkout(repository, originalBranch)
                } catch (_: Exception) {
                    // 忽略切换失败
                }
            }
            return BatchOperationResult(
                repositoryName = repoName,
                repositoryPath = repository.root.path,
                success = false,
                message = "执行异常: ${e.message}"
            )
        }
    }

    /**
     * 合并到指定环境
     */
    fun mergeToTarget(
        repository: GitRepository,
        sourceBranch: String,
        target: MergeTarget,
        autoPush: Boolean = true,
        switchBackAfterMerge: Boolean = true
    ): BatchOperationResult {
        return executeMerge(repository, sourceBranch, target.branchName, autoPush, switchBackAfterMerge = switchBackAfterMerge)
    }

    /**
     * 一键合并到所有环境
     * dev -> test -> main
     * 注意：一键合并时中间步骤不切换回原分支，只在最后切回
     */
    fun oneClickMerge(
        repository: GitRepository,
        sourceBranch: String,
        autoPush: Boolean = true,
        switchBackAfterMerge: Boolean = true
    ): List<BatchOperationResult> {
        val results = mutableListOf<BatchOperationResult>()
        val targets = listOf(MergeTarget.DEV, MergeTarget.TEST, MergeTarget.MAIN)

        for ((index, target) in targets.withIndex()) {
            val isLastTarget = index == targets.size - 1
            // 只在最后一个目标时切换回原分支
            val result = mergeToTarget(
                repository,
                sourceBranch,
                target,
                autoPush,
                switchBackAfterMerge = isLastTarget && switchBackAfterMerge
            )
            results.add(result)

            // 如果某一步失败，停止后续操作，但仍然切换回原分支
            if (!result.success) {
                if (switchBackAfterMerge) {
                    gitOps.checkout(repository, sourceBranch)
                }
                break
            }
        }

        return results
    }

    /**
     * 批量合并多个仓库
     */
    fun batchMerge(
        repositories: List<GitRepository>,
        sourceBranch: String,
        targetBranch: String,
        autoPush: Boolean = true,
        switchBackAfterMerge: Boolean = true
    ): List<BatchOperationResult> {
        return repositories.map { repo ->
            executeMerge(repo, sourceBranch, targetBranch, autoPush, switchBackAfterMerge = switchBackAfterMerge)
        }
    }

    /**
     * 批量一键合并
     */
    fun batchOneClickMerge(
        repositories: List<GitRepository>,
        sourceBranch: String,
        autoPush: Boolean = true,
        switchBackAfterMerge: Boolean = true
    ): Map<String, List<BatchOperationResult>> {
        return repositories.associate { repo ->
            repo.root.name to oneClickMerge(repo, sourceBranch, autoPush, switchBackAfterMerge)
        }
    }

    /**
     * 切换回功能分支
     */
    fun switchBackToSourceBranch(repository: GitRepository, sourceBranch: String): Boolean {
        val result = gitOps.checkout(repository, sourceBranch)
        return result.success()
    }

    /**
     * 记录操作历史
     */
    private fun recordHistory(
        repository: GitRepository,
        sourceBranch: String,
        targetBranch: String,
        success: Boolean,
        message: String
    ) {
        val operationType = when (targetBranch) {
            "dev" -> OperationType.MERGE_TO_DEV
            "test" -> OperationType.MERGE_TO_TEST
            "main" -> OperationType.MERGE_TO_MAIN
            else -> OperationType.MERGE_TO_DEV
        }

        val history = OperationHistory(
            operationType = operationType,
            repositoryName = repository.root.name,
            repositoryPath = repository.root.path,
            sourceBranch = sourceBranch,
            targetBranch = targetBranch,
            success = success,
            message = message
        )

        historyService.addHistory(history)
    }
}
