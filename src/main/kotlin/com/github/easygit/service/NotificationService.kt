package com.github.easygit.service

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project

/**
 * 通知服务
 * 负责显示各种通知消息
 */
object NotificationService {

    private const val NOTIFICATION_GROUP_ID = "EasyGit.Notifications"

    private fun getNotificationGroup() = NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)

    /**
     * 显示信息通知
     */
    fun info(project: Project?, title: String, content: String) {
        getNotificationGroup()
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * 显示警告通知
     */
    fun warning(project: Project?, title: String, content: String) {
        getNotificationGroup()
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * 显示错误通知
     */
    fun error(project: Project?, title: String, content: String) {
        getNotificationGroup()
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    /**
     * 显示带动作的通知
     */
    fun infoWithAction(
        project: Project?,
        title: String,
        content: String,
        actionName: String,
        action: () -> Unit
    ) {
        getNotificationGroup()
            .createNotification(title, content, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimple(actionName) { action() })
            .notify(project)
    }

    /**
     * 显示成功通知
     */
    fun success(project: Project?, title: String, content: String) {
        info(project, title, content)
    }

    /**
     * 显示合并结果通知
     */
    fun showMergeResult(
        project: Project?,
        successCount: Int,
        failCount: Int,
        details: String = ""
    ) {
        val title = "合并操作完成"
        val content = buildString {
            append("成功: $successCount, 失败: $failCount")
            if (details.isNotBlank()) {
                append("\n$details")
            }
        }

        if (failCount == 0) {
            info(project, title, content)
        } else {
            warning(project, title, content)
        }
    }

    /**
     * 显示 Tag 创建结果
     */
    fun showTagResult(
        project: Project?,
        tagResults: List<Pair<String, String>> // 仓库名 -> Tag 名
    ) {
        val title = "Tag 创建完成"
        val content = buildString {
            append("已创建的 Tag:\n")
            tagResults.forEach { (repoName, tagName) ->
                append("$repoName $tagName\n")
            }
        }
        info(project, title, content)
    }

    /**
     * 显示冲突警告
     */
    fun showConflictWarning(
        project: Project?,
        repositoryName: String,
        conflictFiles: List<String>
    ) {
        val title = "合并冲突 - $repositoryName"
        val content = buildString {
            append("以下文件存在冲突，请手动解决:\n")
            conflictFiles.take(5).forEach { append("• $it\n") }
            if (conflictFiles.size > 5) {
                append("... 还有 ${conflictFiles.size - 5} 个文件")
            }
        }
        warning(project, title, content)
    }
}
