# EasyGit 技术架构文档

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                │
├─────────────────────────────────────────────────────────────────┤
│  Actions          │  Dialogs           │  Tool Window           │
│  - MergeActions   │  - MergeDialog     │  - EasyGitToolWindow   │
│  - TagActions     │  - TagDialog       │                        │
│  - ProjectView    │  - ResultDialog    │                        │
│    Actions        │  - HistoryDialog   │                        │
└────────┬──────────┴────────┬───────────┴────────┬───────────────┘
         │                   │                    │
         ▼                   ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Service Layer                             │
├─────────────────────────────────────────────────────────────────┤
│  RepositoryService     │  HistoryService    │  NotificationSvc  │
│  - 仓库扫描和管理       │  - 历史记录持久化   │  - 消息通知       │
└────────┬───────────────┴────────┬───────────┴────────┬──────────┘
         │                        │                    │
         ▼                        ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Git Layer                               │
├─────────────────────────────────────────────────────────────────┤
│  GitOperations         │  MergeExecutor      │  TagManager      │
│  - fetch/pull          │  - 合并流程控制      │  - Tag 序号计算  │
│  - checkout            │  - 冲突检测          │  - Tag 创建      │
│  - merge               │  - 历史记录          │                  │
│  - push                │                      │                  │
│  - tag                 │                      │                  │
└────────┬───────────────┴────────┬────────────┴────────┬─────────┘
         │                        │                     │
         ▼                        ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    IntelliJ Platform API                        │
├─────────────────────────────────────────────────────────────────┤
│  Git4Idea              │  VirtualFileSystem  │  PersistentState │
│  - GitRepository       │  - VirtualFile      │  - Settings      │
│  - GitCommand          │                     │  - History       │
│  - GitLineHandler      │                     │                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 模块详解

### 1. Actions 模块

负责处理用户交互，是插件的入口点。

#### 文件：`MergeActions.kt`
```kotlin
// 基础合并 Action
abstract class BaseMergeAction(private val target: MergeTarget) : AnAction()

// 具体实现
class MergeToDevAction : BaseMergeAction(MergeTarget.DEV)
class MergeToTestAction : BaseMergeAction(MergeTarget.TEST)
class MergeToMainAction : BaseMergeAction(MergeTarget.MAIN)
```

**流程**：
1. 获取配置的仓库列表
2. 显示 MergeDialog 让用户选择
3. 调用 MergeExecutor 执行合并
4. 显示 ResultDialog 展示结果

#### 文件：`ProjectViewActions.kt`
```kotlin
// 项目视图右键菜单 Action 基类
abstract class ProjectViewBaseAction : AnAction() {
    // 从右键上下文获取 Git 仓库
    protected fun getRepositoryFromContext(e: AnActionEvent): GitRepository?
    protected fun getRepositoriesFromContext(e: AnActionEvent): List<GitRepository>
}
```

**特点**：
- 自动检测右键选中的目录是否为 Git 仓库
- 支持多选批量操作
- 菜单仅在 Git 仓库目录上显示

---

### 2. Git 模块

封装所有 Git 操作。

#### 文件：`GitOperations.kt`

底层 Git 命令封装：

```kotlin
class GitOperations(private val project: Project) {
    private val git: Git = Git.getInstance()

    // Fetch/Pull
    fun fetch(repository: GitRepository): GitCommandResult
    fun pull(repository: GitRepository, remoteBranch: String): GitCommandResult

    // 分支操作
    fun checkout(repository: GitRepository, branchName: String): GitCommandResult
    fun getCurrentBranch(repository: GitRepository): String?
    fun branchExists(repository: GitRepository, branchName: String): Boolean

    // 合并操作
    fun merge(repository: GitRepository, branchToMerge: String): GitCommandResult
    fun mergeWithResult(repository: GitRepository, branchToMerge: String): MergeResult
    fun abortMerge(repository: GitRepository): GitCommandResult

    // 推送操作
    fun push(repository: GitRepository): GitCommandResult
    fun pushToRemote(repository: GitRepository, remoteBranch: String): GitCommandResult

    // Tag 操作
    fun createTag(repository: GitRepository, tagName: String, message: String?): GitCommandResult
    fun pushTag(repository: GitRepository, tagName: String): GitCommandResult
    fun getAllTags(repository: GitRepository): List<String>

    // 状态
    fun hasUncommittedChanges(repository: GitRepository): Boolean
}
```

#### 文件：`MergeExecutor.kt`

合并流程控制：

