# EasyGit API 参考文档

## Git 操作 API

### GitOperations

Git 底层操作封装类。

#### 构造函数
```kotlin
class GitOperations(private val project: Project)
```

#### Fetch/Pull 操作

```kotlin
/**
 * Fetch 远程仓库所有分支
 * @param repository Git 仓库对象
 * @return GitCommandResult 命令执行结果
 */
fun fetch(repository: GitRepository): GitCommandResult

/**
 * Pull 远程分支
 * @param repository Git 仓库对象
 * @param remoteBranch 远程分支名
 * @return GitCommandResult 命令执行结果
 */
fun pull(repository: GitRepository, remoteBranch: String): GitCommandResult
```

#### 分支操作

```kotlin
/**
 * 切换分支
 * @param repository Git 仓库对象
 * @param branchName 分支名
 * @return GitCommandResult 命令执行结果
 */
fun checkout(repository: GitRepository, branchName: String): GitCommandResult

/**
 * 创建并切换到新分支
 * @param repository Git 仓库对象
 * @param branchName 新分支名
 * @param startPoint 起始点（分支或提交）
 * @return GitCommandResult 命令执行结果
 */
fun checkoutNewBranch(repository: GitRepository, branchName: String, startPoint: String): GitCommandResult

/**
 * 获取当前分支名
 * @param repository Git 仓库对象
 * @return 分支名，无法获取时返回 null
 */
fun getCurrentBranch(repository: GitRepository): String?

/**
 * 获取所有本地分支
 * @param repository Git 仓库对象
 * @return 分支名列表
 */
fun getLocalBranches(repository: GitRepository): List<String>

/**
 * 获取所有远程分支
 * @param repository Git 仓库对象
 * @return 分支名列表
 */
fun getRemoteBranches(repository: GitRepository): List<String>

/**
 * 检查分支是否存在（本地或远程）
 * @param repository Git 仓库对象
 * @param branchName 分支名
 * @return 是否存在
 */
fun branchExists(repository: GitRepository, branchName: String): Boolean
```

#### 合并操作

```kotlin
/**
 * 合并分支
 * @param repository Git 仓库对象
 * @param branchToMerge 要合并的分支名
 * @param noFastForward 是否禁用 fast-forward
 * @return GitCommandResult 命令执行结果
 */
fun merge(repository: GitRepository, branchToMerge: String, noFastForward: Boolean = false): GitCommandResult

/**
 * 合并分支并返回结构化结果
 * @param repository Git 仓库对象
 * @param branchToMerge 要合并的分支名
 * @return MergeResult 合并结果（Success/Conflict/Error）
 */
fun mergeWithResult(repository: GitRepository, branchToMerge: String): MergeResult

/**
 * 中止合并
 * @param repository Git 仓库对象
 * @return GitCommandResult 命令执行结果
 */
fun abortMerge(repository: GitRepository): GitCommandResult
```

#### 推送操作

```kotlin
/**
 * 推送当前分支
 * @param repository Git 仓库对象
 * @return GitCommandResult 命令执行结果
 */
fun push(repository: GitRepository): GitCommandResult

/**
 * 推送到指定远程分支
 * @param repository Git 仓库对象
 * @param remoteBranch 远程分支名
 * @return GitCommandResult 命令执行结果
 */
fun pushToRemote(repository: GitRepository, remoteBranch: String): GitCommandResult

/**
 * 设置上游分支并推送
 * @param repository Git 仓库对象
 * @param remoteBranch 远程分支名
 * @return GitCommandResult 命令执行结果
 */
fun pushSetUpstream(repository: GitRepository, remoteBranch: String): GitCommandResult
```

#### Tag 操作

```kotlin
/**
 * 创建 Tag
 * @param repository Git 仓库对象
 * @param tagName Tag 名称
 * @param message Tag 描述（可选，不为空则创建 annotated tag）
 * @return GitCommandResult 命令执行结果
 */
fun createTag(repository: GitRepository, tagName: String, message: String? = null): GitCommandResult

/**
 * 推送 Tag 到远程
 * @param repository Git 仓库对象
 * @param tagName Tag 名称
 * @return GitCommandResult 命令执行结果
 */
fun pushTag(repository: GitRepository, tagName: String): GitCommandResult

/**
 * 推送所有 Tags 到远程
 * @param repository Git 仓库对象
 * @return GitCommandResult 命令执行结果
 */
fun pushAllTags(repository: GitRepository): GitCommandResult

/**
 * 获取所有 Tags（按版本号降序排列）
 * @param repository Git 仓库对象
 * @return Tag 名称列表
 */
fun getAllTags(repository: GitRepository): List<String>

/**
 * 删除本地 Tag
 * @param repository Git 仓库对象
 * @param tagName Tag 名称
 * @return GitCommandResult 命令执行结果
 */
fun deleteTag(repository: GitRepository, tagName: String): GitCommandResult

/**
 * 删除远程 Tag
 * @param repository Git 仓库对象
 * @param tagName Tag 名称
 * @return GitCommandResult 命令执行结果
 */
fun deleteRemoteTag(repository: GitRepository, tagName: String): GitCommandResult
```

