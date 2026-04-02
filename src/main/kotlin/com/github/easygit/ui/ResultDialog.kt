package com.github.easygit.ui

import com.github.easygit.model.BatchOperationResult
import com.github.easygit.model.TagResult
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * 操作结果对话框
 */
class ResultDialog(
    project: Project,
    private val title: String,
    private val results: List<BatchOperationResult>
) : DialogWrapper(project) {

    private val tableModel = ResultTableModel(results)

    init {
        setTitle(title)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(700, 400)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 统计信息
        val successCount = results.count { it.success }
        val failCount = results.size - successCount
        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statsPanel.add(JBLabel("总计: ${results.size}"))
        statsPanel.add(JBLabel(" | "))
        statsPanel.add(JBLabel("<html><font color='green'>成功: $successCount</font></html>"))
        statsPanel.add(JBLabel(" | "))
        statsPanel.add(JBLabel("<html><font color='red'>失败: $failCount</font></html>"))
        panel.add(statsPanel, BorderLayout.NORTH)

        // 结果表格
        val table = JBTable(tableModel)
        table.columnModel.getColumn(0).preferredWidth = 150
        table.columnModel.getColumn(1).preferredWidth = 60
        table.columnModel.getColumn(2).preferredWidth = 100
        table.columnModel.getColumn(3).preferredWidth = 300

        // 设置状态列的颜色渲染
        table.columnModel.getColumn(1).cellRenderer = object : DefaultTableCellRenderer() {
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

        // 如果有 Tag 结果，添加复制按钮
        val tagResults = results.filter { it.tagName != null && it.success }
        if (tagResults.isNotEmpty()) {
            val copyButton = JButton("复制所有 Tag 名称")
            copyButton.addActionListener {
                val tagList = tagResults.map { "${it.repositoryName} ${it.tagName}" }.joinToString("\n")
                val selection = StringSelection(tagList)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                JOptionPane.showMessageDialog(panel, "已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE)
            }
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
            buttonPanel.add(copyButton)
            panel.add(buttonPanel, BorderLayout.SOUTH)
        }

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    private class ResultTableModel(private val results: List<BatchOperationResult>) : AbstractTableModel() {

        private val columnNames = arrayOf("仓库名称", "状态", "Tag", "消息")

        override fun getRowCount(): Int = results.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val result = results[rowIndex]
            return when (columnIndex) {
                0 -> result.repositoryName
                1 -> if (result.success) "成功" else "失败"
                2 -> result.tagName ?: "-"
                3 -> result.message
                else -> ""
            }
        }
    }
}

/**
 * Tag 创建结果对话框
 */
class TagResultDialog(
    project: Project,
    private val results: List<TagResult>
) : DialogWrapper(project) {

    init {
        title = "Tag 创建结果"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(500, 350)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 统计信息
        val successCount = results.count { it.success }
        val failCount = results.size - successCount
        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statsPanel.add(JBLabel("总计: ${results.size}"))
        statsPanel.add(JBLabel(" | "))
        statsPanel.add(JBLabel("<html><font color='green'>成功: $successCount</font></html>"))
        if (failCount > 0) {
            statsPanel.add(JBLabel(" | "))
            statsPanel.add(JBLabel("<html><font color='red'>失败: $failCount</font></html>"))
        }
        panel.add(statsPanel, BorderLayout.NORTH)

        // 结果列表
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.font = textArea.font.deriveFont(14f)

        val sb = StringBuilder()
        sb.appendLine("已创建的 Tag:\n")
        sb.appendLine("=" .repeat(40))
        results.filter { it.success }.forEach { result ->
            sb.appendLine("${result.repositoryName} ${result.tagName}")
        }

        if (failCount > 0) {
            sb.appendLine()
            sb.appendLine("失败的操作:\n")
            sb.appendLine("=" .repeat(40))
            results.filter { !it.success }.forEach { result ->
                sb.appendLine("${result.repositoryName}: ${result.message}")
            }
        }

        textArea.text = sb.toString()
        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 复制按钮
        val copyButton = JButton("复制 Tag 列表")
        copyButton.addActionListener {
            val tagList = results.filter { it.success }
                .map { "${it.repositoryName}: ${it.tagName}" }
                .joinToString("\n")
            val selection = StringSelection(tagList)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(panel, "已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE)
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(copyButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
