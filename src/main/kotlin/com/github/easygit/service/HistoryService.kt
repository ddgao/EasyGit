package com.github.easygit.service

import com.github.easygit.model.OperationHistory
import com.github.easygit.model.OperationHistoryEntry
import com.github.easygit.model.OperationHistoryState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 操作历史服务
 * 负责记录和管理所有操作历史
 */
@State(
    name = "EasyGitHistory",
    storages = [Storage("EasyGitHistory.xml")]
)
@Service
class HistoryService : PersistentStateComponent<OperationHistoryState> {

    private var myState = OperationHistoryState()

    override fun getState(): OperationHistoryState = myState

    override fun loadState(state: OperationHistoryState) {
        myState = state
    }

    /**
     * 添加历史记录
     */
    fun addHistory(history: OperationHistory) {
        val entry = OperationHistoryEntry.fromOperationHistory(history)
        myState.histories.add(0, entry) // 添加到开头

        // 限制历史记录数量
        while (myState.histories.size > OperationHistoryState.MAX_HISTORY_SIZE) {
            myState.histories.removeAt(myState.histories.size - 1)
        }
    }

    /**
     * 获取所有历史记录
     */
    fun getAllHistories(): List<OperationHistory> {
        return myState.histories.mapNotNull {
            try {
                it.toOperationHistory()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取指定仓库的历史记录
     */
    fun getHistoriesByRepository(repositoryPath: String): List<OperationHistory> {
        return getAllHistories().filter { it.repositoryPath == repositoryPath }
    }

    /**
     * 获取最近的历史记录
     */
    fun getRecentHistories(limit: Int = 50): List<OperationHistory> {
        return getAllHistories().take(limit)
    }

    /**
     * 清空历史记录
     */
    fun clearHistory() {
        myState.histories.clear()
    }

    /**
     * 删除指定的历史记录
     */
    fun deleteHistory(id: Long) {
        myState.histories.removeIf { it.id == id }
    }

    /**
     * 获取成功的操作数量
     */
    fun getSuccessCount(): Int {
        return myState.histories.count { it.success }
    }

    /**
     * 获取失败的操作数量
     */
    fun getFailureCount(): Int {
        return myState.histories.count { !it.success }
    }

    companion object {
        fun getInstance(): HistoryService {
            return ApplicationManager.getApplication().getService(HistoryService::class.java)
        }
    }
}