#### 状态操作

```kotlin
/**
 * 获取仓库状态
 * @param repository Git 仓库对象
 * @return GitCommandResult 命令执行结果
 */
fun status(repository: GitRepository): GitCommandResult

/**
 * 检查是否有未提交的更改
 * @param repository Git 仓库对象
 * @return 是否有未提交更改
 */
fun hasUncommittedChanges(repository: GitRepository): Boolean

/**
 * 刷新仓库状态
 * @param repository Git 仓库对象
 */
fun refreshRepository(repository: GitRepository)
```

---

### MergeExecutor

合并流程执行器。

#### 构造函数
```kotlin
class MergeExecutor(private val project: Project)
```

#### 方法

```kotlin
/**
 * 执行单次合并
 * @param repository Git 仓库对象
 * @param sourceBranch 源分支（功能分支）
 * @param targetBranch 目标分支（dev/test/main）
 * @param autoPush 是否自动推送
 * @param fetchFirst 是否先 fetch
 * @return BatchOperationResult 操作结果
 */
fun executeMerge(
    repository: GitRepository,
    sourceBranch: String,
    targetBranch: String,
    autoPush: Boolean = false,
    fetchFirst: Boolean = true
): BatchOperationResult

/**
 * 合并到指定环境
 * @param repository Git 仓库对象
 * @param sourceBranch 源分支
 * @param target 目标环境
 * @param autoPush 是否自动推送
 * @return BatchOperationResult 操作结果
 */
fun mergeToTarget(
    repository: GitRepository,
    sourceBranch: String,
    target: MergeTarget,
    autoPush: Boolean = false
): BatchOperationResult

/**
 * 一键合并到所有环境（dev → test → main）
 * @param repository Git 仓库对象
 * @param sourceBranch 源分支
 * @param autoPush 是否自动推送
 * @return 每个环境的操作结果列表
 */
fun oneClickMerge(
    repository: GitRepository,
    sourceBranch: String,
    autoPush: Boolean = false
): List<BatchOperationResult>

/**
 * 批量合并多个仓库
 * @param repositories 仓库列表
 * @param sourceBranch 源分支
 * @param targetBranch 目标分支
 * @param autoPush 是否自动推送
 * @return 操作结果列表
 */
fun batchMerge(
    repositories: List<GitRepository>,
    sourceBranch: String,
    targetBranch: String,
    autoPush: Boolean = false
): List<BatchOperationResult>

/**
 * 切换回功能分支
 * @param repository Git 仓库对象
 * @param sourceBranch 源分支名
 * @return 是否成功
 */
fun switchBackToSourceBranch(repository: GitRepository, sourceBranch: String): Boolean
```

---

### TagManager

Tag 管理器。

#### 构造函数
```kotlin
class TagManager(private val project: Project)
```

#### 方法

