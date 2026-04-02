package com.github.easygit.actions

import com.github.easygit.git.GitOperations
import com.github.easygit.git.MergeExecutor
import com.github.easygit.git.TagManager
import com.github.easygit.model.BatchOperationResult
import com.github.easygit.model.MergeTarget
import com.github.easygit.model.OperationHistory
import com.github.easygit.model.OperationType
import com.github.easygit.model.TagConfig
import com.github.easygit.model.TagType
import com.github.easygit.service.HistoryService
import com.github.easygit.service.NotificationService
import com.github.easygit.settings.EasyGitSettings
import com.github.easygit.ui.ResultDialog
import com.github.easygit.ui.TagResultDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.util.concurrent.Callable

/**
 * 项目视图右键菜单的基础 Action
 * 直接对右键选中的目录（Git 仓库）进行操作
 */
abstract class ProjectViewBaseAction : AnAction() {

    /**
     * 异步获取右键选中的多个文件/目录对应的 Git 仓库
     * 在后台线程执行仓库查找，避免 EDT 线程访问问题
     * @param e ActionEvent
     * @param callback 回调函数，在 EDT 上执行，传入找到的仓库列表
     */
    protected fun getRepositoriesFromContextAsync(
        e: AnActionEvent,
        callback: (List<GitRepository>) -> Unit
    ) {
        val project = e.project ?: run {
            callback(emptyList())
            return
        }
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: run {
            callback(emptyList())
            return
        }

        // 使用 ReadAction.nonBlocking 在后台线程执行
        ReadAction.nonBlocking(Callable {
            virtualFiles.mapNotNull { findGitRepository(project, it) }.distinct()
        })
            .finishOnUiThread(ModalityState.defaultModalityState()) { repositories ->
                callback(repositories)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /**
     * 查找目录对应的 Git 仓库（必须在后台线程调用）
     */
    private fun findGitRepository(project: Project, virtualFile: VirtualFile): GitRepository? {
        val repoManager = GitRepositoryManager.getInstance(project)

        // 直接检查当前目录
        var repo = repoManager.getRepositoryForRoot(virtualFile)
        if (repo != null) return repo

        // 向上查找父目录
        var current: VirtualFile? = virtualFile
        while (current != null) {
            repo = repoManager.getRepositoryForRoot(current)
            if (repo != null) return repo

            // 检查是否包含 .git 目录
            if (current.findChild(".git") != null) {
                return repoManager.getRepositoryForRoot(current)
            }
            current = current.parent
        }

        // 从项目仓库中查找包含此文件的仓库
        for (repository in repoManager.repositories) {
            if (isUnderDirectory(virtualFile, repository.root)) {
                return repository
            }
        }

        return null
    }

    private fun isUnderDirectory(file: VirtualFile, directory: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current == directory) return true
            current = current.parent
        }
        return false
    }

