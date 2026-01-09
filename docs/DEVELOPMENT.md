# EasyGit 开发指南

## 开发环境搭建

### 环境要求
- JDK 17+
- IntelliJ IDEA 2023.3+
- Gradle 8.5+（建议使用 Gradle Wrapper）

### 项目导入

1. 打开 IntelliJ IDEA
2. `File → Open` 选择项目目录
3. 选择 "Open as Project"
4. 等待 Gradle 同步完成

### 运行和调试

```bash
# 运行插件（启动新的 IDEA 实例）
Gradle → Tasks → intellij platform → runIde

# 构建插件包
Gradle → Tasks → intellij platform → buildPlugin
# 输出：build/distributions/EasyGit-1.0.0.zip
```

---

## 代码规范

### 包结构

```
com.github.easygit
├── actions       # Action 类，处理用户交互
├── git           # Git 操作封装
├── model         # 数据模型
├── service       # 业务服务
├── settings      # 配置持久化
└── ui            # UI 组件
```

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Action 类 | `XxxAction` | `MergeToDevAction` |
| Dialog 类 | `XxxDialog` | `MergeDialog` |
| Service 类 | `XxxService` | `RepositoryService` |
| 配置类 | `EasyGitXxxSettings` | `EasyGitSettings` |
| 枚举 | 大写下划线 | `MERGE_TO_DEV` |

### 代码风格

- 使用 Kotlin 语言
- 遵循 Kotlin 官方代码规范
- 使用 `data class` 定义数据模型
- 使用 `sealed class` 定义有限状态
- 使用 `object` 定义单例服务

---

## 添加新功能

### 场景一：添加新的合并目标

例如：添加 UAT 环境

**Step 1: 修改枚举**

`model/MergeConfig.kt`:
```kotlin
enum class MergeTarget(val branchName: String, val displayName: String) {
    DEV("dev", "开发环境"),
    TEST("test", "测试环境"),
    UAT("uat", "UAT环境"),  // 新增
    MAIN("main", "生产环境")
}
```

**Step 2: 添加配置项**

`settings/EasyGitSettings.kt`:
```kotlin
data class State(
    // ... 现有配置
    var uatBranchName: String = "uat",  // 新增
)
```

**Step 3: 更新设置页面**

`settings/EasyGitConfigurable.kt`:
```kotlin
// 在 createBranchPanel() 中添加 UAT 分支输入框
```

**Step 4: 创建 Action 类**

`actions/MergeActions.kt`:
```kotlin
class MergeToUatAction : BaseMergeAction(MergeTarget.UAT)
```

**Step 5: 创建项目视图 Action**

`actions/ProjectViewActions.kt`:
```kotlin
class ProjectViewMergeToUatAction : ProjectViewMergeAction(MergeTarget.UAT)
```

**Step 6: 注册 Action**

`plugin.xml`:
```xml
<!-- 主菜单 -->
<action id="EasyGit.MergeToUat"
        class="com.github.easygit.actions.MergeToUatAction"
        text="合并到 UAT"
        description="将当前分支合并到 UAT 分支"/>

<!-- 项目视图右键菜单 -->
<action id="EasyGit.ProjectView.MergeToUat"
        class="com.github.easygit.actions.ProjectViewMergeToUatAction"
        text="合并到 UAT"
        icon="AllIcons.Vcs.Merge"/>
```

---

### 场景二：添加新的 Tag 类型

例如：添加 Release Tag（格式：v1.0.0-R01）

**Step 1: 修改枚举**

`model/TagConfig.kt`:
```kotlin
enum class TagType(val prefix: String, val displayName: String) {
    NORMAL("", "正常版本"),
    PATCH("-P", "临时需求"),
    HOTFIX("-H", "Hotfix"),
    RELEASE("-R", "Release")  // 新增
}
```

**Step 2: 创建快捷 Action（可选）**

`actions/ProjectViewActions.kt`:
```kotlin
class ProjectViewCreateReleaseTagAction : ProjectViewQuickTagAction(TagType.RELEASE)
```

**Step 3: 注册 Action**

