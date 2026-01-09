package com.github.easygit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 项目级设置
 */
@State(
    name = "EasyGitProjectSettings",
    storages = [Storage("easyGit.xml")]
)
@Service(Service.Level.PROJECT)
class EasyGitProjectSettings : PersistentStateComponent<EasyGitProjectSettings.State> {

    data class State(
        // 选中的仓库路径
        var selectedRepositoryPaths: MutableList<String> = mutableListOf(),

        // 上次使用的配置
        var lastSourceBranch: String = "",
        var lastTargetBranch: String = "",
        var lastTagVersion: String = "",

        // 项目特定配置（覆盖全局配置）
        var useProjectSettings: Boolean = false,
        var projectDevBranch: String = "dev",
        var projectTestBranch: String = "test",
        var projectMainBranch: String = "main"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): EasyGitProjectSettings {
            return project.getService(EasyGitProjectSettings::class.java)
        }
    }

    // ==================== 选中仓库管理 ====================

    fun getSelectedRepositoryPaths(): List<String> = myState.selectedRepositoryPaths

    fun setSelectedRepositoryPaths(paths: List<String>) {
        myState.selectedRepositoryPaths = paths.toMutableList()
    }

    fun addSelectedRepository(path: String) {
        if (!myState.selectedRepositoryPaths.contains(path)) {
            myState.selectedRepositoryPaths.add(path)
        }
    }

    fun removeSelectedRepository(path: String) {
        myState.selectedRepositoryPaths.remove(path)
    }

    fun isRepositorySelected(path: String): Boolean {
        return myState.selectedRepositoryPaths.contains(path)
    }

    // ==================== 上次使用的配置 ====================

    fun saveLastConfig(sourceBranch: String, targetBranch: String) {
        myState.lastSourceBranch = sourceBranch
        myState.lastTargetBranch = targetBranch
    }

    fun getLastSourceBranch(): String = myState.lastSourceBranch

    fun getLastTargetBranch(): String = myState.lastTargetBranch

    fun saveLastTagVersion(version: String) {
        myState.lastTagVersion = version
    }

    fun getLastTagVersion(): String = myState.lastTagVersion

    // ==================== 分支配置 ====================

    fun getDevBranch(): String {
        return if (myState.useProjectSettings) myState.projectDevBranch
        else EasyGitSettings.getInstance().devBranchName
    }

    fun getTestBranch(): String {
        return if (myState.useProjectSettings) myState.projectTestBranch
        else EasyGitSettings.getInstance().testBranchName
    }

    fun getMainBranch(): String {
        return if (myState.useProjectSettings) myState.projectMainBranch
        else EasyGitSettings.getInstance().mainBranchName
    }
}
