package com.github.easygit.actions

import com.github.easygit.git.MergeExecutor
import com.github.easygit.model.BatchOperationResult
import com.github.easygit.model.MergeTarget
import com.github.easygit.service.NotificationService
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.service.RepositoryService
import com.github.easygit.settings.EasyGitSettings
import com.github.easygit.ui.MergeDialog
import com.github.easygit.ui.ResultDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * 合并到 Dev 分支
 */
class MergeToDevAction : BaseMergeAction(MergeTarget.DEV)

/**
 * 合并到 Test 分支
 */
class MergeToTestAction : BaseMergeAction(MergeTarget.TEST)

/**
 * 合并到 Main 分支
 */
class MergeToMainAction : BaseMergeAction(MergeTarget.MAIN)

/**
 * 基础合并 Action
 */
abstract class BaseMergeAction(private val target: MergeTarget) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = EasyGitSettings.getInstance()
        val repoService = RepositoryService(project)

        // 获取配置的仓库
        val repositories = if (settings.repositoryPaths.isNotEmpty()) {
            repoService.loadRepositories(settings.repositoryPaths).map { repo ->
                RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo
                )
            }
        } else {
            // 使用当前项目的仓库
            repoService.getProjectRepositories().map { repo ->
                RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo
                )
            }
        }

        if (repositories.isEmpty()) {
            NotificationService.warning(project, "EasyGit", "未找到 Git 仓库，请在设置中配置仓库路径")
            return
        }

        // 显示合并对话框
        val dialog = MergeDialog(project, repositories, target)
        if (dialog.showAndGet()) {
            val selectedRepos = dialog.getSelectedRepositories()
            val sourceBranch = dialog.getSourceBranch()
            val targetBranch = dialog.getTargetBranch()
            val autoFetch = dialog.isAutoFetch()
            val autoPush = dialog.isAutoPush()

            // 执行合并
            executeMerge(project, selectedRepos, sourceBranch, targetBranch, autoFetch, autoPush)
        }
    }

    private fun executeMerge(
        project: Project,
        repositories: List<RepositoryInfo>,
        sourceBranch: String,
        targetBranch: String,
        autoFetch: Boolean,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "合并分支", true) {
            override fun run(indicator: ProgressIndicator) {
                val results = mutableListOf<BatchOperationResult>()
                val executor = MergeExecutor(project)

                repositories.forEachIndexed { index, repoInfo ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repositories.size
                    indicator.text = "正在处理: ${repoInfo.name}"
                    indicator.text2 = "$sourceBranch → $targetBranch"

                    val repo = repoInfo.repository
                    if (repo != null) {
                        val result = executor.executeMerge(
                            repository = repo,
                            sourceBranch = sourceBranch,
                            targetBranch = targetBranch,
                            autoPush = autoPush,
                            fetchFirst = autoFetch
                        )
                        results.add(result)
                    } else {
                        results.add(BatchOperationResult(
                            repositoryName = repoInfo.name,
                            repositoryPath = repoInfo.path,
                            success = false,
                            message = "无法获取仓库对象"
                        ))
                    }
                }

                // 显示结果
                showResults(project, results, sourceBranch, targetBranch)
            }
        })
    }

    private fun showResults(
        project: Project,
        results: List<BatchOperationResult>,
        sourceBranch: String,
        targetBranch: String
    ) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val dialog = ResultDialog(project, "合并结果: $sourceBranch → $targetBranch", results)
            dialog.show()

            // 发送通知
            val successCount = results.count { it.success }
            val failCount = results.size - successCount
            NotificationService.showMergeResult(project, successCount, failCount)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
