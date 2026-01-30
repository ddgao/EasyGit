package com.github.easygit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 应用级设置（全局配置）
 */
@State(
    name = "EasyGitSettings",
    storages = [Storage("EasyGitSettings.xml")]
)
@Service
class EasyGitSettings : PersistentStateComponent<EasyGitSettings.State> {

    data class State(
        // 仓库路径列表
        var repositoryPaths: MutableList<String> = mutableListOf(),

        // 分支配置
        var devBranchName: String = "dev",
        var testBranchName: String = "test",
        var mainBranchName: String = "main",

        // 自动化选项
        var autoFetchBeforeMerge: Boolean = true,
        var autoPushAfterMerge: Boolean = false,
        // Tag 版本号提取正则（使用第一个捕获组作为基础版本号）
        // 例如: "R_(\\\\d+\\\\.\\\\d+\\\\.\\\\d+).*" 从 R_3.1.6-H02 提取 R_3.1.6
        var tagVersionPattern: String = "^(R_\\\\d+\\\\.\\\\d+\\\\.\\\\d+|v?\\\\d+\\\\.\\\\d+\\\\.\\\\d+).*$",

        // 打 Tag 基准分支（留空则使用 mainBranchName）
        var tagBaseBranch: String = "",

        // 扫描配置
        var scanRootPath: String = "",
        var scanMaxDepth: Int = 3
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): EasyGitSettings {
            return ApplicationManager.getApplication().getService(EasyGitSettings::class.java)
        }
    }

    // ==================== 便捷访问方法 ====================

    var repositoryPaths: List<String>
        get() = myState.repositoryPaths
        set(value) {
            myState.repositoryPaths = value.toMutableList()
        }

    var devBranchName: String
        get() = myState.devBranchName
        set(value) {
            myState.devBranchName = value
        }

    var testBranchName: String
        get() = myState.testBranchName
        set(value) {
            myState.testBranchName = value
        }

    var mainBranchName: String
        get() = myState.mainBranchName
        set(value) {
            myState.mainBranchName = value
        }

    var autoFetchBeforeMerge: Boolean
        get() = myState.autoFetchBeforeMerge
        set(value) {
            myState.autoFetchBeforeMerge = value
        }

    var autoPushAfterMerge: Boolean
        get() = myState.autoPushAfterMerge
        set(value) {
            myState.autoPushAfterMerge = value
        }

    var tagBaseBranch: String
        get() = myState.tagBaseBranch
        set(value) {
            myState.tagBaseBranch = value
        }

    val tagBaseBranchName: String
        get() = myState.tagBaseBranch.ifBlank { myState.mainBranchName }

    // ==================== 辅助方法 ====================

    /**
     * 添加仓库路径
     */
    fun addRepositoryPath(path: String) {
        if (!myState.repositoryPaths.contains(path)) {
            myState.repositoryPaths.add(path)
        }
    }

    /**
     * 移除仓库路径
     */
    fun removeRepositoryPath(path: String) {
        myState.repositoryPaths.remove(path)
    }

    /**
     * 清空仓库路径
     */
    fun clearRepositoryPaths() {
        myState.repositoryPaths.clear()
    }

    /**
     * 获取目标分支名
     */
    fun getTargetBranchName(target: String): String {
        return when (target.lowercase()) {
            "dev" -> myState.devBranchName
            "test" -> myState.testBranchName
            "main", "master" -> myState.mainBranchName
            else -> target
        }
    }
}
