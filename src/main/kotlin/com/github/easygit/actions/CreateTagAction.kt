package com.github.easygit.actions

import com.github.easygit.git.GitOperations
import com.github.easygit.git.TagManager
import com.github.easygit.model.OperationHistory
import com.github.easygit.model.OperationType
import com.github.easygit.model.TagResult
import com.github.easygit.service.HistoryService
import com.github.easygit.service.NotificationService
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.service.RepositoryService
import com.github.easygit.settings.EasyGitSettings
import com.github.easygit.ui.RepositoryTagInfo
import com.github.easygit.ui.TagDialog
import com.github.easygit.ui.TagResultDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * 创建 Tag Action
 */
class CreateTagAction : AnAction() {

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

        // 显示 Tag 对话框
        val dialog = TagDialog(project, repositories)
        if (dialog.showAndGet()) {
            val selectedRepoTags = dialog.getSelectedRepositoryTags()
            val description = dialog.getDescription()
            val autoPush = dialog.isAutoPush()

            // 执行创建 Tag（每个仓库使用独立的 Tag 名称）
            createTagsWithIndividualNames(project, selectedRepoTags, description, autoPush)
        }
    }

    /**
     * 为每个仓库创建独立的 Tag
     */
    private fun createTagsWithIndividualNames(
        project: Project,
        repositoryTags: List<RepositoryTagInfo>,
        description: String,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 Tag", true) {
            override fun run(indicator: ProgressIndicator) {
                val results = mutableListOf<TagResult>()
                val tagManager = TagManager(project)
                val gitOps = GitOperations(project)
                val historyService = HistoryService.getInstance()
                val settings = EasyGitSettings.getInstance()
                val baseBranch = "origin/${settings.tagBaseBranchName}"

                repositoryTags.forEachIndexed { index, repoTag ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repositoryTags.size
                    indicator.text = "正在处理: ${repoTag.repositoryInfo.name}"

                    val repo = repoTag.repositoryInfo.repository
                    val tagName = repoTag.tagName

                    if (repo != null) {
                        indicator.text2 = "Fetch 远端分支..."
                        val fetchResult = gitOps.fetch(repo)
                        if (!fetchResult.success()) {
                            results.add(TagResult(
                                repositoryName = repoTag.repositoryInfo.name,
                                tagName = tagName,
                                success = false,
                                message = "Fetch 失败: ${fetchResult.errorOutputAsJoinedString}"
                            ))
                            return@forEachIndexed
                        }

                        val result = tagManager.createTagWithName(
                            repo,
                            tagName,
                            description,
                            autoPush
                        )

                        results.add(result)

                        val history = OperationHistory(
                            operationType = OperationType.CREATE_TAG,
                            repositoryName = repoTag.repositoryInfo.name,
                            repositoryPath = repoTag.repositoryInfo.path,
                            sourceBranch = baseBranch,
                            tagName = result.tagName,
                            success = result.success,
                            message = result.message
                        )
                        historyService.addHistory(history)

                        indicator.text2 = if (result.success) {
                            "已创建: ${result.tagName}"
                        } else {
                            "失败: ${result.message}"
                        }
                    } else {
                        results.add(TagResult(
                            repositoryName = repoTag.repositoryInfo.name,
                            tagName = tagName,
                            success = false,
                            message = "无法获取仓库对象"
                        ))
                    }
                }

                showResults(project, results)
            }
        })
    }

    private fun showResults(project: Project, results: List<TagResult>) {
        ApplicationManager.getApplication().invokeLater {
            val dialog = TagResultDialog(project, results)
            dialog.show()

            // 显示通知
            val successResults = results.filter { it.success }
            if (successResults.isNotEmpty()) {
                NotificationService.showTagResult(project, successResults.map {
                    it.repositoryName to it.tagName
                })
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
