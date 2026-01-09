package com.github.easygit.ui

import com.github.easygit.git.TagManager
import com.github.easygit.model.TagType
import com.github.easygit.service.RepositoryInfo
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Tag 列表查看对话框
 */
class TagListDialog(
    parent: Window?,
    repoName: String,
    tags: List<String>
) : JDialog(parent, "Tag 列表 - $repoName", ModalityType.MODELESS) {

    init {
        val contentPanel = JPanel(BorderLayout(10, 10))
        contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        if (tags.isEmpty()) {
            contentPanel.add(JBLabel("该仓库没有 Tag"), BorderLayout.CENTER)
        } else {
            val listModel = DefaultListModel<String>()
            tags.forEachIndexed { index, tag ->
                listModel.addElement("${index + 1}. $tag")
            }
            val tagList = JList(listModel)
            tagList.selectionMode = ListSelectionModel.SINGLE_SELECTION

            val scrollPane = JBScrollPane(tagList)
            scrollPane.preferredSize = Dimension(350, 300)

            val headerLabel = JBLabel("最近 ${tags.size} 个 Tag（按时间降序）：")
            contentPanel.add(headerLabel, BorderLayout.NORTH)
            contentPanel.add(scrollPane, BorderLayout.CENTER)
        }

        val closeButton = JButton("关闭")
        closeButton.addActionListener { dispose() }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        buttonPanel.add(closeButton)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = contentPanel
        pack()
        setLocationRelativeTo(parent)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isAlwaysOnTop = true
    }
}

/**
 * 仓库 Tag 信息（包含独立的 Tag 名称）
 */
data class RepositoryTagInfo(
    val repositoryInfo: RepositoryInfo,
    var tagName: String,
    var selected: Boolean = true
)

/**
 * Tag 创建对话框
 * 支持为每个仓库设置独立的 Tag 名称
 */
