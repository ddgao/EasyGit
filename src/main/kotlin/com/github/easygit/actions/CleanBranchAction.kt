package com.github.easygit.actions

import com.github.easygit.service.NotificationService
import com.github.easygit.service.RepositoryService
import com.github.easygit.ui.BranchCleanDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class CleanBranchAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repoService = RepositoryService(project)

        val repositories = repoService.getProjectRepositories()

        if (repositories.isEmpty()) {
            NotificationService.warning(project, "EasyGit", "未找到 Git 仓库")
            return
        }

        val repository = if (repositories.size == 1) {
            repositories.first()
        } else {
            val repoNames = repositories.map { it.root.name }.toTypedArray()
            com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                .createPopupChooserBuilder(repoNames.toList())
                .setTitle("选择仓库")
                .setItemChosenCallback { selectedName ->
                    val repo = repositories.find { it.root.name == selectedName }
                    if (repo != null) {
                        val dialog = BranchCleanDialog(project, repo)
                        dialog.show()
                    }
                }
                .createPopup()
                .showCenteredInCurrentWindow(project)
            return
        }

        val dialog = BranchCleanDialog(project, repository)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}

class ProjectViewCleanBranchAction : com.github.easygit.actions.ProjectViewBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 使用父类的异步方法获取仓库
        getRepositoriesFromContextAsync(e) { repositories ->
            if (repositories.isEmpty()) {
                NotificationService.warning(project, "EasyGit", "未找到 Git 仓库")
                return@getRepositoriesFromContextAsync
            }

            val repository = if (repositories.size == 1) {
                repositories.first()
            } else {
                val repoNames = repositories.map { it.root.name }.toTypedArray()
                com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(repoNames.toList())
                    .setTitle("选择仓库")
                    .setItemChosenCallback { selectedName ->
                        val repo = repositories.find { it.root.name == selectedName }
                        if (repo != null) {
                            val dialog = BranchCleanDialog(project, repo)
                            dialog.show()
                        }
                    }
                    .createPopup()
                    .showCenteredInCurrentWindow(project)
                return@getRepositoriesFromContextAsync
            }

            val dialog = BranchCleanDialog(project, repository)
            dialog.show()
        }
    }
}
