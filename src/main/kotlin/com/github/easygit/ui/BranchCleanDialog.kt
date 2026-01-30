package com.github.easygit.ui

import com.github.easygit.model.BranchDeleteResult
import com.github.easygit.model.BranchFilterConfig
import com.github.easygit.model.BranchInfo
import com.github.easygit.service.BranchCleanService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class BranchCleanDialog(
    private val project: Project,
    private val repository: GitRepository
) : DialogWrapper(project) {

    private val branchCleanService = BranchCleanService.getInstance(project)
    private var allBranches: List<BranchInfo> = emptyList()
    private var filteredBranches: List<BranchInfo> = emptyList()

    private val tableModel = BranchTableModel()
    private val branchTable = JBTable(tableModel)

    private val searchField = SearchTextField()
    private val monthsSpinner = JSpinner(SpinnerNumberModel(0, 0, 24, 1))
    private val mergedOnlyCheckBox = JBCheckBox("仅已合并")
    private val unmergedOnlyCheckBox = JBCheckBox("仅未合并")
    private val authorComboBox = JComboBox<String>()

    private val statusLabel = JBLabel("正在加载分支...")
    private val statsLabel = JBLabel("")

    init {
        title = "分支清理 - ${repository.root.name}"
        setOKButtonText("删除选中的分支")
        setCancelButtonText("关闭")
        init()
        loadBranches()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.preferredSize = Dimension(900, 600)
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        mainPanel.add(createFilterPanel(), BorderLayout.NORTH)
        mainPanel.add(createTablePanel(), BorderLayout.CENTER)
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createFilterPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("筛选条件")

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JBLabel("搜索:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        searchField.preferredSize = Dimension(200, 30)
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilters()
            override fun removeUpdate(e: DocumentEvent?) = applyFilters()
            override fun changedUpdate(e: DocumentEvent?) = applyFilters()
        })
        panel.add(searchField, gbc)

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel("过期月数(0=全部):"), gbc)

        gbc.gridx = 3
        monthsSpinner.preferredSize = Dimension(60, 30)
        monthsSpinner.addChangeListener { applyFilters() }
        panel.add(monthsSpinner, gbc)

        gbc.gridx = 4
        mergedOnlyCheckBox.addActionListener {
            if (mergedOnlyCheckBox.isSelected) unmergedOnlyCheckBox.isSelected = false
            applyFilters()
        }
        panel.add(mergedOnlyCheckBox, gbc)

        gbc.gridx = 5
        unmergedOnlyCheckBox.addActionListener {
            if (unmergedOnlyCheckBox.isSelected) mergedOnlyCheckBox.isSelected = false
            applyFilters()
        }
        panel.add(unmergedOnlyCheckBox, gbc)

        gbc.gridx = 6
        panel.add(JBLabel("作者:"), gbc)

        gbc.gridx = 7
        authorComboBox.preferredSize = Dimension(150, 30)
        authorComboBox.addActionListener { applyFilters() }
        panel.add(authorComboBox, gbc)

        return panel
    }

    private fun createTablePanel(): JPanel {
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = BorderFactory.createTitledBorder("分支列表")

        branchTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        branchTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        branchTable.rowHeight = 25

        branchTable.columnModel.getColumn(0).preferredWidth = 30
        branchTable.columnModel.getColumn(0).maxWidth = 50
        branchTable.columnModel.getColumn(1).preferredWidth = 200
        branchTable.columnModel.getColumn(2).preferredWidth = 100
        branchTable.columnModel.getColumn(3).preferredWidth = 120
        branchTable.columnModel.getColumn(4).preferredWidth = 100
        branchTable.columnModel.getColumn(5).preferredWidth = 150

        branchTable.columnModel.getColumn(4).cellRenderer = MergedStatusCellRenderer()

        val scrollPane = JBScrollPane(branchTable)
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val selectAllButton = JButton("全选")
        selectAllButton.addActionListener { tableModel.selectAll(true) }
        val deselectAllButton = JButton("取消全选")
        deselectAllButton.addActionListener { tableModel.selectAll(false) }
        val selectMergedButton = JButton("选择已合并")
        selectMergedButton.addActionListener { tableModel.selectMerged() }
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener { loadBranches() }

        buttonPanel.add(selectAllButton)
        buttonPanel.add(deselectAllButton)
        buttonPanel.add(selectMergedButton)
        buttonPanel.add(refreshButton)
        buttonPanel.add(Box.createHorizontalStrut(20))
        buttonPanel.add(statsLabel)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createBottomPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        leftPanel.add(statusLabel)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val viewLogButton = JButton("查看删除日志")
        viewLogButton.addActionListener { showDeleteLog() }
        rightPanel.add(viewLogButton)

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    private fun loadBranches() {
        statusLabel.text = "正在加载分支..."
        isOKActionEnabled = false

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载分支信息", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.0
                    indicator.text = "正在 Fetch 远程仓库..."
                    
                    indicator.fraction = 0.3
                    indicator.text = "正在获取分支列表..."
                    
                    val branches = branchCleanService.loadBranches(repository, fetchFirst = true)
                    
                    indicator.fraction = 0.8
                    indicator.text = "正在获取作者列表..."
                    
                    val authors = branchCleanService.getAllAuthors(repository)
                    
                    indicator.fraction = 1.0
                    indicator.text = "加载完成"

                    ApplicationManager.getApplication().invokeLater {
                        allBranches = branches
                        updateAuthorComboBox(authors)
                        applyFilters()
                        if (allBranches.isEmpty()) {
                            statusLabel.text = "未找到远程分支（受保护分支已排除）"
                        } else {
                            statusLabel.text = "已加载 ${allBranches.size} 个分支（已排除受保护分支）"
                        }
                        isOKActionEnabled = true
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(BranchCleanDialog::class.java)
                        .error("加载分支失败", e)
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = "加载失败: ${e.message}"
                        isOKActionEnabled = false
                    }
                }
            }

            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "已取消加载"
                    isOKActionEnabled = false
                }
            }
        })
    }

    private fun updateAuthorComboBox(authors: List<String>) {
        authorComboBox.removeAllItems()
        authorComboBox.addItem("全部作者")
        authors.forEach { authorComboBox.addItem(it) }
    }

    private fun applyFilters() {
        val monthsOld = (monthsSpinner.value as Int).let { if (it == 0) null else it }
        val author = (authorComboBox.selectedItem as? String)?.let {
            if (it == "全部作者") null else it
        }
        val searchKeyword = searchField.text.takeIf { it.isNotBlank() }

        val config = BranchFilterConfig(
            monthsOld = monthsOld,
            mergedOnly = mergedOnlyCheckBox.isSelected,
            unmergedOnly = unmergedOnlyCheckBox.isSelected,
            author = author,
            searchKeyword = searchKeyword
        )

        filteredBranches = branchCleanService.filterBranches(allBranches, config)
        tableModel.setBranches(filteredBranches)
        updateStats()
    }

    private fun updateStats() {
        val total = filteredBranches.size
        val merged = filteredBranches.count { it.isMergedToAny() }
        val unmerged = total - merged
        val selected = tableModel.getSelectedCount()

        statsLabel.text = "共 $total 个分支 | 已合并: $merged | 未合并: $unmerged | 已选择: $selected"
    }

    private fun showDeleteLog() {
        val logContent = branchCleanService.readDeleteLog()
        val logPath = branchCleanService.getLogFilePath()

        if (logContent.isBlank()) {
            Messages.showInfoMessage(project, "暂无删除记录", "删除日志")
            return
        }

        val dialog = object : DialogWrapper(project) {
            init {
                title = "删除日志"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JPanel(BorderLayout())
                panel.preferredSize = Dimension(600, 400)

                val textArea = JTextArea(logContent)
                textArea.isEditable = false
                textArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)

                panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
                panel.add(JBLabel("日志文件: $logPath"), BorderLayout.SOUTH)

                return panel
            }
        }
        dialog.show()
    }

    override fun doOKAction() {
        val selectedBranches = tableModel.getSelectedBranches()

        if (selectedBranches.isEmpty()) {
            Messages.showWarningDialog(project, "请至少选择一个分支", "提示")
            return
        }

        val unmergedCount = selectedBranches.count { !it.isMergedToAny() }
        val warningMessage = if (unmergedCount > 0) {
            "确定要删除 ${selectedBranches.size} 个分支吗？\n\n" +
            "⚠️ 警告: 其中 $unmergedCount 个分支尚未合并到任何保护分支！\n\n" +
            "此操作将同时删除本地和远程分支，无法撤销！"
        } else {
            "确定要删除 ${selectedBranches.size} 个分支吗？\n\n" +
            "此操作将同时删除本地和远程分支，无法撤销！"
        }

        val result = Messages.showYesNoDialog(
            project,
            warningMessage,
            "确认删除",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) return

        deleteBranches(selectedBranches)
    }

    private fun deleteBranches(branches: List<BranchInfo>) {
        isOKActionEnabled = false
        statusLabel.text = "正在删除分支..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "删除分支", false) {
            override fun run(indicator: ProgressIndicator) {
                val branchNames = branches.map { it.name }
                val results = branchCleanService.deleteBranches(repository, branchNames)

                ApplicationManager.getApplication().invokeLater {
                    showDeleteResults(results)
                    loadBranches()
                }
            }
        })
    }

    private fun showDeleteResults(results: List<BranchDeleteResult>) {
        val successCount = results.count { it is BranchDeleteResult.Success }
        val failCount = results.count { it is BranchDeleteResult.Error }

        val message = StringBuilder()
        message.append("删除完成！\n\n")
        message.append("成功: $successCount 个分支\n")

        if (failCount > 0) {
            message.append("失败: $failCount 个分支\n\n")
            message.append("失败详情:\n")
            results.filterIsInstance<BranchDeleteResult.Error>().forEach {
                message.append("  - ${it.branchName}: ${it.errorMessage}\n")
            }
        }

        if (failCount > 0) {
            Messages.showWarningDialog(project, message.toString(), "删除结果")
        } else {
            Messages.showInfoMessage(project, message.toString(), "删除结果")
        }
    }

    private inner class BranchTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("选择", "分支名称", "作者", "最后提交", "合并状态", "最近提交")
        private var branches: List<BranchInfo> = emptyList()
        private var selected: BooleanArray = BooleanArray(0)

        fun setBranches(newBranches: List<BranchInfo>) {
            branches = newBranches
            selected = BooleanArray(branches.size) { false }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = branches.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val branch = branches[rowIndex]
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

            return when (columnIndex) {
                0 -> selected[rowIndex]
                1 -> branch.name
                2 -> branch.author
                3 -> "${branch.lastCommitDate.format(dateFormatter)} (${branch.getDaysAgo()}天前)"
                4 -> {
                    val mergedList = branch.getMergedBranches()
                    if (mergedList.isEmpty()) "未合并" else "已合并: ${mergedList.joinToString(", ")}"
                }
                5 -> branch.lastCommits.firstOrNull()?.let { "${it.hash} ${it.message}" } ?: ""
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                selected[rowIndex] = aValue
                fireTableCellUpdated(rowIndex, columnIndex)
                updateStats()
            }
        }

        fun selectAll(select: Boolean) {
            for (i in selected.indices) {
                selected[i] = select
            }
            fireTableDataChanged()
            updateStats()
        }

        fun selectMerged() {
            for (i in branches.indices) {
                selected[i] = branches[i].isMergedToAny()
            }
            fireTableDataChanged()
            updateStats()
        }

        fun getSelectedCount(): Int = selected.count { it }

        fun getSelectedBranches(): List<BranchInfo> {
            return branches.filterIndexed { index, _ -> selected[index] }
        }
    }

    private class MergedStatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            val text = value?.toString() ?: ""
            if (!isSelected) {
                foreground = if (text.startsWith("已合并")) Color(0, 128, 0) else Color(200, 0, 0)
            }

            return component
        }
    }
}