```kotlin
class MergeExecutor(private val project: Project) {

    fun executeMerge(
        repository: GitRepository,
        sourceBranch: String,
        targetBranch: String,
        autoPush: Boolean,
        fetchFirst: Boolean
    ): BatchOperationResult {
        // 1. 检查未提交更改
        // 2. Fetch 最新代码
        // 3. 切换到目标分支
        // 4. Pull 目标分支
        // 5. 执行合并
        // 6. Push（如果配置）
        // 7. 记录历史
    }

    fun oneClickMerge(
        repository: GitRepository,
        sourceBranch: String,
        autoPush: Boolean
    ): List<BatchOperationResult> {
        // 依次合并到 dev → test → main
    }
}
```

#### 文件：`TagManager.kt`

Tag 智能管理：

```kotlin
class TagManager(private val project: Project) {

    // 获取最新正常版本 Tag
    fun getLatestVersionTag(repository: GitRepository): String?

    // 计算下一个序号
    fun getNextSequence(repository: GitRepository, baseVersion: String, tagType: TagType): Int {
        // 解析现有 Tag，找出最大序号并 +1
        // v1.0.0-P01, v1.0.0-P02 → 返回 3
    }

    // 生成下一个 Tag 名称
    fun generateNextTagName(repository: GitRepository, baseVersion: String, tagType: TagType): String

    // 创建 Tag
    fun createTag(repository: GitRepository, tagConfig: TagConfig, autoPush: Boolean): TagResult

    // 版本号验证和格式化
    fun isValidVersion(version: String): Boolean
    fun formatVersion(version: String): String  // 确保以 v 开头
}
```

**Tag 序号计算逻辑**：
```
输入: baseVersion = "v1.0.0", tagType = PATCH
现有 Tags: ["v1.0.0", "v1.0.0-P01", "v1.0.0-P02", "v1.0.0-H01"]
匹配模式: v1.0.0-P(\d+)
匹配结果: [01, 02]
最大序号: 2
返回: 3
生成: v1.0.0-P03
```

---

### 3. Model 模块

数据模型定义。

#### 文件：`MergeConfig.kt`
```kotlin
// 合并配置
data class MergeConfig(
    val sourceBranch: String,
    val targetBranch: String,
    val autoPush: Boolean = false,
    val fetchBeforeMerge: Boolean = true
)

// 合并目标
enum class MergeTarget(val branchName: String, val displayName: String) {
    DEV("dev", "开发环境"),
    TEST("test", "测试环境"),
    MAIN("main", "生产环境")
}

// 合并结果（密封类）
sealed class MergeResult {
    data class Success(val message: String) : MergeResult()
    data class Conflict(val conflictFiles: List<String>) : MergeResult()
    data class Error(val message: String) : MergeResult()
}
```

#### 文件：`TagConfig.kt`
```kotlin
// Tag 类型
enum class TagType(val prefix: String, val displayName: String) {
    NORMAL("", "正常版本"),      // v1.0.0
    PATCH("-P", "临时需求"),     // v1.0.0-P01
    HOTFIX("-H", "Hotfix")       // v1.0.0-H01
}

// Tag 配置
data class TagConfig(
    val baseVersion: String,
    val tagType: TagType,
    val description: String = "",
    val autoPush: Boolean = false
) {
    fun generateTagName(sequence: Int = 1): String
}
```

#### 文件：`OperationHistory.kt`
```kotlin
// 操作类型
enum class OperationType(val displayName: String) {
    MERGE_TO_DEV("合并到 Dev"),
    MERGE_TO_TEST("合并到 Test"),
    MERGE_TO_MAIN("合并到 Main"),
    ONE_CLICK_MERGE("一键合并"),
    CREATE_TAG("打 Tag")
}

// 操作历史
data class OperationHistory(
    val id: Long,
    val timestamp: LocalDateTime,
    val operationType: OperationType,
    val repositoryName: String,
    val repositoryPath: String,
    val sourceBranch: String,
    val targetBranch: String,
    val tagName: String,
    val success: Boolean,
    val message: String
)
```

---

### 4. Service 模块

业务服务层。

#### 文件：`RepositoryService.kt`
```kotlin
class RepositoryService(private val project: Project) {

    // 获取项目仓库
    fun getProjectRepositories(): List<GitRepository>

    // 根据路径加载仓库
    fun loadRepositories(paths: List<String>): List<GitRepository>

    // 扫描目录发现仓库
    fun scanRepositories(rootPath: String, maxDepth: Int = 3): List<VirtualFile>

    // 验证是否为 Git 仓库
    fun isValidGitRepository(path: String): Boolean
}
```

#### 文件：`HistoryService.kt`
```kotlin
@Service
class HistoryService : PersistentStateComponent<OperationHistoryState> {

    fun addHistory(history: OperationHistory)
    fun getAllHistories(): List<OperationHistory>
    fun getRecentHistories(limit: Int): List<OperationHistory>
    fun clearHistory()
}
```

