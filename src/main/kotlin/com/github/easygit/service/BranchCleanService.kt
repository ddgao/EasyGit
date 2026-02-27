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
        progressCheck: (() -> Unit)? = null
    ): List<BranchInfo> {
        val log = com.intellij.openapi.diagnostic.Logger.getInstance(BranchCleanService::class.java)
        log.info("开始加载分支, fetchFirst=$fetchFirst")

        progressCheck?.invoke()

        if (fetchFirst) {
            try {
                val fetchResult = gitOperations.fetchWithTimeout(repository, timeoutSeconds = FETCH_TIMEOUT_SECONDS)
                if (!fetchResult.success()) {
                    log.warn("Fetch 失败: ${fetchResult.errorOutputAsJoinedString}")
                }
            } catch (e: Exception) {
                log.warn("Fetch 失败: ${e.message}")
            }
        }

        progressCheck?.invoke()

        val protectedBranches = getProtectedBranches()
        val branches = gitOperations.getRemoteBranchesWithDetails(
            repository = repository,
            timeoutSeconds = LIST_BRANCH_TIMEOUT_SECONDS
        )
        log.info("获取到 ${branches.size} 个远程分支")

        progressCheck?.invoke()

        val mergedStatusMap = try {
            gitOperations.batchCheckMergedStatus(
                repository = repository,
                protectedBranches = protectedBranches,
                timeoutSecondsPerTarget = MERGED_CHECK_TIMEOUT_SECONDS
            )
        } catch (e: Exception) {
            log.warn("批量检查合并状态失败: ${e.message}")
            emptyMap()
        }

        return branches.map { branch ->
            progressCheck?.invoke()

            val mergedTo = mergedStatusMap[branch.name]
                ?: protectedBranches.associateWith { false }
            val isProtected = protectedBranches.contains(branch.name)

            branch.copy(
                mergedTo = mergedTo,
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

            if (config.mergedToDev && branch.mergedTo[settings.devBranchName] != true) return@filter false
            if (config.mergedToTest && branch.mergedTo[settings.testBranchName] != true) return@filter false
            if (config.mergedToMain && branch.mergedTo[settings.mainBranchName] != true) return@filter false

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
        private const val FETCH_TIMEOUT_SECONDS = 30L
        private const val LIST_BRANCH_TIMEOUT_SECONDS = 30L
        private const val MERGED_CHECK_TIMEOUT_SECONDS = 15L

        fun getInstance(project: Project): BranchCleanService {
            return project.getService(BranchCleanService::class.java)
        }
    }
}