`plugin.xml`:
```xml
<action id="EasyGit.ProjectView.CreateReleaseTag"
        class="com.github.easygit.actions.ProjectViewCreateReleaseTagAction"
        text="打 Release Tag"
        description="创建 Release Tag (v1.0.0-R01)"/>
```

**Step 4: 更新 TagDialog UI（可选）**

在 TagDialog 的类型选择下拉框中会自动包含新类型。

---

### 场景三：添加新的右键菜单项

例如：添加"查看分支历史"功能

**Step 1: 创建 Action 类**

```kotlin
// actions/ProjectViewActions.kt
class ProjectViewShowBranchHistoryAction : ProjectViewBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = getRepositoryFromContext(e) ?: return

        // 实现逻辑
        val currentBranch = repository.currentBranch?.name ?: return
        // 打开历史面板或对话框
    }
}
```

**Step 2: 注册 Action**

```xml
<!-- plugin.xml -->
<action id="EasyGit.ProjectView.ShowBranchHistory"
        class="com.github.easygit.actions.ProjectViewShowBranchHistoryAction"
        text="查看分支历史"
        description="查看当前分支的提交历史"
        icon="AllIcons.Vcs.History"/>
```

---

### 场景四：添加新的 Git 操作

例如：添加 Stash 操作

**Step 1: 在 GitOperations 中添加方法**

```kotlin
// git/GitOperations.kt
fun stash(repository: GitRepository, message: String? = null): GitCommandResult {
    val handler = GitLineHandler(project, repository.root, GitCommand.STASH)
    if (message != null) {
        handler.addParameters("save", message)
    }
    return git.runCommand(handler)
}

fun stashPop(repository: GitRepository): GitCommandResult {
    val handler = GitLineHandler(project, repository.root, GitCommand.STASH)
    handler.addParameters("pop")
    return git.runCommand(handler)
}
```

**Step 2: 在需要的地方调用**

```kotlin
// 在合并前自动 stash
if (gitOps.hasUncommittedChanges(repository)) {
    val stashResult = gitOps.stash(repository, "Auto stash before merge")
    // ... 执行合并
    gitOps.stashPop(repository)
}
```

---

## 对话框开发

### 创建新对话框

```kotlin
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import javax.swing.JPanel

class MyDialog(project: Project) : DialogWrapper(project) {

    init {
        title = "对话框标题"
        init()  // 必须调用
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        // 构建 UI
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        // 输入验证
        if (someField.text.isBlank()) {
            return ValidationInfo("请输入内容", someField)
        }
        return null
    }

    override fun doOKAction() {
        // 获取用户输入
        // 执行操作
        super.doOKAction()
    }
}

// 使用
val dialog = MyDialog(project)
if (dialog.showAndGet()) {
    // 用户点击了 OK
}
```

### 使用 Kotlin UI DSL

```kotlin
import com.intellij.ui.dsl.builder.*

override fun createCenterPanel(): JComponent {
    return panel {
        row("用户名:") {
            textField()
                .bindText(::username)
                .columns(COLUMNS_MEDIUM)
        }
        row("密码:") {
            passwordField()
                .bindText(::password)
        }
        row {
            checkBox("记住密码")
                .bindSelected(::rememberPassword)
        }
    }
}
```

---

## 后台任务

### 使用 ProgressManager

```kotlin
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

ProgressManager.getInstance().run(object : Task.Backgroundable(project, "任务标题", true) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        items.forEachIndexed { index, item ->
            indicator.checkCanceled()  // 检查是否取消
            indicator.fraction = index.toDouble() / items.size
            indicator.text = "正在处理: ${item.name}"
            indicator.text2 = "详细信息"

            // 执行操作
            processItem(item)
        }
    }

    override fun onSuccess() {
        // 任务成功完成
    }

    override fun onThrowable(error: Throwable) {
        // 任务异常
    }
})
```

### 在 UI 线程执行

```kotlin
import com.intellij.openapi.application.ApplicationManager

// 切换到 UI 线程
ApplicationManager.getApplication().invokeLater {
    // UI 操作
    dialog.show()
}

// 读操作
ApplicationManager.getApplication().runReadAction {
    // 读取文件等
}

// 写操作
ApplicationManager.getApplication().runWriteAction {
    // 修改文件等
}
```

