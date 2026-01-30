package com.github.easygit.service

import com.github.easygit.git.GitOperations
import com.github.easygit.model.BranchDeleteResult
import com.github.easygit.model.BranchFilterConfig
import com.github.easygit.model.BranchInfo
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class BranchCleanService(private val project: Project) {

    private val gitOperations = GitOperations(project)
    private val settings = EasyGitSettings.getInstance()

    fun getProtectedBranches(): List<String> {
        return listOf(
            settings.devBranchName,
            settings.testBranchName,
            settings.mainBranchName
        ).filter { it.isNotBlank() }
    }

    fun loadBranches(
        repository: GitRepository,
        fetchFirst: Boolean = true,
        lastCommitCount: Int = 3
    ): List<BranchInfo> {
        com.intellij.openapi.diagnostic.Logger.getInstance(BranchCleanService::class.java)
            .info("开始加载分支, fetchFirst=$fetchFirst")
        
        if (fetchFirst) {
            try {
                gitOperations.fetch(repository)
            } catch (e: Exception) {
                com.intellij.openapi.diagnostic.Logger.getInstance(BranchCleanService::class.java)
                    .warn("Fetch 失败: ${e.message}")
            }
        }

        val protectedBranches = getProtectedBranches()
        val branches = gitOperations.getRemoteBranchesWithDetails(repository)
        
        com.intellij.openapi.diagnostic.Logger.getInstance(BranchCleanService::class.java)
            .info("获取到 ${branches.size} 个远程分支")

        // 为了提高性能，只在分支数量较少时获取详细信息
        if (branches.size > 50) {
            // 分支太多，只返回基本信息
            return branches.map { branch ->
                val isProtected = protectedBranches.contains(branch.name)
                branch.copy(isProtected = isProtected)
            }
        }

        return branches.map { branch ->
            val mergedTo = try {
                gitOperations.checkBranchMergedStatus(repository, branch.name, protectedBranches)
            } catch (e: Exception) {
                emptyMap()
            }
            val lastCommits = try {
                gitOperations.getLastCommits(repository, branch.name, lastCommitCount)
            } catch (e: Exception) {
                emptyList()
            }
            val isProtected = protectedBranches.contains(branch.name)

            branch.copy(
                mergedTo = mergedTo,
                lastCommits = lastCommits,
                isProtected = isProtected
            )
        }
    }

    fun filterBranches(branches: List<BranchInfo>, config: BranchFilterConfig): List<BranchInfo> {
        return branches.filter { branch ->
            if (branch.isProtected) return@filter false

            config.monthsOld?.let { months ->
                val cutoffDate = LocalDateTime.now().minusMonths(months.toLong())
                if (branch.lastCommitDate >= cutoffDate) return@filter false
            }

            config.startDate?.let { start ->
                if (branch.lastCommitDate < start) return@filter false
            }

            config.endDate?.let { end ->
                if (branch.lastCommitDate > end) return@filter false
            }

            if (config.mergedOnly && !branch.isMergedToAny()) return@filter false
            if (config.unmergedOnly && branch.isMergedToAny()) return@filter false

            config.author?.let { author ->
                if (!branch.author.contains(author, ignoreCase = true)) return@filter false
            }

            config.searchKeyword?.let { keyword ->
                if (!branch.name.contains(keyword, ignoreCase = true)) return@filter false
            }

            true
        }
    }

    fun deleteBranch(
        repository: GitRepository,
        branchName: String,
        deleteLocal: Boolean = true,
        deleteRemote: Boolean = true,
        dryRun: Boolean = false
    ): BranchDeleteResult {
        if (dryRun) {
            return BranchDeleteResult.DryRun(branchName)
        }

        try {
            var deletedLocal = false
            var deletedRemote = false

            if (deleteLocal && gitOperations.localBranchExists(repository, branchName)) {
                val localResult = gitOperations.deleteLocalBranch(repository, branchName)
                deletedLocal = localResult.success()
            }

            if (deleteRemote) {
                val remoteResult = gitOperations.deleteRemoteBranch(repository, branchName)
                deletedRemote = remoteResult.success()
                if (!remoteResult.success()) {
                    return BranchDeleteResult.Error(branchName, remoteResult.errorOutputAsJoinedString)
                }
            }

            logDeletion(repository.root.name, branchName)

            return BranchDeleteResult.Success(branchName, deletedLocal, deletedRemote)
        } catch (e: Exception) {
            return BranchDeleteResult.Error(branchName, e.message ?: "Unknown error")
        }
    }

    fun deleteBranches(
        repository: GitRepository,
        branchNames: List<String>,
        deleteLocal: Boolean = true,
        deleteRemote: Boolean = true,
        dryRun: Boolean = false
    ): List<BranchDeleteResult> {
        return branchNames.map { branchName ->
            deleteBranch(repository, branchName, deleteLocal, deleteRemote, dryRun)
        }
    }

    private fun logDeletion(repoName: String, branchName: String) {
        val logFile = getLogFile()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        val logEntry = "[$timestamp] [$repoName] 已删除分支: $branchName\n"
        logFile.appendText(logEntry)
    }

    private fun getLogFile(): File {
        val logDir = File(System.getProperty("user.home"), ".easygit")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "branch-delete-log.txt")
    }

    fun getLogFilePath(): String = getLogFile().absolutePath

    fun readDeleteLog(): String {
        val logFile = getLogFile()
        return if (logFile.exists()) logFile.readText() else ""
    }

    fun clearDeleteLog() {
        val logFile = getLogFile()
        if (logFile.exists()) {
            logFile.writeText("")
        }
    }

    fun getAllAuthors(repository: GitRepository): List<String> {
        return gitOperations.getAllAuthors(repository)
    }

    companion object {
        fun getInstance(project: Project): BranchCleanService {
            return project.getService(BranchCleanService::class.java)
        }
    }
}