#### 文件：`NotificationService.kt`
```kotlin
object NotificationService {
    fun info(project: Project?, title: String, content: String)
    fun warning(project: Project?, title: String, content: String)
    fun error(project: Project?, title: String, content: String)
    fun showMergeResult(project: Project?, successCount: Int, failCount: Int)
    fun showTagResult(project: Project?, tagResults: List<Pair<String, String>>)
}
```

---

### 5. Settings 模块

配置持久化。

#### 文件：`EasyGitSettings.kt`
```kotlin
@State(name = "EasyGitSettings", storages = [Storage("EasyGitSettings.xml")])
@Service
class EasyGitSettings : PersistentStateComponent<EasyGitSettings.State> {

    data class State(
        var repositoryPaths: MutableList<String>,
        var devBranchName: String = "dev",
        var testBranchName: String = "test",
        var mainBranchName: String = "main",
        var autoFetchBeforeMerge: Boolean = true,
        var autoPushAfterMerge: Boolean = false,
        var tagPrefix: String = "v"
    )
}
```

存储位置：`~/.config/JetBrains/<IDE>/options/EasyGitSettings.xml`

---

### 6. UI 模块

用户界面组件。

#### 文件：`EasyGitToolWindow.kt`
工具窗口面板，包含：
- 仓库列表表格（可勾选）
- 工具栏按钮
- 状态栏

#### 文件：`MergeDialog.kt`
合并配置对话框：
- 仓库选择表格
- 源分支/目标分支下拉框
- 自动 Fetch/Push 选项

#### 文件：`TagDialog.kt`
Tag 创建对话框：
- 仓库选择
- 基础版本号输入
- Tag 类型选择
- 描述输入
- 实时预览

#### 文件：`ResultDialog.kt`
结果展示对话框：
- 统计信息
- 详细结果表格
- 复制功能

---

## 数据流

### 合并操作流程

```
用户右键 → ProjectViewMergeAction
    │
    ▼
获取选中的 Git 仓库
    │
    ▼
显示确认对话框（输入源分支）
    │
    ▼
ProgressManager.run() 启动后台任务
    │
    ▼
MergeExecutor.executeMerge()
    ├── GitOperations.fetch()
    ├── GitOperations.checkout()
    ├── GitOperations.pull()
    ├── GitOperations.merge()
    │       │
    │       ▼
    │   检查合并结果
    │   ├── Success → GitOperations.push() (可选)
    │   └── Conflict → 返回冲突信息
    │
    ▼
HistoryService.addHistory()
    │
    ▼
显示 ResultDialog
    │
    ▼
NotificationService.showMergeResult()
```

### Tag 创建流程

```
用户右键 → ProjectViewCreatePatchTagAction
    │
    ▼
获取选中的 Git 仓库
    │
    ▼
TagManager.suggestNextVersion() 获取建议版本
    │
    ▼
显示输入对话框
    │
    ▼
TagManager.getNextSequence() 计算序号
    │
    ▼
TagManager.createTag()
    ├── GitOperations.createTag()
    └── GitOperations.pushTag() (可选)
    │
    ▼
HistoryService.addHistory()
    │
    ▼
显示 TagResultDialog
```

---

## 扩展点

### 添加新的合并目标

1. **修改枚举**：`MergeTarget.kt`
```kotlin
enum class MergeTarget {
    DEV, TEST, MAIN,
    UAT  // 新增
}
```

2. **添加配置**：`EasyGitSettings.kt`
```kotlin
var uatBranchName: String = "uat"
```

3. **创建 Action**：`MergeActions.kt`
```kotlin
class MergeToUatAction : BaseMergeAction(MergeTarget.UAT)
```

4. **注册 Action**：`plugin.xml`
```xml
<action id="EasyGit.MergeToUat"
        class="...MergeToUatAction"
        text="合并到 UAT"/>
```

### 添加新的 Tag 类型

1. **修改枚举**：`TagConfig.kt`
```kotlin
enum class TagType {
    NORMAL, PATCH, HOTFIX,
    RELEASE("-R", "Release")  // 新增
}
```

2. **创建快捷 Action**：`ProjectViewActions.kt`
```kotlin
class ProjectViewCreateReleaseTagAction : ProjectViewQuickTagAction(TagType.RELEASE)
```

---

## 异常处理

| 场景 | 处理方式 |
|------|---------|
| 未提交更改 | 提示用户先提交或暂存 |
| 分支不存在 | 尝试从远程检出，失败则报错 |
| 合并冲突 | 返回冲突文件列表，中断流程 |
| Push 失败 | 返回部分成功状态 |
| 网络异常 | 捕获并显示错误信息 |
