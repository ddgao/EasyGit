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
│  - fetch/fetchTags     │  - 合并流程控制      │  - Tag 序号计算  │
│  - checkout            │  - 远端分支处理      │  - 基于远端分支  │
│  - checkoutFromRemote  │  - 冲突检测          │    创建 Tag     │
│  - merge               │  - 历史记录          │                  │
│  - push                │                      │                  │
│  - createTagOnCommit   │                      │                  │
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

## 核心设计：基于远端分支

### 合并操作

**传统方式**：基于本地分支合并
**EasyGit 方式**：基于远端分支合并

```
源分支选择：只显示远端分支（origin/feature-xxx）
目标分支：使用远端分支（origin/dev, origin/test, origin/main）

合并流程：
1. fetch 获取最新远端分支
2. 如果本地无目标分支，从远端创建：git checkout -b dev origin/dev
3. 如果本地已有目标分支，切换并 pull 最新
4. 执行合并
5. push 到远端
6. 切回原分支
```

### 打 Tag 操作

**传统方式**：在当前分支上打 Tag
**EasyGit 方式**：基于配置的远端分支 commit 创建 Tag

```
配置项：tagBaseBranch（默认 main）
远端分支：origin/{tagBaseBranch}

打 Tag 流程：
1. 获取远端分支的最新 commit：git rev-parse origin/main
2. 在该 commit 上创建 Tag：git tag -a v1.0.0 <commit-hash> -m "message"
3. 推送 Tag：git push origin v1.0.0
```

**优势**：
- 不受当前工作分支影响
- 确保 Tag 打在正确的发布分支上
- 无需切换分支

---

## 模块详解

### 1. Git 模块

#### GitOperations.kt - 新增方法

```kotlin
// 从远端分支创建本地分支
fun checkoutFromRemote(repository, localBranch, remoteBranch): GitCommandResult

// 获取远端分支的最新 commit hash
fun getRemoteBranchCommit(repository, remoteBranch): String?

// 在指定 commit 上创建 Tag
fun createTagOnCommit(repository, tagName, commitHash, message): GitCommandResult

// Fetch 远端 Tags
fun fetchTags(repository): GitCommandResult
```

#### MergeExecutor.kt - 远端分支处理

```kotlin
fun executeMerge(...) {
    // 检测目标分支是否为远端格式
    val isTargetRemote = targetBranch.startsWith("origin/")
    val localTargetBranch = if (isTargetRemote) 
        targetBranch.removePrefix("origin/") 
    else 
        targetBranch
    
    // 本地分支不存在时从远端创建
    if (!gitOps.localBranchExists(repository, localTargetBranch)) {
        gitOps.checkoutFromRemote(repository, localTargetBranch, remoteTargetBranch)
    }
    
    // 继续合并流程...
}
```

#### TagManager.kt - 基于远端分支创建 Tag

```kotlin
fun createTagWithName(repository, tagName, description, autoPush): TagResult {
    val remoteBranch = "origin/${settings.tagBaseBranchName}"
    
    // 获取远端分支的 commit
    val commitHash = gitOps.getRemoteBranchCommit(repository, remoteBranch)
        ?: return TagResult(error = "获取远端分支 commit 失败")
    
    // 在该 commit 上创建 Tag
    gitOps.createTagOnCommit(repository, tagName, commitHash, description)
    
    if (autoPush) {
        gitOps.pushTag(repository, tagName)
    }
}
```

---

### 2. Settings 模块

#### EasyGitSettings.kt - 新增配置

```kotlin
data class State(
    // 分支配置
    var devBranchName: String = "dev",
    var testBranchName: String = "test",
    var mainBranchName: String = "main",
    var tagBaseBranch: String = "main",  // 打 Tag 基准分支
    
    // 自动化选项
    var autoFetchBeforeMerge: Boolean = true,
    var autoPushAfterMerge: Boolean = false,
    
    // 仓库配置（可选，用于工具窗口）
    var repositoryPaths: MutableList<String> = mutableListOf(),
    
    // Tag 版本号提取正则
    var tagVersionPattern: String = "^(R_\\d+\\.\\d+\\.\\d+|v?\\d+\\.\\d+\\.\\d+).*$"
)

// 便捷属性
val tagBaseBranchName: String
    get() = state.tagBaseBranch.ifBlank { "main" }
```

---

### 3. UI 模块

#### MergeDialog.kt - 只显示远端分支

```kotlin
private fun loadBranches() {
    // 只加载远端分支
    val remoteBranches = gitOps.getRemoteBranches(repository)
    sourceBranchCombo.removeAllItems()
    remoteBranches.forEach { sourceBranchCombo.addItem(it) }
}
```

#### TagDialog.kt - 显示基准分支

```kotlin
// 表格列
private val columnNames = arrayOf(
    "选择", 
    "仓库名称", 
    "基准分支",     // 显示 origin/main（从配置读取）
    "最新 Tag", 
    "新 Tag 名称", 
    "操作"
)

// 基准分支值
private val baseBranch = "origin/${settings.tagBaseBranchName}"
```

---

## 数据流

### 合并操作流程（基于远端分支）

```
用户右键 → ProjectViewMergeAction
    │
    ▼
显示 MergeDialog（只显示远端分支）
    │
    ▼
用户选择：origin/feature-xxx → origin/dev
    │
    ▼
MergeExecutor.executeMerge()
    ├── GitOperations.fetch()           # 获取最新远端
    ├── 检查本地是否有 dev 分支
    │   ├── 有 → checkout dev + pull
    │   └── 无 → checkoutFromRemote(dev, origin/dev)
    ├── GitOperations.merge(feature-xxx)
    ├── GitOperations.push()            # 推送到远端
    └── checkout 回原分支
    │
    ▼
显示 ResultDialog
```

### Tag 创建流程（基于远端分支）

```
用户右键 → ProjectViewCreateTagAction
    │
    ▼
显示 TagDialog
    ├── 加载最新 Tag（本地）
    └── 计算建议的新 Tag 名称
    │
    ▼
用户确认 Tag 名称和描述
    │
    ▼
TagManager.createTagWithName()
    ├── 获取配置的基准分支：origin/main
    ├── gitOps.getRemoteBranchCommit(origin/main)
    ├── gitOps.createTagOnCommit(tagName, commitHash, message)
    └── gitOps.pushTag(tagName)  # 如果开启自动推送
    │
    ▼
显示 TagResultDialog
```

---

## 配置说明

### 分支配置

| 配置项 | 默认值 | 用途 |
|--------|--------|------|
| devBranchName | dev | 合并目标 DEV |
| testBranchName | test | 合并目标 TEST |
| mainBranchName | main | 合并目标 MAIN |
| tagBaseBranch | main | Tag 创建的基准远端分支 |

### 工具窗口仓库来源

```kotlin
fun refreshRepositories() {
    if (settings.repositoryPaths.isNotEmpty()) {
        // 使用配置的仓库路径
        repoService.loadRepositories(settings.repositoryPaths)
    } else {
        // 自动使用 IDEA 检测到的项目仓库
        repoService.getProjectRepositories()
    }
}
```
