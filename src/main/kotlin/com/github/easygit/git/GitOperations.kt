package com.github.easygit.git

import com.github.easygit.model.BranchInfo
import com.github.easygit.model.CommitInfo
import com.github.easygit.model.MergeResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import com.intellij.util.concurrency.AppExecutorUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
     * 带超时的 fetch 远程仓库。
     *
     * @param repository Git 仓库
     * @param timeoutSeconds 超时时间（秒）
     * @return git 命令执行结果，超时会返回失败结果
     */
    fun fetchWithTimeout(repository: GitRepository, timeoutSeconds: Long): GitCommandResult {
        return runGitCommandWithTimeout(
            repository = repository,
            command = GitCommand.FETCH,
            timeoutSeconds = timeoutSeconds,
            params = arrayOf("--all", "--prune")
        )
    }

    /**
     * Fetch 远程 Tags（同步删除远端已删除的 Tags）
     */
    fun fetchTags(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.FETCH)
        handler.addParameters("origin", "--tags", "--prune", "--prune-tags")
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
     * 忽略：未跟踪文件、被 .gitignore 忽略的文件、.gitignore 中列出的已跟踪文件
     */
    fun hasUncommittedChanges(repository: GitRepository): Boolean {
        val result = status(repository)
        if (!result.success()) return false

        val changedFiles = result.output
            .filter { line -> line.isNotBlank() && !line.startsWith("??") && !line.startsWith("!!") }
            .map { line -> line.substring(3).trim() }

        if (changedFiles.isEmpty()) return false

        val ignoredFiles = getIgnoredTrackedFiles(repository, changedFiles)
        val realChanges = changedFiles.filter { it !in ignoredFiles }

        return realChanges.isNotEmpty()
    }

    /**
     * 检查哪些文件在 .gitignore 中（即使已被跟踪）
     */
    private fun getIgnoredTrackedFiles(repository: GitRepository, files: List<String>): Set<String> {
        if (files.isEmpty()) return emptySet()

        return try {
            val command = mutableListOf("git", "check-ignore", "--no-index")
            command.addAll(files)

            val process = ProcessBuilder(command)
                .directory(java.io.File(repository.root.path))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readLines()
            process.waitFor()

            output.filter { it.isNotBlank() }.toSet()
        } catch (e: Exception) {
            emptySet()
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

    // ==================== 分支清理操作 ====================

    fun getRemoteBranchesWithDetails(
        repository: GitRepository,
        remoteName: String = "origin",
        timeoutSeconds: Long = 30
    ): List<BranchInfo> {
        com.intellij.openapi.diagnostic.Logger.getInstance(GitOperations::class.java)
            .info("获取远程分支详情, remoteName=$remoteName")

        val result = runGitCommandWithTimeout(
            repository,
            GitCommand.BRANCH,
            timeoutSeconds,
            arrayOf("-r", "--format=%(refname:short)|%(committerdate:iso-strict)|%(authorname)")
        )
        
        com.intellij.openapi.diagnostic.Logger.getInstance(GitOperations::class.java)
            .info("Git branch 命令结果: success=${result.success()}, output lines=${result.output.size}")
        
        if (!result.success()) {
            val errorMessage = result.errorOutputAsJoinedString
                .ifBlank { result.outputAsJoinedString }
                .ifBlank { "未知错误" }
            throw IllegalStateException("获取远程分支失败: $errorMessage")
        }

        return result.output
            .filter { it.isNotBlank() && !it.contains("HEAD") && it.startsWith("$remoteName/") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 3) return@mapNotNull null

                val fullName = parts[0].trim()
                val branchName = fullName.removePrefix("$remoteName/")
                val dateStr = parts[1].trim()
                val author = parts[2].trim()

                val lastCommitDate = try {
                    LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: Exception) {
                    LocalDateTime.now()
                }

                BranchInfo(
                    name = branchName,
                    remoteName = fullName,
                    lastCommitDate = lastCommitDate,
                    author = author
                )
            }
    }

    fun isBranchMergedTo(
        repository: GitRepository,
        branchName: String,
        targetBranch: String,
        remoteName: String = "origin"
    ): Boolean {
        val result = runGitCommand(
            repository,
            GitCommand.BRANCH,
            "-r", "--merged", "$remoteName/$targetBranch"
        )
        if (!result.success()) return false

        return result.output.any { line ->
            line.trim() == "$remoteName/$branchName"
        }
    }

    fun checkBranchMergedStatus(
        repository: GitRepository,
        branchName: String,
        protectedBranches: List<String>,
        remoteName: String = "origin"
    ): Map<String, Boolean> {
        return protectedBranches.associateWith { targetBranch ->
            isBranchMergedTo(repository, branchName, targetBranch, remoteName)
        }
    }

    /**
     * 批量获取已合并到目标分支的所有远程分支名（不含 origin/ 前缀）
     *
     * @param repository Git 仓库
     * @param targetBranch 目标分支名（不含 origin/ 前缀，如 "main"）
     * @param remoteName 远程名称
     * @return 已合并的远程分支名集合
     */
    fun getBranchesMergedTo(
        repository: GitRepository,
        targetBranch: String,
        remoteName: String = "origin",
        timeoutSeconds: Long = 15
    ): Set<String> {
        val result = runGitCommandWithTimeout(
            repository,
            GitCommand.BRANCH,
            timeoutSeconds,
            arrayOf("-r", "--merged", "$remoteName/$targetBranch")
        )
        if (!result.success()) return emptySet()

        return result.output
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("HEAD") && it.startsWith("$remoteName/") }
            .map { it.removePrefix("$remoteName/") }
            .toSet()
    }

    /**
     * 批量检查所有远程分支对各保护分支的合并状态
     * 只执行 protectedBranches.size 次 git 命令，而非 branches × protectedBranches 次
     *
     * @param repository Git 仓库
     * @param protectedBranches 保护分支列表
     * @param remoteName 远程名称
     * @return Map<分支名, Map<保护分支名, 是否已合并>>
     */
    fun batchCheckMergedStatus(
        repository: GitRepository,
        protectedBranches: List<String>,
        remoteName: String = "origin",
        timeoutSecondsPerTarget: Long = 15
    ): Map<String, Map<String, Boolean>> {
        val mergedSets = protectedBranches.associateWith { target ->
            getBranchesMergedTo(repository, target, remoteName, timeoutSecondsPerTarget)
        }

        val allBranches = mergedSets.values.flatten().toSet()
        return allBranches.associateWith { branch ->
            protectedBranches.associateWith { target ->
                mergedSets[target]?.contains(branch) == true
            }
        }
    }

    fun getLastCommits(
        repository: GitRepository,
        branchName: String,
        count: Int = 3,
        remoteName: String = "origin"
    ): List<CommitInfo> {
        val result = runGitCommand(
            repository,
            GitCommand.LOG,
            "$remoteName/$branchName",
            "--format=%h|%s|%an|%ar",
            "-n", count.toString()
        )
        if (!result.success()) return emptyList()

        return result.output
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 4)
                if (parts.size < 4) return@mapNotNull null

                CommitInfo(
                    hash = parts[0].trim(),
                    message = parts[1].trim(),
                    author = parts[2].trim(),
                    relativeDate = parts[3].trim()
                )
            }
    }

    fun deleteLocalBranch(repository: GitRepository, branchName: String, force: Boolean = true): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
        handler.addParameters(if (force) "-D" else "-d", branchName)
        return git.runCommand(handler)
    }

    fun deleteRemoteBranch(repository: GitRepository, branchName: String, remoteName: String = "origin"): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.PUSH)
        handler.addParameters(remoteName, "--delete", branchName)
        return git.runCommand(handler)
    }

    fun localBranchExists(repository: GitRepository, branchName: String): Boolean {
        return repository.branches.localBranches.any { it.name == branchName }
    }

    /**
     * 基于远端分支创建或重置本地分支
     * 如果本地分支存在，先删除再从远端创建
     * 如果不存在，直接从远端分支创建
     */
    fun checkoutFromRemote(repository: GitRepository, localBranch: String, remoteBranch: String): GitCommandResult {
        if (localBranchExists(repository, localBranch)) {
            val currentBranch = getCurrentBranch(repository)
            if (currentBranch == localBranch) {
                val tempResult = checkout(repository, remoteBranch)
                if (!tempResult.success()) {
                    return tempResult
                }
            }
            deleteLocalBranch(repository, localBranch, force = true)
        }
        return checkoutNewBranch(repository, localBranch, remoteBranch)
    }

    /**
     * 获取远端分支的 commit hash
     */
    fun getRemoteBranchCommit(repository: GitRepository, remoteBranch: String): String? {
        val handler = GitLineHandler(project, repository.root, GitCommand.REV_PARSE)
        handler.addParameters(remoteBranch)
        val result = git.runCommand(handler)
        return if (result.success()) {
            result.output.firstOrNull()?.trim()
        } else {
            null
        }
    }

    /**
     * 在指定 commit 上创建 Tag
     */
    fun createTagOnCommit(repository: GitRepository, tagName: String, commitHash: String, message: String? = null): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.TAG)
        if (!message.isNullOrBlank()) {
            handler.addParameters("-a", tagName, commitHash, "-m", message)
        } else {
            handler.addParameters(tagName, commitHash)
        }
        return git.runCommand(handler)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getAllAuthors(repository: GitRepository, remoteName: String = "origin"): List<String> {
        val result = runGitCommand(
            repository,
            GitCommand.BRANCH,
            "-r", "--format=%(authorname)"
        )
        if (!result.success()) return emptyList()

        return result.output
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private fun runGitCommand(repository: GitRepository, command: GitCommand, vararg params: String): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, command)
        params.forEach { handler.addParameters(it) }
        return git.runCommand(handler)
    }

    private fun runGitCommandWithTimeout(
        repository: GitRepository,
        command: GitCommand,
        timeoutSeconds: Long,
        params: Array<String>
    ): GitCommandResult {
        if (timeoutSeconds <= 0) {
            return runGitCommand(repository, command, *params)
        }

        val future = AppExecutorUtil.getAppExecutorService().submit<GitCommandResult> {
            runGitCommand(repository, command, *params)
        }

        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            GitCommandResult(
                false,
                -1,
                emptyList(),
                listOf("Git 命令执行超时（${timeoutSeconds}s）: ${command.name()} ${params.joinToString(" ")}")
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            GitCommandResult(false, -1, emptyList(), listOf("Git 命令执行被中断: ${e.message ?: "unknown"}"))
        } catch (e: ExecutionException) {
            val causeMessage = e.cause?.message ?: e.message ?: "unknown"
            GitCommandResult(false, -1, emptyList(), listOf("Git 命令执行失败: $causeMessage"))
        }
    }
}