    /**
     * 检查是否应该显示此菜单项
     * 只在有项目时显示，具体的仓库检查在 actionPerformed 中进行
     */
    override fun update(e: AnActionEvent) {
        // 只检查项目是否存在，避免在 EDT 线程访问 GitRepositoryManager
        // 具体的仓库检查会在 actionPerformed 中异步执行
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

// ==================== 合并操作 ====================

/**
 * 项目视图 - 合并到 Dev
 */
class ProjectViewMergeToDevAction : ProjectViewMergeAction(MergeTarget.DEV)

/**
 * 项目视图 - 合并到 Test
 */
class ProjectViewMergeToTestAction : ProjectViewMergeAction(MergeTarget.TEST)

/**
 * 项目视图 - 合并到 Main
 */
class ProjectViewMergeToMainAction : ProjectViewMergeAction(MergeTarget.MAIN)

/**
 * 项目视图合并 Action 基类
 */
abstract class ProjectViewMergeAction(private val target: MergeTarget) : ProjectViewBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 异步获取仓库列表
        getRepositoriesFromContextAsync(e) { repositories ->
            if (repositories.isEmpty()) {
                NotificationService.warning(project, "EasyGit", "未找到 Git 仓库")
                return@getRepositoriesFromContextAsync
            }

            // 使用 MergeDialog 显示完整的合并选项
            val repoInfoList = repositories.map { repo ->
                com.github.easygit.service.RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo,
                    selected = true
                )
            }

            val dialog = com.github.easygit.ui.MergeDialog(project, repoInfoList, target)
            if (dialog.showAndGet()) {
                val selectedRepos = dialog.getSelectedRepositories().mapNotNull { it.repository }
                val sourceBranch = dialog.getSourceBranch()
                val targetBranchName = dialog.getTargetBranch()
                val autoFetch = dialog.isAutoFetch()
                val autoPush = dialog.isAutoPush()

                // 执行合并
                executeMerge(project, selectedRepos, sourceBranch, targetBranchName, autoFetch, autoPush)
            }
        }
    }

    private fun executeMerge(
        project: Project,
        repositories: List<GitRepository>,
        sourceBranch: String,
        targetBranch: String,
        autoFetch: Boolean,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "合并到 $targetBranch", true) {
            override fun run(indicator: ProgressIndicator) {
                val executor = MergeExecutor(project)
                val results = mutableListOf<BatchOperationResult>()

                repositories.forEachIndexed { index, repo ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repositories.size
                    indicator.text = "正在处理: ${repo.root.name}"
                    indicator.text2 = "$sourceBranch → $targetBranch"

                    val result = executor.executeMerge(
                        repository = repo,
                        sourceBranch = sourceBranch,
                        targetBranch = targetBranch,
                        autoPush = autoPush,
                        fetchFirst = autoFetch
                    )
                    results.add(result)
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = ResultDialog(project, "合并结果: $sourceBranch → $targetBranch", results)
                    dialog.show()

                    val successCount = results.count { it.success }
                    val failCount = results.size - successCount
                    NotificationService.showMergeResult(project, successCount, failCount)
                }
            }
        })
    }
}

// ==================== 一键合并 ====================

/**
 * 项目视图 - 一键合并所有环境
 */
class ProjectViewOneClickMergeAction : ProjectViewBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 异步获取仓库列表
        getRepositoriesFromContextAsync(e) { repositories ->
            if (repositories.isEmpty()) {
                NotificationService.warning(project, "EasyGit", "未找到 Git 仓库")
                return@getRepositoriesFromContextAsync
            }

            val settings = EasyGitSettings.getInstance()
            val currentBranches = repositories.mapNotNull { it.currentBranch?.name }.distinct()
            val defaultBranch = currentBranches.firstOrNull() ?: ""

            // 确认对话框
            val sourceBranch = Messages.showInputDialog(
                project,
                "将依次合并以下 ${repositories.size} 个仓库到所有环境:\n" +
                        repositories.joinToString("\n") { "  - ${it.root.name}" } +
                        "\n\n合并顺序:\n" +
                        "  1. ${settings.devBranchName}\n" +
                        "  2. ${settings.testBranchName}\n" +
                        "  3. ${settings.mainBranchName}\n" +
                        "\n注意：合并后将推送到远端并切回原分支\n" +
                        "\n请确认源分支名称:",
                "一键合并所有环境",
                Messages.getQuestionIcon(),
                defaultBranch,
                object : InputValidator {
                    override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                    override fun canClose(inputString: String?): Boolean = checkInput(inputString)
                }
            ) ?: return@getRepositoriesFromContextAsync

            executeOneClickMerge(project, repositories, sourceBranch)
        }
    }

    private fun executeOneClickMerge(
        project: Project,
        repositories: List<GitRepository>,
        sourceBranch: String
    ) {
        val settings = EasyGitSettings.getInstance()
        val targets = listOf(settings.devBranchName, settings.testBranchName, settings.mainBranchName)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "一键合并", true) {
            override fun run(indicator: ProgressIndicator) {
                val executor = MergeExecutor(project)
                val allResults = mutableListOf<BatchOperationResult>()

                targets.forEachIndexed { targetIndex, targetBranch ->
                    repositories.forEachIndexed { repoIndex, repo ->
                        indicator.checkCanceled()
                        val progress = (targetIndex * repositories.size + repoIndex).toDouble() /
                                (targets.size * repositories.size)
                        indicator.fraction = progress
                        indicator.text = "合并到 $targetBranch"
                        indicator.text2 = repo.root.name

                        val result = executor.executeMerge(
                            repository = repo,
                            sourceBranch = sourceBranch,
                            targetBranch = targetBranch,
                            autoPush = true,  // 默认推送到远端
                            fetchFirst = settings.autoFetchBeforeMerge
                        )
                        allResults.add(BatchOperationResult(
                            repositoryName = "${repo.root.name} → $targetBranch",
                            repositoryPath = result.repositoryPath,
                            success = result.success,
                            message = result.message
                        ))
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = ResultDialog(project, "一键合并结果", allResults)
                    dialog.show()

                    val successCount = allResults.count { it.success }
                    val failCount = allResults.size - successCount
                    NotificationService.showMergeResult(project, successCount, failCount)
                }
            }
        })
    }
}

