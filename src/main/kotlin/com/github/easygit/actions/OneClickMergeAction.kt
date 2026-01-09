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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * 一键合并所有环境
 */
class OneClickMergeAction : AnAction() {

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

        // 显示合并对话框（不设置默认目标，因为是一键合并）
        val dialog = MergeDialog(project, repositories, null)
        if (dialog.showAndGet()) {
            val selectedRepos = dialog.getSelectedRepositories()
            val sourceBranch = dialog.getSourceBranch()
            val autoFetch = dialog.isAutoFetch()
            val autoPush = dialog.isAutoPush()

            // 确认操作
            val confirm = Messages.showYesNoDialog(
                project,
                "将依次合并 $sourceBranch 到:\n" +
                        "  1. ${settings.devBranchName}\n" +
                        "  2. ${settings.testBranchName}\n" +
                        "  3. ${settings.mainBranchName}\n\n" +
                        "选中了 ${selectedRepos.size} 个仓库，确认执行？",
                "一键合并确认",
                Messages.getQuestionIcon()
            )

            if (confirm == Messages.YES) {
                executeOneClickMerge(project, selectedRepos, sourceBranch, autoFetch, autoPush)
            }
        }
    }

    private fun executeOneClickMerge(
        project: Project,
        repositories: List<RepositoryInfo>,
        sourceBranch: String,
        autoFetch: Boolean,
        autoPush: Boolean
    ) {
        val settings = EasyGitSettings.getInstance()
        val targets = listOf(
            settings.devBranchName,
            settings.testBranchName,
            settings.mainBranchName
        )

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "一键合并", true) {
            override fun run(indicator: ProgressIndicator) {
                val allResults = mutableMapOf<String, MutableList<BatchOperationResult>>()
                val executor = MergeExecutor(project)
                var hasError = false

                // 对每个目标分支执行合并
                targets.forEachIndexed { targetIndex, targetBranch ->
                    if (hasError) return@forEachIndexed

                    indicator.text = "合并到 $targetBranch"

                    repositories.forEachIndexed { repoIndex, repoInfo ->
                        indicator.checkCanceled()
                        val overallProgress = (targetIndex * repositories.size + repoIndex).toDouble() /
                                (targets.size * repositories.size)
                        indicator.fraction = overallProgress
                        indicator.text2 = "${repoInfo.name}: $sourceBranch → $targetBranch"

                        val repo = repoInfo.repository
                        if (repo != null) {
                            val result = executor.executeMerge(
                                repository = repo,
                                sourceBranch = sourceBranch,
                                targetBranch = targetBranch,
                                autoPush = autoPush,
                                fetchFirst = autoFetch
                            )

                            allResults.getOrPut(repoInfo.name) { mutableListOf() }.add(result)

                            // 如果合并失败（冲突等），记录但继续处理其他仓库
                            if (!result.success) {
                                // 不中断整个流程，只是记录
                            }
                        }
                    }
                }

                // 显示结果
                showResults(project, allResults, sourceBranch, targets)
            }
        })
    }

    private fun showResults(
        project: Project,
        allResults: Map<String, List<BatchOperationResult>>,
        sourceBranch: String,
        targets: List<String>
    ) {
        ApplicationManager.getApplication().invokeLater {
            // 汇总所有结果
            val flatResults = allResults.flatMap { (repoName, results) ->
                results.mapIndexed { index, result ->
                    BatchOperationResult(
                        repositoryName = "$repoName → ${targets.getOrNull(index) ?: "unknown"}",
                        repositoryPath = result.repositoryPath,
                        success = result.success,
                        message = result.message
                    )
                }
            }

            val dialog = ResultDialog(project, "一键合并结果", flatResults)
            dialog.show()

            // 发送通知
            val successCount = flatResults.count { it.success }
            val failCount = flatResults.size - successCount
            NotificationService.showMergeResult(project, successCount, failCount)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