---

## 持久化配置

### 应用级配置

```kotlin
@State(
    name = "MySettings",
    storages = [Storage("MySettings.xml")]
)
@Service
class MySettings : PersistentStateComponent<MySettings.State> {

    data class State(
        var option1: String = "default",
        var option2: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): MySettings =
            ApplicationManager.getApplication().getService(MySettings::class.java)
    }
}
```

### 项目级配置

```kotlin
@State(
    name = "MyProjectSettings",
    storages = [Storage("myPlugin.xml")]
)
@Service(Service.Level.PROJECT)
class MyProjectSettings : PersistentStateComponent<MyProjectSettings.State> {
    // ...

    companion object {
        fun getInstance(project: Project): MyProjectSettings =
            project.getService(MyProjectSettings::class.java)
    }
}
```

---

## 通知

### 使用 Notification

```kotlin
import com.intellij.notification.*

// 需要先在 plugin.xml 中注册通知组
// <notificationGroup id="EasyGit.Notifications" displayType="BALLOON"/>

val group = NotificationGroupManager.getInstance()
    .getNotificationGroup("EasyGit.Notifications")

// 显示通知
group.createNotification("标题", "内容", NotificationType.INFORMATION)
    .notify(project)

// 带动作的通知
group.createNotification("标题", "内容", NotificationType.WARNING)
    .addAction(NotificationAction.createSimple("查看详情") {
        // 处理点击
    })
    .notify(project)
```

---

## 测试

### 单元测试

```kotlin
import org.junit.Test
import org.junit.Assert.*

class TagManagerTest {

    @Test
    fun testIsValidVersion() {
        val manager = TagManager(mockProject)
        assertTrue(manager.isValidVersion("v1.0.0"))
        assertTrue(manager.isValidVersion("1.0.0"))
        assertFalse(manager.isValidVersion("v1.0"))
        assertFalse(manager.isValidVersion("abc"))
    }

    @Test
    fun testFormatVersion() {
        val manager = TagManager(mockProject)
        assertEquals("v1.0.0", manager.formatVersion("1.0.0"))
        assertEquals("v1.0.0", manager.formatVersion("v1.0.0"))
    }
}
```

### 集成测试

使用 `LightPlatformTestCase` 或 `BasePlatformTestCase` 进行集成测试。

---

## 调试技巧

### 日志输出

```kotlin
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(MyClass::class.java)

LOG.info("信息日志")
LOG.warn("警告日志")
LOG.error("错误日志", exception)
LOG.debug("调试日志")  // 需要开启 Debug 模式
```

### 开启 Debug 日志

1. `Help → Diagnostic Tools → Debug Log Settings`
2. 添加 `#com.github.easygit`
3. 重启 IDE

### 查看日志

- `Help → Show Log in Finder/Explorer`
- 或查看 `~/Library/Logs/JetBrains/<IDE>/idea.log`

---

## 发布

### 构建发布包

```bash
# 构建
gradle buildPlugin

# 输出
build/distributions/EasyGit-1.0.0.zip
```

### 更新版本号

`gradle.properties`:
```properties
pluginVersion=1.0.1
```

### 发布到 Marketplace

1. 注册 JetBrains 账号
2. 申请 Plugin Upload Token
3. 配置环境变量 `PUBLISH_TOKEN`
4. 运行 `gradle publishPlugin`

---

## 常见问题

### Q: Action 不显示在菜单中？
A: 检查 `plugin.xml` 中的 Action 注册和 `add-to-group` 配置。

### Q: 右键菜单不显示？
A: 检查 Action 的 `update()` 方法，确保 `e.presentation.isEnabledAndVisible = true`。

### Q: Git 操作失败？
A: 检查 `Git4Idea` 依赖是否正确配置，确保 Git 仓库有效。

### Q: 配置不保存？
A: 确保 Service 正确注册在 `plugin.xml` 中，并且 State 类是 data class。