/**
 * 项目视图 - 打 Tag（弹出对话框选择类型）
 */
class ProjectViewCreateTagAction : ProjectViewBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 异步获取仓库列表
        getRepositoriesFromContextAsync(e) { repositories ->
            if (repositories.isEmpty()) {
                NotificationService.warning(project, "EasyGit", "未找到 Git 仓库")
                return@getRepositoriesFromContextAsync
            }

            // 使用已有的 TagDialog
            val repoInfoList = repositories.map { repo ->
                com.github.easygit.service.RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo,
                    selected = true
                )
            }

            val dialog = com.github.easygit.ui.TagDialog(project, repoInfoList)
            if (dialog.showAndGet()) {
                val selectedRepoTags = dialog.getSelectedRepositoryTags()
                val description = dialog.getDescription()
                val autoPush = dialog.isAutoPush()

                // 执行创建 Tag（每个仓库使用独立的 Tag 名称）
                createTagsWithIndividualNames(project, selectedRepoTags, description, autoPush)
            }
        }
    }

    private fun createTagsWithIndividualNames(
        project: Project,
        repositoryTags: List<com.github.easygit.ui.RepositoryTagInfo>,
        description: String,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 Tag", true) {
            override fun run(indicator: ProgressIndicator) {
                val tagManager = com.github.easygit.git.TagManager(project)
                val historyService = HistoryService.getInstance()
                val settings = com.github.easygit.settings.EasyGitSettings.getInstance()
                val baseBranch = "origin/${settings.tagBaseBranchName}"
                val results = mutableListOf<com.github.easygit.model.TagResult>()

                repositoryTags.forEachIndexed { index, repoTag ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repositoryTags.size
                    indicator.text = "正在处理: ${repoTag.repositoryInfo.name}"

                    val repo = repoTag.repositoryInfo.repository

                    if (repo != null) {
                        val result = tagManager.createTagWithName(
                            repo,
                            repoTag.tagName,
                            description,
                            autoPush
                        )
                        results.add(result)

                        historyService.addHistory(OperationHistory(
                            operationType = OperationType.CREATE_TAG,
                            repositoryName = repoTag.repositoryInfo.name,
                            repositoryPath = repoTag.repositoryInfo.path,
                            sourceBranch = baseBranch,
                            tagName = result.tagName,
                            success = result.success,
                            message = result.message
                        ))
                    } else {
                        results.add(com.github.easygit.model.TagResult(
                            repositoryName = repoTag.repositoryInfo.name,
                            tagName = repoTag.tagName,
                            success = false,
                            message = "无法获取仓库对象"
                        ))
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = TagResultDialog(project, results)
                    dialog.show()
                }
            }
        })
    }
}


