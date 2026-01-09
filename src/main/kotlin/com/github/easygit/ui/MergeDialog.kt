package com.github.easygit.ui

import com.github.easygit.git.GitOperations
import com.github.easygit.model.MergeTarget
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

/**
 * 合并配置对话框
 */
class MergeDialog(
    private val project: Project,
    private val repositories: List<RepositoryInfo>,
    private val defaultTarget: MergeTarget? = null
) : DialogWrapper(project) {

    private val settings = EasyGitSettings.getInstance()

    // 仓库选择表格
    private val tableModel = RepositoryTableModel(repositories)
    private val repositoryTable = JBTable(tableModel)

    // 分支数据
    private val allLocalBranches = mutableListOf<String>()
    private val allRemoteBranches = mutableListOf<String>()
    private val currentBranches = mutableSetOf<String>()

    // 源分支选择（带搜索的列表）
    private val sourceSearchField = SearchTextField()
    private val sourceListModel = DefaultListModel<String>()
    private val sourceBranchList = JBList(sourceListModel)

    // 目标分支选择（带搜索的列表）
    private val targetSearchField = SearchTextField()
    private val targetListModel = DefaultListModel<String>()
    private val targetBranchList = JBList(targetListModel)

    // 选项（名称与全局配置统一，默认值从全局配置读取）
    private val autoFetchCheckBox = JBCheckBox("合并前自动 Fetch 最新代码", settings.autoFetchBeforeMerge)
    private val autoPushCheckBox = JBCheckBox("合并后自动 Push 到远程", settings.autoPushAfterMerge)

    // 结果
    private var selectedRepositories: List<RepositoryInfo> = emptyList()
    private var sourceBranch: String = ""
    private var targetBranch: String = ""

    init {
        title = "合并分支"
        init()
        initBranchData()
        setupBranchLists()

        // 设置默认目标分支
        if (defaultTarget != null) {
            selectBranchInList(targetBranchList, defaultTarget.branchName)
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.preferredSize = Dimension(700, 550)
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 仓库选择区域
        val repoPanel = createRepositoryPanel()
        mainPanel.add(repoPanel, BorderLayout.CENTER)

        // 配置区域
        val configPanel = createConfigPanel()
        mainPanel.add(configPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createRepositoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("选择仓库")

        // 配置表格
        repositoryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        repositoryTable.columnModel.getColumn(0).preferredWidth = 30
        repositoryTable.columnModel.getColumn(0).maxWidth = 50
        repositoryTable.columnModel.getColumn(1).preferredWidth = 150
        repositoryTable.columnModel.getColumn(2).preferredWidth = 100
        repositoryTable.columnModel.getColumn(3).preferredWidth = 200

        val scrollPane = JBScrollPane(repositoryTable)
        scrollPane.preferredSize = Dimension(580, 200)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 全选/取消全选按钮
        val buttonPanel = JPanel()
        val selectAllButton = JButton("全选")
        selectAllButton.addActionListener {
            tableModel.selectAll(true)
        }
        val deselectAllButton = JButton("取消全选")
        deselectAllButton.addActionListener {
            tableModel.selectAll(false)
        }
        buttonPanel.add(selectAllButton)
        buttonPanel.add(deselectAllButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createConfigPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("合并配置")

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        // 源分支面板
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 0.5
        gbc.weighty = 1.0
        panel.add(createBranchSelectionPanel("源分支:", sourceSearchField, sourceBranchList, true), gbc)

        // 目标分支面板
        gbc.gridx = 2
        panel.add(createBranchSelectionPanel("目标分支:", targetSearchField, targetBranchList, false), gbc)

        // 选项
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        panel.add(autoFetchCheckBox, gbc)

        gbc.gridx = 2
        gbc.gridwidth = 2
        panel.add(autoPushCheckBox, gbc)

        return panel
    }

    /**
     * 创建分支选择面板（包含标签、搜索框、列表）
     */
    private fun createBranchSelectionPanel(
        label: String,
        searchField: SearchTextField,
        branchList: JBList<String>,
        isSource: Boolean
    ): JPanel {
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        // 标签
        panel.add(JBLabel(label), BorderLayout.NORTH)

        // 搜索框
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterBranches(searchField.text, isSource)
            override fun removeUpdate(e: DocumentEvent?) = filterBranches(searchField.text, isSource)
            override fun changedUpdate(e: DocumentEvent?) = filterBranches(searchField.text, isSource)
        })
        panel.add(searchField, BorderLayout.CENTER)

        // 分支列表
        branchList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        branchList.visibleRowCount = 8
        val scrollPane = JBScrollPane(branchList)
        scrollPane.preferredSize = Dimension(250, 150)
        panel.add(scrollPane, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 初始化分支数据
     */
    private fun initBranchData() {
        val localBranchSet = mutableSetOf<String>()
        val remoteBranchSet = mutableSetOf<String>()

        repositories.forEach { repoInfo ->
            repoInfo.repository?.let { repo ->
                val gitOps = GitOperations(project)
                localBranchSet.addAll(gitOps.getLocalBranches(repo))
                remoteBranchSet.addAll(gitOps.getRemoteBranches(repo))
                gitOps.getCurrentBranch(repo)?.let { currentBranches.add(it) }
            }
        }

        allLocalBranches.addAll(localBranchSet.sorted())
        allRemoteBranches.addAll(remoteBranchSet.sorted())
    }

    /**
     * 设置分支列表
     */
    private fun setupBranchLists() {
        // 源分支列表：当前分支优先，然后本地分支，最后远端分支
        populateBranchList(sourceListModel, prioritizeCurrentBranches = true)

        // 目标分支列表：配置的环境分支优先，然后本地分支，最后远端分支
        populateBranchList(targetListModel, prioritizeCurrentBranches = false)

        // 默认选择
        if (currentBranches.isNotEmpty()) {
            selectBranchInList(sourceBranchList, currentBranches.first())
        }
        selectBranchInList(targetBranchList, settings.devBranchName)
    }

    /**
     * 填充分支列表
     */
    private fun populateBranchList(
        listModel: DefaultListModel<String>,
        prioritizeCurrentBranches: Boolean,
        filter: String = ""
    ) {
        listModel.clear()

        val filterLower = filter.lowercase()

        // 确定优先显示的分支
        val priorityBranches = if (prioritizeCurrentBranches) {
            currentBranches
        } else {
            setOf(settings.devBranchName, settings.testBranchName, settings.mainBranchName)
        }

        // 添加优先分支（本地）
        priorityBranches
            .filter { it in allLocalBranches && (filter.isEmpty() || it.lowercase().contains(filterLower)) }
            .sorted()
            .forEach { listModel.addElement(it) }

        // 添加其他本地分支
        allLocalBranches
            .filter { it !in priorityBranches && (filter.isEmpty() || it.lowercase().contains(filterLower)) }
            .forEach { listModel.addElement(it) }

        // 添加分隔符（如果有远端分支）
        val filteredRemoteBranches = allRemoteBranches
            .filter { filter.isEmpty() || it.lowercase().contains(filterLower) }

        if (filteredRemoteBranches.isNotEmpty() && listModel.size() > 0) {
            listModel.addElement("── 远端分支 ──")
        }

        // 添加远端分支
        filteredRemoteBranches.forEach { listModel.addElement(it) }
    }

    /**
     * 过滤分支列表
     */
    private fun filterBranches(filter: String, isSource: Boolean) {
        val listModel = if (isSource) sourceListModel else targetListModel
        populateBranchList(listModel, prioritizeCurrentBranches = isSource, filter = filter)
    }

    /**
     * 在列表中选择指定分支
     */
    private fun selectBranchInList(list: JBList<String>, branchName: String) {
        val model = list.model
        for (i in 0 until model.size) {
            if (model.getElementAt(i) == branchName) {
                list.selectedIndex = i
                list.ensureIndexIsVisible(i)
                break
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        // 检查是否选择了仓库
        if (!tableModel.hasSelection()) {
            return ValidationInfo("请至少选择一个仓库", repositoryTable)
        }

        // 检查源分支
        val source = sourceBranchList.selectedValue
        if (source.isNullOrBlank() || source == "── 远端分支 ──") {
            return ValidationInfo("请选择源分支", sourceBranchList)
        }

        // 检查目标分支
        val target = targetBranchList.selectedValue
        if (target.isNullOrBlank() || target == "── 远端分支 ──") {
            return ValidationInfo("请选择目标分支", targetBranchList)
        }

        // 检查源分支和目标分支不能相同
        if (source == target) {
            return ValidationInfo("源分支和目标分支不能相同", targetBranchList)
        }

        return null
    }

    override fun doOKAction() {
        selectedRepositories = tableModel.getSelectedRepositories()
        sourceBranch = sourceBranchList.selectedValue ?: ""
        targetBranch = targetBranchList.selectedValue ?: ""
        super.doOKAction()
    }

    fun getSelectedRepositories(): List<RepositoryInfo> = selectedRepositories
    fun getSourceBranch(): String = sourceBranch
    fun getTargetBranch(): String = targetBranch
    fun isAutoFetch(): Boolean = autoFetchCheckBox.isSelected
    fun isAutoPush(): Boolean = autoPushCheckBox.isSelected

    /**
     * 仓库表格数据模型
     */
    private class RepositoryTableModel(
        private val repositories: List<RepositoryInfo>
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("选择", "仓库名称", "当前分支", "路径")
        private val selected = BooleanArray(repositories.size) { true }

        override fun getRowCount(): Int = repositories.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == 0
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val repo = repositories[rowIndex]
            return when (columnIndex) {
                0 -> selected[rowIndex]
                1 -> repo.name
                2 -> repo.currentBranch
                3 -> repo.path
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                selected[rowIndex] = aValue
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }

        fun selectAll(select: Boolean) {
            for (i in selected.indices) {
                selected[i] = select
            }
            fireTableDataChanged()
        }

        fun hasSelection(): Boolean = selected.any { it }

        fun getSelectedRepositories(): List<RepositoryInfo> {
            return repositories.filterIndexed { index, _ -> selected[index] }
        }
    }
}