class TagDialog(
    private val project: Project,
    private val repositories: List<RepositoryInfo>
) : DialogWrapper(project) {

    private val settings = EasyGitSettings.getInstance()

    // 仓库选择表格
    private val tableModel = RepositoryTableModel(repositories)
    private val repositoryTable = JBTable(tableModel)

    // Tag 配置
    private val tagTypeCombo = ComboBox(TagType.entries.toTypedArray())
    private val descriptionArea = JBTextArea(3, 30)

    // 选项
    private val autoPushCheckBox = JBCheckBox("创建后自动 Push Tag", true)

    // 批量设置 Tag 名称
    private val batchTagNameField = JBTextField()
    private val applyToAllButton = JButton("应用到所有仓库")
    private val resetButton = JButton("重置")

    // 结果
    private var selectedRepositoryTags: List<RepositoryTagInfo> = emptyList()
    private var tagType: TagType = TagType.NORMAL
    private var description: String = ""

    init {
        title = "创建 Tag"
        init()
        initDefaultValues()
        setupListeners()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.preferredSize = Dimension(750, 520)
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
        panel.border = BorderFactory.createTitledBorder("选择仓库并设置 Tag 名称")

        // 配置表格
        repositoryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        repositoryTable.columnModel.getColumn(0).preferredWidth = 30   // 选择
        repositoryTable.columnModel.getColumn(0).maxWidth = 50
        repositoryTable.columnModel.getColumn(1).preferredWidth = 120  // 仓库名称
        repositoryTable.columnModel.getColumn(2).preferredWidth = 80   // 当前分支
        repositoryTable.columnModel.getColumn(3).preferredWidth = 100  // 最新 Tag
        repositoryTable.columnModel.getColumn(4).preferredWidth = 150  // 新 Tag 名称
        repositoryTable.columnModel.getColumn(5).preferredWidth = 60   // 操作

        // 设置行高以便编辑
        repositoryTable.rowHeight = 25

        // 为"查看更多"列设置渲染器
        repositoryTable.columnModel.getColumn(5).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): java.awt.Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                text = "<html><font color='blue'><u>查看更多</u></font></html>"
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                return component
            }
        }

        // 为表格添加点击事件
        repositoryTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = repositoryTable.rowAtPoint(e.point)
                val col = repositoryTable.columnAtPoint(e.point)
                if (col == 5 && row >= 0) {
                    showMoreTags(row)
                }
            }
        })

        val scrollPane = JBScrollPane(repositoryTable)
        scrollPane.preferredSize = Dimension(730, 200)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
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

        // 批量设置区域
        buttonPanel.add(Box.createHorizontalStrut(20))
        buttonPanel.add(JBLabel("批量设置:"))
        batchTagNameField.preferredSize = Dimension(150, 25)
        batchTagNameField.toolTipText = "输入 Tag 名称后点击'应用到所有仓库'"
        buttonPanel.add(batchTagNameField)
        applyToAllButton.addActionListener {
            val tagName = batchTagNameField.text.trim()
            if (tagName.isNotBlank()) {
                tableModel.setAllTagNames(tagName)
            }
        }
        buttonPanel.add(applyToAllButton)

        // 重置按钮
        resetButton.toolTipText = "重置为每个仓库自动计算的 Tag 名称"
        resetButton.addActionListener {
            batchTagNameField.text = ""
            resetAllTagNames()
        }
        buttonPanel.add(resetButton)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createConfigPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("Tag 配置")

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        // Tag 类型
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JBLabel("Tag 类型:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        tagTypeCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is TagType) {
                    text = value.displayName
                }
                return this
            }
        }
        panel.add(tagTypeCombo, gbc)

        // 提示信息
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        val tipLabel = JBLabel("提示：可在表格的\"新 Tag 名称\"列中为每个仓库设置不同的 Tag 名称")
        tipLabel.foreground = java.awt.Color.GRAY
        panel.add(tipLabel, gbc)

        // 描述
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("描述 (可选):"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 0.5
        val scrollPane = JBScrollPane(descriptionArea)
        scrollPane.preferredSize = Dimension(400, 50)
        panel.add(scrollPane, gbc)

        // 选项
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST
        panel.add(autoPushCheckBox, gbc)

        return panel
    }

    private fun initDefaultValues() {
        // 异步为每个仓库计算默认 Tag 名称
        ApplicationManager.getApplication().executeOnPooledThread {
            val tagManager = TagManager(project)
            repositories.forEachIndexed { index, repoInfo ->
                val repo = repoInfo.repository
                if (repo != null) {
                    val suggestedTag = tagManager.suggestNextTagName(repo, TagType.NORMAL)
                    SwingUtilities.invokeLater {
                        tableModel.setTagName(index, suggestedTag)
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // Tag 类型变化时重新计算所有仓库的 Tag 名称
        tagTypeCombo.addActionListener {
            updateAllTagNames()
        }
    }

    private fun updateAllTagNames() {
        val type = tagTypeCombo.selectedItem as? TagType ?: TagType.NORMAL

        ApplicationManager.getApplication().executeOnPooledThread {
            val tagManager = TagManager(project)
            repositories.forEachIndexed { index, repoInfo ->
                val repo = repoInfo.repository
                if (repo != null) {
                    val suggestedTag = tagManager.suggestNextTagName(repo, type)
                    SwingUtilities.invokeLater {
                        tableModel.setTagName(index, suggestedTag)
                    }
                }
            }
        }
    }

    /**
     * 重置所有仓库的 Tag 名称为自动计算的值
     */
    private fun resetAllTagNames() {
        updateAllTagNames()
    }

    /**
     * 显示更多 Tag 对话框
     */
    private fun showMoreTags(rowIndex: Int) {
        val repoInfo = repositories[rowIndex]
        val repo = repoInfo.repository ?: return

        // 获取当前对话框的窗口
        val parentWindow = SwingUtilities.getWindowAncestor(repositoryTable)

        ApplicationManager.getApplication().executeOnPooledThread {
            val tagManager = TagManager(project)
            val tags = tagManager.getTopTags(repo, 20)

            SwingUtilities.invokeLater {
                // 使用非模态对话框显示 Tag 列表
                val tagListDialog = TagListDialog(parentWindow, repoInfo.name, tags)
                tagListDialog.isVisible = true
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        // 检查是否选择了仓库
        if (!tableModel.hasSelection()) {
            return ValidationInfo("请至少选择一个仓库", repositoryTable)
        }

        // 检查每个选中仓库的 Tag 名称
        val selectedRepos = tableModel.getSelectedRepositoryTags()
        for (repoTag in selectedRepos) {
            if (repoTag.tagName.isBlank()) {
                return ValidationInfo("仓库 ${repoTag.repositoryInfo.name} 的 Tag 名称不能为空", repositoryTable)
            }
        }

        return null
    }

    override fun doOKAction() {
        selectedRepositoryTags = tableModel.getSelectedRepositoryTags()
        tagType = tagTypeCombo.selectedItem as? TagType ?: TagType.NORMAL
        description = descriptionArea.text.trim()
        super.doOKAction()
    }

    /**
     * 获取选中的仓库及其 Tag 名称
     */
    fun getSelectedRepositoryTags(): List<RepositoryTagInfo> = selectedRepositoryTags

    /**
     * 获取选中的仓库列表（向后兼容）
     */
    fun getSelectedRepositories(): List<RepositoryInfo> = selectedRepositoryTags.map { it.repositoryInfo }

    /**
     * 获取第一个仓库的 Tag 名称（向后兼容，多仓库时请使用 getSelectedRepositoryTags）
     */
    fun getTagName(): String = selectedRepositoryTags.firstOrNull()?.tagName ?: ""

    fun getTagType(): TagType = tagType
    fun getDescription(): String = description
    fun isAutoPush(): Boolean = autoPushCheckBox.isSelected

    // 保持向后兼容
    @Deprecated("使用 getTagName() 代替", ReplaceWith("getTagName()"))
    fun getBaseVersion(): String = getTagName()

    /**
     * 仓库表格数据模型
     */
    private inner class RepositoryTableModel(
        private val repositories: List<RepositoryInfo>
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("选择", "仓库名称", "当前分支", "最新 Tag", "新 Tag 名称", "操作")
        private val selected = BooleanArray(repositories.size) { true }
        private val latestTags = Array(repositories.size) { "加载中..." }
        private val newTagNames = Array(repositories.size) { "计算中..." }

        init {
            // 异步加载每个仓库的最新 Tag
            loadLatestTags()
        }

        private fun loadLatestTags() {
            ApplicationManager.getApplication().executeOnPooledThread {
                val tagManager = TagManager(project)
                repositories.forEachIndexed { index, repoInfo ->
                    val repo = repoInfo.repository
                    if (repo != null) {
                        val latestTag = tagManager.getLatestVersionTag(repo) ?: "无"
                        latestTags[index] = latestTag
                        // 在 EDT 线程更新 UI
                        ApplicationManager.getApplication().invokeLater {
                            fireTableCellUpdated(index, 3)
                        }
                    } else {
                        latestTags[index] = "N/A"
                        ApplicationManager.getApplication().invokeLater {
                            fireTableCellUpdated(index, 3)
                        }
                    }
                }
            }
        }

        fun setTagName(rowIndex: Int, tagName: String) {
            if (rowIndex in newTagNames.indices) {
                newTagNames[rowIndex] = tagName
                fireTableCellUpdated(rowIndex, 4)
            }
        }

        fun setAllTagNames(tagName: String) {
            for (i in newTagNames.indices) {
                newTagNames[i] = tagName
            }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = repositories.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            // 选择列和新 Tag 名称列可编辑
            return columnIndex == 0 || columnIndex == 4
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val repo = repositories[rowIndex]
            return when (columnIndex) {
                0 -> selected[rowIndex]
                1 -> repo.name
                2 -> repo.currentBranch
                3 -> latestTags[rowIndex]
                4 -> newTagNames[rowIndex]
                5 -> "查看更多"
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            when (columnIndex) {
                0 -> if (aValue is Boolean) {
                    selected[rowIndex] = aValue
                    fireTableCellUpdated(rowIndex, columnIndex)
                }
                4 -> if (aValue is String) {
                    newTagNames[rowIndex] = aValue
                    fireTableCellUpdated(rowIndex, columnIndex)
                }
            }
        }

        fun selectAll(select: Boolean) {
            for (i in selected.indices) {
                selected[i] = select
            }
            fireTableDataChanged()
        }

        fun hasSelection(): Boolean = selected.any { it }

        fun getSelectedRepositoryTags(): List<RepositoryTagInfo> {
            return repositories.mapIndexedNotNull { index, repoInfo ->
                if (selected[index]) {
                    RepositoryTagInfo(
                        repositoryInfo = repoInfo,
                        tagName = newTagNames[index],
                        selected = true
                    )
                } else null
            }
        }

        fun getSelectedRepositories(): List<RepositoryInfo> {
            return repositories.filterIndexed { index, _ -> selected[index] }
        }
    }
}
