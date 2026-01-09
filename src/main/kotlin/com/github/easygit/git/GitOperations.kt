package com.github.easygit.git

import com.github.easygit.model.MergeResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Git 操作封装类
 * 提供所有 Git 操作的底层实现
 */
class GitOperations(private val project: Project) {

    private val git: Git = Git.getInstance()

    // ==================== Fetch/Pull 操作 ====================

    /**
     * Fetch 远程仓库
     */
    fun fetch(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.FETCH)
        handler.addParameters("--all", "--prune")
        return git.runCommand(handler)
    }

    /**
     * Pull 远程分支
     */
    fun pull(repository: GitRepository, remoteBranch: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PULL)
        handler.addParameters("origin", remoteBranch)
        return git.runCommand(handler)
    }

    // ==================== 分支操作 ====================

    /**
     * 切换分支
     */
    fun checkout(repository: GitRepository, branchName: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.CHECKOUT)
        handler.addParameters(branchName)
        return git.runCommand(handler)
    }

    /**
     * 创建并切换到新分支
     */
    fun checkoutNewBranch(repository: GitRepository, branchName: String, startPoint: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.CHECKOUT)
        handler.addParameters("-b", branchName, startPoint)
        return git.runCommand(handler)
    }

    /**
     * 获取当前分支名
     */
    fun getCurrentBranch(repository: GitRepository): String? {
        return repository.currentBranch?.name
    }

    /**
     * 获取所有本地分支
     */
    fun getLocalBranches(repository: GitRepository): List<String> {
        return repository.branches.localBranches.map { it.name }
    }

    /**
     * 获取所有远程分支
     */
    fun getRemoteBranches(repository: GitRepository): List<String> {
        return repository.branches.remoteBranches.map { it.name }
    }

    /**
     * 检查分支是否存在
     */
    fun branchExists(repository: GitRepository, branchName: String): Boolean {
        val localExists = repository.branches.localBranches.any { it.name == branchName }
        val remoteExists = repository.branches.remoteBranches.any {
            it.name == branchName || it.name == "origin/$branchName"
        }
        return localExists || remoteExists
    }

    // ==================== 合并操作 ====================

    /**
     * 合并分支
     */
    fun merge(repository: GitRepository, branchToMerge: String, noFastForward: Boolean = false): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.MERGE)
        if (noFastForward) {
            handler.addParameters("--no-ff")
        }
        handler.addParameters(branchToMerge)
        return git.runCommand(handler)
    }

    /**
     * 合并分支并返回结果
     */
    fun mergeWithResult(repository: GitRepository, branchToMerge: String): MergeResult {
        val result = merge(repository, branchToMerge)

        return when {
            result.success() -> MergeResult.Success()
            hasConflict(result) -> {
                val conflictFiles = parseConflictFiles(result)
                MergeResult.Conflict(conflictFiles)
            }
            else -> MergeResult.Error(result.errorOutputAsJoinedString)
        }
    }

    /**
     * 检查是否有冲突
     */
    private fun hasConflict(result: GitCommandResult): Boolean {
        val output = result.outputAsJoinedString + result.errorOutputAsJoinedString
        return output.contains("CONFLICT") || output.contains("Automatic merge failed")
    }

    /**
     * 解析冲突文件
     */
    private fun parseConflictFiles(result: GitCommandResult): List<String> {
        val output = result.outputAsJoinedString
        val conflictPattern = Regex("CONFLICT \\([^)]+\\): Merge conflict in (.+)")
        return conflictPattern.findAll(output).map { it.groupValues[1] }.toList()
    }

    /**
     * 中止合并
     */
    fun abortMerge(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.MERGE)
        handler.addParameters("--abort")
        return git.runCommand(handler)
    }

    // ==================== 推送操作 ====================

    /**
     * 推送当前分支
     */
    fun push(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        return git.runCommand(handler)
    }

    /**
     * 推送到指定远程分支
     */
    fun pushToRemote(repository: GitRepository, remoteBranch: String): GitCommandResult {
        val currentBranch = getCurrentBranch(repository) ?: return GitCommandResult(
            false, -1, listOf(), listOf("无法获取当前分支")
        )
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters("origin", "$currentBranch:$remoteBranch")
        return git.runCommand(handler)
    }

    /**
     * 设置上游分支并推送
     */
    fun pushSetUpstream(repository: GitRepository, remoteBranch: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters("-u", "origin", remoteBranch)
        return git.runCommand(handler)
    }

    // ==================== Tag 操作 ====================

    /**
     * 创建 Tag
     */
    fun createTag(repository: GitRepository, tagName: String, message: String? = null): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.TAG)
        if (!message.isNullOrBlank()) {
            handler.addParameters("-a", tagName, "-m", message)
        } else {
            handler.addParameters(tagName)
        }
        return git.runCommand(handler)
    }

    /**
     * 推送 Tag
     */
    fun pushTag(repository: GitRepository, tagName: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters("origin", tagName)
        return git.runCommand(handler)
    }

    /**
     * 推送所有 Tags
     */
    fun pushAllTags(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters("origin", "--tags")
        return git.runCommand(handler)
    }

    /**
     * 获取所有 Tags（按创建时间倒序）
     */
    fun getAllTags(repository: GitRepository): List<String> {
        val handler = GitLineHandler(project, repository.root, GitCommand.TAG)
        handler.addParameters("-l", "--sort=-creatordate")
        val result = git.runCommand(handler)
        return if (result.success()) {
            result.output.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    /**
     * 删除本地 Tag
     */
    fun deleteTag(repository: GitRepository, tagName: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.TAG)
        handler.addParameters("-d", tagName)
        return git.runCommand(handler)
    }

    /**
     * 删除远程 Tag
     */
    fun deleteRemoteTag(repository: GitRepository, tagName: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters("origin", ":refs/tags/$tagName")
        return git.runCommand(handler)
    }

    // ==================== 状态操作 ====================

    /**
     * 获取仓库状态
     */
    fun status(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.STATUS)
        handler.addParameters("--porcelain")
        return git.runCommand(handler)
    }

    /**
     * 检查是否有未提交的更改（已跟踪文件的修改或暂存）
     * 忽略未跟踪的文件（以 ?? 开头），因为它们不影响分支切换和合并
     */
    fun hasUncommittedChanges(repository: GitRepository): Boolean {
        val result = status(repository)
        if (!result.success()) return false

        // 过滤掉未跟踪的文件（以 ?? 开头）和被忽略的文件（以 !! 开头）
        return result.output.any { line ->
            line.isNotBlank() && !line.startsWith("??") && !line.startsWith("!!")
        }
    }

    /**
     * 获取仓库根目录
     */
    fun getRepositoryRoot(repository: GitRepository): VirtualFile {
        return repository.root
    }

    /**
     * 刷新仓库状态
     */
    fun refreshRepository(repository: GitRepository) {
        repository.update()
    }
}
