# EasyGit API 参考文档

## Git 操作 API

### GitOperations

Git 底层操作封装类。

```kotlin
class GitOperations(private val project: Project)
```

#### Fetch 操作

```kotlin
// Fetch 远程仓库所有分支
fun fetch(repository: GitRepository): GitCommandResult

// Fetch 远程 Tags
fun fetchTags(repository: GitRepository): GitCommandResult
```

#### 分支操作

```kotlin
// 切换分支
fun checkout(repository: GitRepository, branchName: String): GitCommandResult

// 创建并切换到新分支
fun checkoutNewBranch(repository: GitRepository, branchName: String, startPoint: String): GitCommandResult

// 从远端分支创建本地分支（用于本地无目标分支时）
fun checkoutFromRemote(repository: GitRepository, localBranch: String, remoteBranch: String): GitCommandResult

// 获取当前分支名
fun getCurrentBranch(repository: GitRepository): String?

// 获取所有本地分支
fun getLocalBranches(repository: GitRepository): List<String>

// 获取所有远程分支
fun getRemoteBranches(repository: GitRepository): List<String>

// 检查本地分支是否存在
fun localBranchExists(repository: GitRepository, branchName: String): Boolean

// 检查分支是否存在（本地或远程）
fun branchExists(repository: GitRepository, branchName: String): Boolean
```

#### 远端分支操作

```kotlin
// 获取远端分支的最新 commit hash
fun getRemoteBranchCommit(repository: GitRepository, remoteBranch: String): String?
```

#### 合并操作

```kotlin
// 合并分支
fun merge(repository: GitRepository, branchToMerge: String, noFastForward: Boolean = false): GitCommandResult

// 合并分支并返回结构化结果
fun mergeWithResult(repository: GitRepository, branchToMerge: String): MergeResult

// 中止合并
fun abortMerge(repository: GitRepository): GitCommandResult
```

#### 推送操作

```kotlin
// 推送当前分支
fun push(repository: GitRepository): GitCommandResult

// 推送到指定远程分支
fun pushToRemote(repository: GitRepository, remoteBranch: String): GitCommandResult

// 设置上游分支并推送
fun pushSetUpstream(repository: GitRepository, remoteBranch: String): GitCommandResult
```

#### Tag 操作

```kotlin
// 创建 Tag（在当前 HEAD）
fun createTag(repository: GitRepository, tagName: String, message: String? = null): GitCommandResult

// 在指定 commit 上创建 Tag（用于基于远端分支打 Tag）
fun createTagOnCommit(repository: GitRepository, tagName: String, commitHash: String, message: String? = null): GitCommandResult

// 推送 Tag 到远程
fun pushTag(repository: GitRepository, tagName: String): GitCommandResult

// 推送所有 Tags 到远程
fun pushAllTags(repository: GitRepository): GitCommandResult

// 获取所有 Tags（按创建时间降序）
fun getAllTags(repository: GitRepository): List<String>

// 删除本地 Tag
fun deleteTag(repository: GitRepository, tagName: String): GitCommandResult

// 删除远程 Tag
fun deleteRemoteTag(repository: GitRepository, tagName: String): GitCommandResult
```

#### 状态操作

```kotlin
// 获取仓库状态
fun status(repository: GitRepository): GitCommandResult

// 检查是否有未提交的更改（忽略未跟踪文件）
fun hasUncommittedChanges(repository: GitRepository): Boolean

// 刷新仓库状态
fun refreshRepository(repository: GitRepository)
```

---

### MergeExecutor

合并流程执行器，支持基于远端分支的合并。

```kotlin
class MergeExecutor(private val project: Project)
```

#### 方法

```kotlin
// 执行单次合并（支持远端分支格式如 origin/dev）
fun executeMerge(
    repository: GitRepository,
    sourceBranch: String,      // 源分支，如 feature-xxx
    targetBranch: String,      // 目标分支，如 origin/dev 或 dev
    autoPush: Boolean = false,
    fetchFirst: Boolean = true
): BatchOperationResult

// 合并到指定环境
fun mergeToTarget(
    repository: GitRepository,
    sourceBranch: String,
    target: MergeTarget,
    autoPush: Boolean = false
): BatchOperationResult

// 一键合并到所有环境（dev → test → main）
fun oneClickMerge(
    repository: GitRepository,
    sourceBranch: String,
    autoPush: Boolean = false
): List<BatchOperationResult>
```

---

### TagManager

Tag 管理器，支持基于远端分支创建 Tag。

```kotlin
class TagManager(private val project: Project)
```

#### 方法

```kotlin
// 获取最新的 Tag
fun getLatestVersionTag(repository: GitRepository): String?

// 获取前 N 个 Tag
fun getTopTags(repository: GitRepository, count: Int = 20): List<String>

// 智能计算下一个 Tag 名称
fun suggestNextTagName(repository: GitRepository, tagType: TagType): String

// 基于配置的远端分支创建 Tag
fun createTagWithName(
    repository: GitRepository,
    tagName: String,
    description: String = "",
    autoPush: Boolean = false
): TagResult

// 创建 Tag（使用 TagConfig）
fun createTag(
    repository: GitRepository,
    tagConfig: TagConfig,
    autoPush: Boolean = false
): TagResult

// 从 Tag 名称提取基础版本号
fun extractBaseVersion(tagName: String): String?

// 验证版本号格式
fun isValidVersion(version: String): Boolean
```

---

## 配置 API

### EasyGitSettings

全局配置（应用级）。

```kotlin
@Service
class EasyGitSettings : PersistentStateComponent<EasyGitSettings.State> {

    data class State(
        // 仓库配置（可选，用于工具窗口）
        var repositoryPaths: MutableList<String> = mutableListOf(),
        
        // 分支配置
        var devBranchName: String = "dev",
        var testBranchName: String = "test",
        var mainBranchName: String = "main",
        var tagBaseBranch: String = "main",  // 打 Tag 基准分支
        
        // 自动化选项
        var autoFetchBeforeMerge: Boolean = true,
        var autoPushAfterMerge: Boolean = false,
        
        // Tag 版本号提取正则
        var tagVersionPattern: String = "^(R_\\d+\\.\\d+\\.\\d+|v?\\d+\\.\\d+\\.\\d+).*$"
    )

    // 便捷属性
    val tagBaseBranchName: String
        get() = state.tagBaseBranch.ifBlank { "main" }

    companion object {
        fun getInstance(): EasyGitSettings
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
    NORMAL("", "正常版本"),      // R_3.1.7 或 v1.0.0
    PATCH("-P", "临时需求"),     // R_3.1.6-P01
    HOTFIX("-H", "Hotfix")       // R_3.1.6-H01
}
```
