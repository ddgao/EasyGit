package com.github.easygit.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * 设置页面
 */
class EasyGitConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private val settings = EasyGitSettings.getInstance()

    // 仓库路径列表
    private val repositoryListModel = DefaultListModel<String>()
    private val repositoryList = JBList(repositoryListModel)

    // 分支配置
    private val devBranchField = JBTextField()
    private val testBranchField = JBTextField()
    private val mainBranchField = JBTextField()
    private val tagBaseBranchField = JBTextField()

    // 自动化选项
    private val autoFetchCheckBox = JBCheckBox("合并前自动 Fetch 最新代码")
    private val autoPushCheckBox = JBCheckBox("合并后自动 Push 到远程")

    // 扫描配置
    private val scanRootPathField = TextFieldWithBrowseButton()
    private val scanMaxDepthField = JBTextField()

    override fun getDisplayName(): String = "EasyGit"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        val tabbedPane = JTabbedPane()

        // 仓库配置标签页
        tabbedPane.addTab("仓库配置", createRepositoryPanel())

        // 分支配置标签页
        tabbedPane.addTab("分支配置", createBranchPanel())

        // 自动化选项标签页
        tabbedPane.addTab("自动化选项", createAutomationPanel())

        mainPanel!!.add(tabbedPane, BorderLayout.CENTER)

        // 加载当前设置
        reset()

        return mainPanel!!
    }

    private fun createRepositoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 仓库列表
        val listPanel = JPanel(BorderLayout())
        listPanel.border = BorderFactory.createTitledBorder("已配置的仓库")

        repositoryList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val decorator = ToolbarDecorator.createDecorator(repositoryList)
            .setAddAction {
                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileChooser.dialogTitle = "选择 Git 仓库目录"
                if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                    val path = fileChooser.selectedFile.absolutePath
                    if (!repositoryListModel.contains(path)) {
                        repositoryListModel.addElement(path)
                    }
                }
            }
            .setRemoveAction {
                val selectedIndex = repositoryList.selectedIndex
                if (selectedIndex >= 0) {
                    repositoryListModel.remove(selectedIndex)
                }
            }
            .disableUpDownActions()

        listPanel.add(decorator.createPanel(), BorderLayout.CENTER)
        listPanel.preferredSize = Dimension(500, 200)

        // 扫描配置
        val scanPanel = JPanel(GridBagLayout())
        scanPanel.border = BorderFactory.createTitledBorder("批量扫描")

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.gridy = 0
        scanPanel.add(JBLabel("扫描根目录:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        scanRootPathField.addBrowseFolderListener(
            "选择扫描根目录",
            "选择包含多个 Git 仓库的根目录",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        scanPanel.add(scanRootPathField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        scanPanel.add(JBLabel("最大扫描深度:"), gbc)

        gbc.gridx = 1
        scanMaxDepthField.preferredSize = Dimension(50, 25)
        scanPanel.add(scanMaxDepthField, gbc)

        gbc.gridx = 2
        val scanButton = JButton("扫描并添加")
        scanButton.addActionListener { scanAndAddRepositories() }
        scanPanel.add(scanButton, gbc)

        panel.add(listPanel, BorderLayout.CENTER)
        panel.add(scanPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createBranchPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        // 目标分支配置
        val branchPanel = JPanel(GridBagLayout())
        branchPanel.border = BorderFactory.createTitledBorder("目标分支名称")

        gbc.gridx = 0
        gbc.gridy = 0
        branchPanel.add(JBLabel("开发环境分支:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        devBranchField.preferredSize = Dimension(150, 25)
        branchPanel.add(devBranchField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        branchPanel.add(JBLabel("测试环境分支:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        branchPanel.add(testBranchField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        branchPanel.add(JBLabel("生产环境分支:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        branchPanel.add(mainBranchField, gbc)

        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        branchPanel.add(JBLabel("打 Tag 基准分支:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        tagBaseBranchField.toolTipText = "留空则使用生产环境分支"
        branchPanel.add(tagBaseBranchField, gbc)

        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        val tagBaseBranchNote = JBLabel("<html><font color='gray'>提示：打 Tag 将基于远端分支 origin/{基准分支} 创建</font></html>")
        branchPanel.add(tagBaseBranchNote, gbc)
        gbc.gridwidth = 1

        // 主面板布局
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(branchPanel, gbc)

        gbc.gridy = 1
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc) // 占位

        return panel
    }

    private fun createAutomationPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        // 合并选项
        val mergePanel = JPanel(GridBagLayout())
        mergePanel.border = BorderFactory.createTitledBorder("合并选项")

        gbc.gridx = 0
        gbc.gridy = 0
        mergePanel.add(autoFetchCheckBox, gbc)

        gbc.gridy = 1
        mergePanel.add(autoPushCheckBox, gbc)

        // 说明
        gbc.gridy = 2
        val noteLabel = JBLabel("<html><font color='gray'>• 合并前 Fetch: 确保获取远程最新代码<br>" +
                "• 合并后 Push: 自动将合并结果推送到远程</font></html>")
        mergePanel.add(noteLabel, gbc)

        // 主面板布局
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(mergePanel, gbc)

        gbc.gridy = 1
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc) // 占位

        return panel
    }

    private fun scanAndAddRepositories() {
        val rootPath = scanRootPathField.text
        if (rootPath.isBlank()) {
            JOptionPane.showMessageDialog(mainPanel, "请先选择扫描根目录", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }

        val maxDepth = scanMaxDepthField.text.toIntOrNull() ?: 3

        // 这里简单实现，实际应该使用后台任务
        val rootFile = java.io.File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            JOptionPane.showMessageDialog(mainPanel, "无效的目录路径", "错误", JOptionPane.ERROR_MESSAGE)
            return
        }

        val repos = mutableListOf<String>()
        scanDirectory(rootFile, repos, 0, maxDepth)

        if (repos.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "未找到 Git 仓库", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        var addedCount = 0
        repos.forEach { path ->
            if (!repositoryListModel.contains(path)) {
                repositoryListModel.addElement(path)
                addedCount++
            }
        }

        JOptionPane.showMessageDialog(
            mainPanel,
            "扫描完成，新增 $addedCount 个仓库（共找到 ${repos.size} 个）",
            "扫描结果",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun scanDirectory(dir: java.io.File, result: MutableList<String>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return

        val gitDir = java.io.File(dir, ".git")
        if (gitDir.exists() && gitDir.isDirectory) {
            result.add(dir.absolutePath)
            return
        }

        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach {
            scanDirectory(it, result, depth + 1, maxDepth)
        }
    }

    override fun isModified(): Boolean {
        val currentPaths = (0 until repositoryListModel.size()).map { repositoryListModel.getElementAt(it) }

        return currentPaths != settings.repositoryPaths ||
                devBranchField.text != settings.state.devBranchName ||
                testBranchField.text != settings.state.testBranchName ||
                mainBranchField.text != settings.state.mainBranchName ||
                tagBaseBranchField.text != settings.state.tagBaseBranch ||
                autoFetchCheckBox.isSelected != settings.state.autoFetchBeforeMerge ||
                autoPushCheckBox.isSelected != settings.state.autoPushAfterMerge ||
                scanRootPathField.text != settings.state.scanRootPath ||
                (scanMaxDepthField.text.toIntOrNull() ?: 3) != settings.state.scanMaxDepth
    }

    override fun apply() {
        // 保存仓库路径
        val paths = (0 until repositoryListModel.size()).map { repositoryListModel.getElementAt(it) }
        settings.repositoryPaths = paths

        // 保存分支配置
        settings.state.devBranchName = devBranchField.text
        settings.state.testBranchName = testBranchField.text
        settings.state.mainBranchName = mainBranchField.text
        settings.state.tagBaseBranch = tagBaseBranchField.text

        // 保存自动化选项
        settings.state.autoFetchBeforeMerge = autoFetchCheckBox.isSelected
        settings.state.autoPushAfterMerge = autoPushCheckBox.isSelected

        // 保存扫描配置
        settings.state.scanRootPath = scanRootPathField.text
        settings.state.scanMaxDepth = scanMaxDepthField.text.toIntOrNull() ?: 3
    }

    override fun reset() {
        // 加载仓库路径
        repositoryListModel.clear()
        settings.repositoryPaths.forEach { repositoryListModel.addElement(it) }

        // 加载分支配置
        devBranchField.text = settings.state.devBranchName
        testBranchField.text = settings.state.testBranchName
        mainBranchField.text = settings.state.mainBranchName
        tagBaseBranchField.text = settings.state.tagBaseBranch

        // 加载自动化选项
        autoFetchCheckBox.isSelected = settings.state.autoFetchBeforeMerge
        autoPushCheckBox.isSelected = settings.state.autoPushAfterMerge

        // 加载扫描配置
        scanRootPathField.text = settings.state.scanRootPath
        scanMaxDepthField.text = settings.state.scanMaxDepth.toString()
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