```kotlin
/**
 * 获取最新的正常版本 Tag
 * @param repository Git 仓库对象
 * @return Tag 名称，如 "v1.0.0"，无则返回 null
 */
fun getLatestVersionTag(repository: GitRepository): String?

/**
 * 获取指定基础版本的下一个序号
 * @param repository Git 仓库对象
 * @param baseVersion 基础版本，如 "v1.0.0"
 * @param tagType Tag 类型
 * @return 下一个序号
 *
 * 示例：
 * 现有 Tags: [v1.0.0-P01, v1.0.0-P02]
 * getNextSequence(repo, "v1.0.0", PATCH) → 3
 */
fun getNextSequence(repository: GitRepository, baseVersion: String, tagType: TagType): Int

/**
 * 生成下一个 Tag 名称
 * @param repository Git 仓库对象
 * @param baseVersion 基础版本
 * @param tagType Tag 类型
 * @return 完整 Tag 名称
 *
 * 示例：
 * generateNextTagName(repo, "v1.0.0", PATCH) → "v1.0.0-P03"
 */
fun generateNextTagName(repository: GitRepository, baseVersion: String, tagType: TagType): String

/**
 * 创建 Tag
 * @param repository Git 仓库对象
 * @param tagConfig Tag 配置
 * @param autoPush 是否自动推送
 * @return TagResult 创建结果
 */
fun createTag(
    repository: GitRepository,
    tagConfig: TagConfig,
    autoPush: Boolean = false
): TagResult

/**
 * 批量创建 Tag
 * @param repositories 仓库列表
 * @param baseVersion 基础版本
 * @param tagType Tag 类型
 * @param description 描述
 * @param autoPush 是否自动推送
 * @return TagResult 列表
 */
fun createTagsForRepositories(
    repositories: List<GitRepository>,
    baseVersion: String,
    tagType: TagType,
    description: String = "",
    autoPush: Boolean = false
): List<TagResult>

/**
 * 从 Tag 名称提取基础版本号
 * @param tagName Tag 名称
 * @return 基础版本号
 *
 * 示例：
 * extractBaseVersion("v1.0.0-P01") → "v1.0.0"
 */
fun extractBaseVersion(tagName: String): String?

/**
 * 获取推荐的下一个版本号
 * @param repository Git 仓库对象
 * @return 推荐版本号
 *
 * 示例：
 * 最新 Tag: v1.0.0
 * suggestNextVersion(repo) → "v1.0.1"
 */
fun suggestNextVersion(repository: GitRepository): String

/**
 * 验证版本号格式
 * @param version 版本号
 * @return 是否有效
 *
 * 有效格式：v1.0.0, 1.0.0
 */
fun isValidVersion(version: String): Boolean

/**
 * 格式化版本号（确保以 v 开头）
 * @param version 版本号
 * @return 格式化后的版本号
 *
 * 示例：
 * formatVersion("1.0.0") → "v1.0.0"
 * formatVersion("v1.0.0") → "v1.0.0"
 */
fun formatVersion(version: String): String
```

---

## 服务 API

### RepositoryService

仓库管理服务。

```kotlin
class RepositoryService(private val project: Project) {

    /**
     * 获取当前项目的所有 Git 仓库
     */
    fun getProjectRepositories(): List<GitRepository>

    /**
     * 根据路径获取 Git 仓库
     */
    fun getRepository(path: String): GitRepository?

    /**
     * 从路径列表加载仓库
     */
    fun loadRepositories(paths: List<String>): List<GitRepository>

    /**
     * 扫描目录下的所有 Git 仓库
     * @param rootPath 根目录路径
     * @param maxDepth 最大扫描深度
     */
    fun scanRepositories(rootPath: String, maxDepth: Int = 3): List<VirtualFile>

    /**
     * 检查路径是否是有效的 Git 仓库
     */
    fun isValidGitRepository(path: String): Boolean

    /**
     * 刷新所有仓库状态
     */
    fun refreshAllRepositories()
}
```

### HistoryService

操作历史服务。

```kotlin
@Service
class HistoryService : PersistentStateComponent<OperationHistoryState> {

    /**
     * 添加历史记录
     */
    fun addHistory(history: OperationHistory)

    /**
     * 获取所有历史记录
     */
    fun getAllHistories(): List<OperationHistory>

    /**
     * 获取指定仓库的历史记录
     */
    fun getHistoriesByRepository(repositoryPath: String): List<OperationHistory>

    /**
     * 获取最近的历史记录
     * @param limit 数量限制，默认 50
     */
    fun getRecentHistories(limit: Int = 50): List<OperationHistory>

    /**
     * 清空历史记录
     */
    fun clearHistory()

    /**
     * 删除指定的历史记录
     */
    fun deleteHistory(id: Long)

    companion object {
        fun getInstance(): HistoryService
    }
}
```

### NotificationService

通知服务（单例对象）。

```kotlin
object NotificationService {

    /**
     * 显示信息通知
     */
    fun info(project: Project?, title: String, content: String)

    /**
     * 显示警告通知
     */
    fun warning(project: Project?, title: String, content: String)

    /**
     * 显示错误通知
     */
    fun error(project: Project?, title: String, content: String)

    /**
     * 显示合并结果通知
     */
    fun showMergeResult(project: Project?, successCount: Int, failCount: Int, details: String = "")

    /**
     * 显示 Tag 创建结果
     * @param tagResults 仓库名 -> Tag 名 的列表
     */
    fun showTagResult(project: Project?, tagResults: List<Pair<String, String>>)

    /**
     * 显示冲突警告
     */
    fun showConflictWarning(project: Project?, repositoryName: String, conflictFiles: List<String>)
}
```

---

