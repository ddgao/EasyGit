package com.github.easygit.ui

import com.github.easygit.git.GitOperations
import com.github.easygit.git.MergeExecutor
import com.github.easygit.git.TagManager
import com.github.easygit.model.BatchOperationResult
import com.github.easygit.model.MergeTarget
import com.github.easygit.model.TagConfig
import com.github.easygit.model.TagType
import com.github.easygit.service.HistoryService
import com.github.easygit.service.NotificationService
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.service.RepositoryService
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import git4idea.repo.GitRepository
import java.awt.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * 工具窗口工厂
 */
class EasyGitToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = EasyGitToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * 工具窗口主面板
 */
class EasyGitToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val settings = EasyGitSettings.getInstance()
    private val repoService = RepositoryService(project)
    private val historyService = HistoryService.getInstance()

    // 仓库列表
    private val repositories = mutableListOf<RepositoryInfo>()
    private val tableModel = RepositoryTableModel(repositories)
    private val repositoryTable = JBTable(tableModel)

    // 分支选择
    private val sourceBranchCombo = ComboBox<String>()

    // 状态栏
    private val statusLabel = JBLabel("就绪")

    init {
        setupUI()
        refreshRepositories()
    }

    private fun setupUI() {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        // 工具栏
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        // 仓库列表
        val centerPanel = createCenterPanel()
        add(centerPanel, BorderLayout.CENTER)

        // 状态栏
        val statusBar = createStatusBar()
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))

        // 刷新按钮
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener { refreshRepositories() }
        toolbar.add(refreshButton)

        toolbar.add(JSeparator(SwingConstants.VERTICAL))

        // 源分支选择
        toolbar.add(JBLabel("功能分支:"))
        sourceBranchCombo.isEditable = true
        sourceBranchCombo.preferredSize = Dimension(150, 25)
        toolbar.add(sourceBranchCombo)

        toolbar.add(JSeparator(SwingConstants.VERTICAL))

        // 合并按钮
        val mergeToDevButton = JButton("合并到 Dev")
        mergeToDevButton.addActionListener { performMerge(MergeTarget.DEV) }
        toolbar.add(mergeToDevButton)

        val mergeToTestButton = JButton("合并到 Test")
        mergeToTestButton.addActionListener { performMerge(MergeTarget.TEST) }
        toolbar.add(mergeToTestButton)

        val mergeToMainButton = JButton("合并到 Main")
        mergeToMainButton.addActionListener { performMerge(MergeTarget.MAIN) }
        toolbar.add(mergeToMainButton)

        toolbar.add(JSeparator(SwingConstants.VERTICAL))

        // 一键合并
        val oneClickButton = JButton("一键合并")
        oneClickButton.addActionListener { performOneClickMerge() }
        toolbar.add(oneClickButton)

        toolbar.add(JSeparator(SwingConstants.VERTICAL))

        // 打 Tag
        val tagButton = JButton("打 Tag")
        tagButton.addActionListener { openTagDialog() }
        toolbar.add(tagButton)

        return toolbar
    }

    private fun createCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 配置表格
        repositoryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        repositoryTable.columnModel.getColumn(0).preferredWidth = 30
        repositoryTable.columnModel.getColumn(0).maxWidth = 50
        repositoryTable.columnModel.getColumn(1).preferredWidth = 150
        repositoryTable.columnModel.getColumn(2).preferredWidth = 100
        repositoryTable.columnModel.getColumn(3).preferredWidth = 80
        repositoryTable.columnModel.getColumn(4).preferredWidth = 200

        // 状态列渲染器
        repositoryTable.columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                when (value) {
                    "就绪" -> foreground = Color.GRAY
                    "成功" -> foreground = Color(0, 128, 0)
                    "失败" -> foreground = Color.RED
                    "进行中" -> foreground = Color.BLUE
                }
                return component
            }
        }

        val scrollPane = JBScrollPane(repositoryTable)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 全选按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val selectAllButton = JButton("全选")
        selectAllButton.addActionListener { tableModel.selectAll(true) }
        buttonPanel.add(selectAllButton)

        val deselectAllButton = JButton("取消全选")
        deselectAllButton.addActionListener { tableModel.selectAll(false) }
        buttonPanel.add(deselectAllButton)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createStatusBar(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(5, 0, 0, 0)

        panel.add(statusLabel, BorderLayout.WEST)

        // 历史记录按钮
        val historyButton = JButton("查看历史")
        historyButton.addActionListener { showHistory() }
        panel.add(historyButton, BorderLayout.EAST)

        return panel
    }

    private fun refreshRepositories() {
        repositories.clear()

        // 从设置中获取仓库路径
        if (settings.repositoryPaths.isNotEmpty()) {
            val repos = repoService.loadRepositories(settings.repositoryPaths)
            repos.forEach { repo ->
                repositories.add(RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo,
                    selected = true
                ))
            }
        } else {
            // 使用当前项目的仓库
            repoService.getProjectRepositories().forEach { repo ->
                repositories.add(RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo,
                    selected = true
                ))
            }
        }

        tableModel.fireTableDataChanged()
        updateBranchCombo()
        updateStatus()
    }

    private fun updateBranchCombo() {
        sourceBranchCombo.removeAllItems()

        val branches = mutableSetOf<String>()
        repositories.forEach { repoInfo ->
            repoInfo.repository?.let { repo ->
                val gitOps = GitOperations(project)
                gitOps.getCurrentBranch(repo)?.let { branches.add(it) }
            }
        }

        branches.sorted().forEach { sourceBranchCombo.addItem(it) }
    }

    private fun updateStatus() {
        val selectedCount = repositories.count { it.selected }
        statusLabel.text = "已选择 $selectedCount / ${repositories.size} 个仓库"
    }

    private fun getSelectedRepositories(): List<RepositoryInfo> {
        return repositories.filter { it.selected }
    }

    private fun getSourceBranch(): String {
        return sourceBranchCombo.selectedItem?.toString() ?: ""
    }

    private fun performMerge(target: MergeTarget) {
        val selectedRepos = getSelectedRepositories()
        if (selectedRepos.isEmpty()) {
            NotificationService.warning(project, "EasyGit", "请选择至少一个仓库")
            return
        }

        val sourceBranch = getSourceBranch()
        if (sourceBranch.isBlank()) {
            NotificationService.warning(project, "EasyGit", "请选择源分支")
            return
        }

        val targetBranch = when (target) {
            MergeTarget.DEV -> settings.devBranchName
            MergeTarget.TEST -> settings.testBranchName
            MergeTarget.MAIN -> settings.mainBranchName
        }

        executeMerge(selectedRepos, sourceBranch, targetBranch)
    }

    private fun performOneClickMerge() {
        val selectedRepos = getSelectedRepositories()
        if (selectedRepos.isEmpty()) {
            NotificationService.warning(project, "EasyGit", "请选择至少一个仓库")
            return
        }

        val sourceBranch = getSourceBranch()
        if (sourceBranch.isBlank()) {
            NotificationService.warning(project, "EasyGit", "请选择源分支")
            return
        }

        val confirm = JOptionPane.showConfirmDialog(
            this,
            "将依次合并 $sourceBranch 到:\n" +
                    "  1. ${settings.devBranchName}\n" +
                    "  2. ${settings.testBranchName}\n" +
                    "  3. ${settings.mainBranchName}\n\n" +
                    "选中了 ${selectedRepos.size} 个仓库，确认执行？",
            "一键合并确认",
            JOptionPane.YES_NO_OPTION
        )

        if (confirm == JOptionPane.YES_OPTION) {
            executeOneClickMerge(selectedRepos, sourceBranch)
        }
    }

    private fun executeMerge(repos: List<RepositoryInfo>, sourceBranch: String, targetBranch: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "合并分支", true) {
            override fun run(indicator: ProgressIndicator) {
                val executor = MergeExecutor(project)
                val results = mutableListOf<BatchOperationResult>()

                repos.forEachIndexed { index, repoInfo ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repos.size
                    indicator.text = "正在处理: ${repoInfo.name}"

                    // 更新表格状态
                    ApplicationManager.getApplication().invokeLater {
                        repoInfo.status = "进行中"
                        tableModel.fireTableDataChanged()
                    }

                    val repo = repoInfo.repository
                    if (repo != null) {
                        val result = executor.executeMerge(
                            repository = repo,
                            sourceBranch = sourceBranch,
                            targetBranch = targetBranch,
                            autoPush = settings.autoPushAfterMerge,
                            fetchFirst = settings.autoFetchBeforeMerge
                        )
                        results.add(result)

                        // 更新表格状态
                        ApplicationManager.getApplication().invokeLater {
                            repoInfo.status = if (result.success) "成功" else "失败"
                            tableModel.fireTableDataChanged()
                        }
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = ResultDialog(project, "合并结果: $sourceBranch → $targetBranch", results)
                    dialog.show()
                }
            }
        })
    }

    private fun executeOneClickMerge(repos: List<RepositoryInfo>, sourceBranch: String) {
        val targets = listOf(settings.devBranchName, settings.testBranchName, settings.mainBranchName)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "一键合并", true) {
            override fun run(indicator: ProgressIndicator) {
                val executor = MergeExecutor(project)
                val allResults = mutableListOf<BatchOperationResult>()

                targets.forEachIndexed { targetIndex, targetBranch ->
                    repos.forEachIndexed { repoIndex, repoInfo ->
                        indicator.checkCanceled()
                        val overallProgress = (targetIndex * repos.size + repoIndex).toDouble() /
                                (targets.size * repos.size)
                        indicator.fraction = overallProgress
                        indicator.text = "合并到 $targetBranch: ${repoInfo.name}"

                        val repo = repoInfo.repository
                        if (repo != null) {
                            val result = executor.executeMerge(
                                repository = repo,
                                sourceBranch = sourceBranch,
                                targetBranch = targetBranch,
                                autoPush = settings.autoPushAfterMerge,
                                fetchFirst = settings.autoFetchBeforeMerge
                            )
                            allResults.add(BatchOperationResult(
                                repositoryName = "${repoInfo.name} → $targetBranch",
                                repositoryPath = result.repositoryPath,
                                success = result.success,
                                message = result.message
                            ))
                        }
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = ResultDialog(project, "一键合并结果", allResults)
                    dialog.show()
                    refreshRepositories()
                }
            }
        })
    }

    private fun openTagDialog() {
        val selectedRepos = getSelectedRepositories()
        if (selectedRepos.isEmpty()) {
            NotificationService.warning(project, "EasyGit", "请选择至少一个仓库")
            return
        }

        val dialog = TagDialog(project, selectedRepos)
        if (dialog.showAndGet()) {
            createTagsWithIndividualNames(
                dialog.getSelectedRepositoryTags(),
                dialog.getDescription(),
                dialog.isAutoPush()
            )
        }
    }

    private fun createTagsWithIndividualNames(
        repositoryTags: List<RepositoryTagInfo>,
        description: String,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 Tag", true) {
            override fun run(indicator: ProgressIndicator) {
                val tagManager = TagManager(project)
                val historyService = HistoryService.getInstance()
                val results = mutableListOf<com.github.easygit.model.TagResult>()

                repositoryTags.forEachIndexed { index, repoTag ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repositoryTags.size
                    indicator.text = "正在处理: ${repoTag.repositoryInfo.name}"

                    val repo = repoTag.repositoryInfo.repository
                    if (repo != null) {
                        val result = tagManager.createTagWithName(
                            repo,
                            repoTag.tagName,
                            description,
                            autoPush
                        )
                        results.add(result)

                        historyService.addHistory(com.github.easygit.model.OperationHistory(
                            operationType = com.github.easygit.model.OperationType.CREATE_TAG,
                            repositoryName = repoTag.repositoryInfo.name,
                            repositoryPath = repoTag.repositoryInfo.path,
                            sourceBranch = "origin/${EasyGitSettings.getInstance().tagBaseBranchName}",
                            tagName = result.tagName,
                            success = result.success,
                            message = result.message
                        ))
                    } else {
                        results.add(com.github.easygit.model.TagResult(
                            repositoryName = repoTag.repositoryInfo.name,
                            tagName = repoTag.tagName,
                            success = false,
                            message = "无法获取仓库对象"
                        ))
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = TagResultDialog(project, results)
                    dialog.show()
                }
            }
        })
    }

    @Deprecated("使用 createTagsWithName 代替")
    private fun createTags(
        repos: List<RepositoryInfo>,
        baseVersion: String,
        tagType: TagType,
        description: String,
        autoPush: Boolean
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 Tag", true) {
            override fun run(indicator: ProgressIndicator) {
                val tagManager = TagManager(project)
                val results = mutableListOf<com.github.easygit.model.TagResult>()

                repos.forEachIndexed { index, repoInfo ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / repos.size
                    indicator.text = "正在处理: ${repoInfo.name}"

                    val repo = repoInfo.repository
                    if (repo != null) {
                        val config = TagConfig(baseVersion, tagType, description, autoPush)
                        val result = tagManager.createTag(repo, config, autoPush)
                        results.add(result)
                    }
                }

                // 显示结果
                ApplicationManager.getApplication().invokeLater {
                    val dialog = TagResultDialog(project, results)
                    dialog.show()
                }
            }
        })
    }

    private fun showHistory() {
        val histories = historyService.getRecentHistories(100)
        if (histories.isEmpty()) {
            JOptionPane.showMessageDialog(this, "暂无操作历史", "历史记录", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val dialog = HistoryDialog(project, histories)
        dialog.show()
    }

    /**
     * 仓库表格数据模型
     */
    private inner class RepositoryTableModel(
        private val repos: MutableList<RepositoryInfo>
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("选择", "仓库名称", "当前分支", "状态", "路径")

        override fun getRowCount(): Int = repos.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == 0
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val repo = repos[rowIndex]
            return when (columnIndex) {
                0 -> repo.selected
                1 -> repo.name
                2 -> repo.currentBranch
                3 -> repo.status
                4 -> repo.path
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                repos[rowIndex].selected = aValue
                fireTableCellUpdated(rowIndex, columnIndex)
                updateStatus()
            }
        }

        fun selectAll(select: Boolean) {
            repos.forEach { it.selected = select }
            fireTableDataChanged()
            updateStatus()
        }
    }
}
