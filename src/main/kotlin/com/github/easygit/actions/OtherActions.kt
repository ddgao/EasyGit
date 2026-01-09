package com.github.easygit.actions

import com.github.easygit.service.NotificationService
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.service.RepositoryService
import com.github.easygit.settings.EasyGitSettings
import com.github.easygit.ui.MergeDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 批量合并 Action
 */
class BatchMergeAction : AnAction() {

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

        // 显示合并对话框
        val dialog = MergeDialog(project, repositories, null)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * 打开工具窗口 Action
 */
class OpenToolWindowAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("EasyGit")
        toolWindow?.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
