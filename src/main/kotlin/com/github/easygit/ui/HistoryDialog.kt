package com.github.easygit.ui

import com.github.easygit.model.OperationHistory
import com.github.easygit.service.HistoryService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * 历史记录对话框
 */
class HistoryDialog(
    project: Project,
    private val histories: List<OperationHistory>
) : DialogWrapper(project) {

    private val tableModel = HistoryTableModel(histories)

    init {
        title = "操作历史"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(800, 500)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 统计信息
        val successCount = histories.count { it.success }
        val failCount = histories.size - successCount
        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statsPanel.add(JBLabel("总计: ${histories.size} 条记录"))
        statsPanel.add(JBLabel(" | "))
        statsPanel.add(JBLabel("<html><font color='green'>成功: $successCount</font></html>"))
        statsPanel.add(JBLabel(" | "))
        statsPanel.add(JBLabel("<html><font color='red'>失败: $failCount</font></html>"))
        panel.add(statsPanel, BorderLayout.NORTH)

        // 历史表格
        val table = JBTable(tableModel)
        table.columnModel.getColumn(0).preferredWidth = 130
        table.columnModel.getColumn(1).preferredWidth = 100
        table.columnModel.getColumn(2).preferredWidth = 100
        table.columnModel.getColumn(3).preferredWidth = 100
        table.columnModel.getColumn(4).preferredWidth = 100
        table.columnModel.getColumn(5).preferredWidth = 50
        table.columnModel.getColumn(6).preferredWidth = 200

        // 结果列渲染器
        table.columnModel.getColumn(5).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (value == "成功") {
                    foreground = Color(0, 128, 0)
                } else {
                    foreground = Color.RED
                }
                return component
            }
        }

        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 清空按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val clearButton = JButton("清空历史")
        clearButton.addActionListener {
            val confirm = JOptionPane.showConfirmDialog(
                panel,
                "确定要清空所有历史记录吗？",
                "确认",
                JOptionPane.YES_NO_OPTION
            )
            if (confirm == JOptionPane.YES_OPTION) {
                HistoryService.getInstance().clearHistory()
                tableModel.clear()
            }
        }
        buttonPanel.add(clearButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    private class HistoryTableModel(
        private var histories: List<OperationHistory>
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("时间", "操作类型", "仓库", "源分支", "目标/Tag", "结果", "消息")

        override fun getRowCount(): Int = histories.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val history = histories[rowIndex]
            return when (columnIndex) {
                0 -> history.getFormattedTime()
                1 -> history.operationType.displayName
                2 -> history.repositoryName
                3 -> history.sourceBranch
                4 -> if (history.tagName.isNotBlank()) history.tagName else history.targetBranch
                5 -> if (history.success) "成功" else "失败"
                6 -> history.message
                else -> ""
            }
        }

        fun clear() {
            histories = emptyList()
            fireTableDataChanged()
        }
    }
}