## 配置 API

### EasyGitSettings

全局配置（应用级）。

```kotlin
@Service
class EasyGitSettings : PersistentStateComponent<EasyGitSettings.State> {

    data class State(
        var repositoryPaths: MutableList<String> = mutableListOf(),
        var devBranchName: String = "dev",
        var testBranchName: String = "test",
        var mainBranchName: String = "main",
        var featureBranchPrefix: String = "",
        var autoFetchBeforeMerge: Boolean = true,
        var autoPushAfterMerge: Boolean = false,
        var tagPrefix: String = "v",
        var scanRootPath: String = "",
        var scanMaxDepth: Int = 3
    )

    // 便捷属性访问
    var repositoryPaths: List<String>
    var devBranchName: String
    var testBranchName: String
    var mainBranchName: String
    var autoFetchBeforeMerge: Boolean
    var autoPushAfterMerge: Boolean

    // 辅助方法
    fun addRepositoryPath(path: String)
    fun removeRepositoryPath(path: String)
    fun clearRepositoryPaths()
    fun getTargetBranchName(target: String): String

    companion object {
        fun getInstance(): EasyGitSettings
    }
}
```

### EasyGitProjectSettings

项目级配置。

```kotlin
@Service(Service.Level.PROJECT)
class EasyGitProjectSettings : PersistentStateComponent<EasyGitProjectSettings.State> {

    data class State(
        var selectedRepositoryPaths: MutableList<String> = mutableListOf(),
        var lastSourceBranch: String = "",
        var lastTargetBranch: String = "",
        var lastTagVersion: String = "",
        var useProjectSettings: Boolean = false,
        var projectDevBranch: String = "dev",
        var projectTestBranch: String = "test",
        var projectMainBranch: String = "main"
    )

    // 选中仓库管理
    fun getSelectedRepositoryPaths(): List<String>
    fun setSelectedRepositoryPaths(paths: List<String>)
    fun addSelectedRepository(path: String)
    fun removeSelectedRepository(path: String)
    fun isRepositorySelected(path: String): Boolean

    // 上次使用的配置
    fun saveLastConfig(sourceBranch: String, targetBranch: String)
    fun getLastSourceBranch(): String
    fun getLastTargetBranch(): String
    fun saveLastTagVersion(version: String)
    fun getLastTagVersion(): String

    // 分支配置（支持项目级覆盖）
    fun getDevBranch(): String
    fun getTestBranch(): String
    fun getMainBranch(): String

    companion object {
        fun getInstance(project: Project): EasyGitProjectSettings
    }
}
```

---

## 数据模型

### MergeResult

```kotlin
sealed class MergeResult {
    data class Success(val message: String = "合并成功") : MergeResult()
    data class Conflict(val conflictFiles: List<String>) : MergeResult()
    data class Error(val message: String) : MergeResult()

    fun isSuccess(): Boolean = this is Success
}
```

### BatchOperationResult

```kotlin
data class BatchOperationResult(
    val repositoryName: String,
    val repositoryPath: String,
    val success: Boolean,
    val message: String,
    val tagName: String? = null
)
```

### TagResult

```kotlin
data class TagResult(
    val repositoryName: String,
    val tagName: String,
    val success: Boolean,
    val message: String
)
```

### OperationHistory

```kotlin
data class OperationHistory(
    val id: Long = System.currentTimeMillis(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val operationType: OperationType,
    val repositoryName: String,
    val repositoryPath: String,
    val sourceBranch: String,
    val targetBranch: String = "",
    val tagName: String = "",
    val success: Boolean,
    val message: String
) {
    fun getFormattedTime(): String
    fun getDisplayText(): String
}
```

---

## 枚举类型

### MergeTarget

```kotlin
enum class MergeTarget(val branchName: String, val displayName: String) {
    DEV("dev", "开发环境"),
    TEST("test", "测试环境"),
    MAIN("main", "生产环境")
}
```

### TagType

```kotlin
enum class TagType(val prefix: String, val displayName: String) {
    NORMAL("", "正常版本"),      // v1.0.0
    PATCH("-P", "临时需求"),     // v1.0.0-P01
    HOTFIX("-H", "Hotfix")       // v1.0.0-H01
}
```

### OperationType

```kotlin
enum class OperationType(val displayName: String) {
    MERGE_TO_DEV("合并到 Dev"),
    MERGE_TO_TEST("合并到 Test"),
    MERGE_TO_MAIN("合并到 Main"),
    ONE_CLICK_MERGE("一键合并"),
    CREATE_TAG("打 Tag")
}
```
