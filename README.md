# EasyGit - IntelliJ IDEA Git 分支合并插件

## 项目概述

EasyGit 是一款 IntelliJ IDEA 插件，专为微服务项目开发设计，简化 Git 分支合并和 Tag 管理流程。

### 核心痛点解决

| 痛点 | 解决方案 |
|------|---------|
| 每次需要手动合并功能分支到 dev/test/main | 一键合并到指定环境或所有环境 |
| 多个微服务项目需要重复操作 | 批量选择多个仓库同时操作 |
| 打 Tag 需要手动计算序号 | 智能识别已有 Tag，自动累加序号 |
| 合并操作容易遗漏 | 操作历史记录，方便追溯 |
| 打完 Tag 需要手动记录 | 汇总展示所有创建的 Tag |
| 多仓库打 Tag 版本不同 | 支持为每个仓库设置独立的 Tag 名称 |

---

## 功能特性

### 1. 分支合并
- **分步合并**：功能分支 → dev / test / main
- **一键合并**：功能分支同时合并到 dev、test、main（并行策略）
- 合并前自动 fetch 最新代码
- 冲突检测和提示，中断流程让用户手动解决
- 可配置是否自动 push
- 合并完成后自动切回原分支

### 2. 智能打 Tag
支持三种 Tag 格式：
- **正常版本**：`v1.0.0`
- **临时需求**：`v1.0.0-P01`、`v1.0.0-P02`（自动累加）
- **Hotfix**：`v1.0.0-H01`、`v1.0.0-H02`（自动累加）

**多仓库独立 Tag 名称**：
- 每个仓库可设置不同的 Tag 名称
- 支持批量设置统一 Tag 名称
- 支持重置为自动计算的建议值

### 3. 批量操作
- 支持同时选择多个独立 Git 仓库
- 进度条显示处理状态
- 结果汇总展示

### 4. 操作历史
- 记录每次合并/打 Tag 操作
- 包含时间、仓库、操作类型、结果
- 支持查看和清空历史

### 5. 结果汇总
打 Tag 完成后汇总展示：
```
项目A v1.2.0
项目B v1.2.0-P01
项目C v1.2.0-H02
```

---

## 使用方式

### 方式一：项目目录右键菜单（推荐）

在 Project 面板中，右键点击项目目录：

```
右键项目目录 → EasyGit
    ├── 合并到 Dev
    ├── 合并到 Test
    ├── 合并到 Main
    ├── ─────────────
    ├── 一键合并所有环境
    ├── ─────────────
    └── 打 Tag
```

**支持多选**：按住 Ctrl/Cmd 选中多个项目，右键可批量操作。

### 方式二：VCS 菜单

`VCS → EasyGit` 下有完整功能菜单：
- 合并到 Dev / Test / Main
- 一键合并所有环境
- 打 Tag
- 批量合并
- 打开 EasyGit 面板

### 方式三：工具窗口

底部 "EasyGit" 面板，提供可视化操作界面：
- 仓库列表（勾选要操作的仓库）
- 快捷按钮
- 操作历史查看

---

## 对话框功能说明

### 合并对话框
- 仓库选择表格（支持全选/取消全选）
- 源分支和目标分支选择
- 合并前 Fetch 选项
- 合并后推送选项

### Tag 对话框
- 仓库选择表格，每个仓库可设置独立的 Tag 名称
- 显示每个仓库的当前分支和最新 Tag
- **批量设置**：输入 Tag 名称后点击"应用到所有仓库"
- **重置**：恢复为每个仓库自动计算的建议 Tag 名称
- Tag 类型选择（正常版本/临时需求/Hotfix）
- 描述（可选）
- 创建后自动 Push Tag 选项
- 查看更多 Tag（点击可查看仓库最近 20 个 Tag）

---

## 配置说明

### 全局配置

`Settings → Tools → EasyGit`

#### 仓库配置
- **已配置的仓库**：手动添加仓库路径列表
- **批量扫描**：指定根目录，自动扫描子目录中的 Git 仓库

#### 分支配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 开发环境分支 | dev | 对应 MergeTarget.DEV |
| 测试环境分支 | test | 对应 MergeTarget.TEST |
| 生产环境分支 | main | 对应 MergeTarget.MAIN |

#### 自动化选项
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 合并前自动 Fetch | ✓ | 确保获取远程最新代码 |
| 合并后自动 Push | ✗ | 自动推送合并结果到远程 |

---

## 项目结构

```
EasyGit/
├── build.gradle.kts              # Gradle 构建配置
├── settings.gradle.kts           # Gradle 设置
├── gradle.properties             # 属性配置
├── src/main/
│   ├── kotlin/com/github/easygit/
│   │   ├── actions/              # Action 类（菜单动作）
│   │   │   ├── MergeActions.kt           # 基础合并 Action
│   │   │   ├── OneClickMergeAction.kt    # 一键合并
│   │   │   ├── CreateTagAction.kt        # 创建 Tag
│   │   │   ├── OtherActions.kt           # 其他 Action
│   │   │   └── ProjectViewActions.kt     # 项目视图右键 Action
│   │   ├── git/                  # Git 操作封装
│   │   │   ├── GitOperations.kt          # Git 底层操作
│   │   │   ├── MergeExecutor.kt          # 合并执行器
│   │   │   └── TagManager.kt             # Tag 管理器
│   │   ├── model/                # 数据模型
│   │   │   ├── MergeConfig.kt            # 合并配置
│   │   │   ├── TagConfig.kt              # Tag 配置
│   │   │   └── OperationHistory.kt       # 操作历史
│   │   ├── service/              # 服务类
│   │   │   ├── RepositoryService.kt      # 仓库管理
│   │   │   ├── HistoryService.kt         # 历史记录
│   │   │   └── NotificationService.kt    # 通知服务
│   │   ├── settings/             # 配置持久化
│   │   │   ├── EasyGitSettings.kt        # 全局配置
│   │   │   ├── EasyGitProjectSettings.kt # 项目配置
│   │   │   └── EasyGitConfigurable.kt    # 设置页面
│   │   └── ui/                   # UI 组件
│   │       ├── EasyGitToolWindow.kt      # 工具窗口
│   │       ├── MergeDialog.kt            # 合并对话框
│   │       ├── TagDialog.kt              # Tag 对话框
│   │       ├── ResultDialog.kt           # 结果对话框
│   │       └── HistoryDialog.kt          # 历史对话框
│   └── resources/
│       └── META-INF/
│           └── plugin.xml        # 插件描述文件
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties
```

---

## 核心类说明

### Actions

| 类名 | 说明 |
|------|------|
| `MergeToDevAction` | 合并到 dev 分支 |
| `MergeToTestAction` | 合并到 test 分支 |
| `MergeToMainAction` | 合并到 main 分支 |
| `OneClickMergeAction` | 一键合并所有环境 |
| `CreateTagAction` | 创建 Tag（弹出对话框） |
| `BatchMergeAction` | 批量合并（弹出对话框） |
| `ProjectViewMergeToDevAction` | 项目视图右键 - 合并到 dev |
| `ProjectViewMergeToTestAction` | 项目视图右键 - 合并到 test |
| `ProjectViewMergeToMainAction` | 项目视图右键 - 合并到 main |
| `ProjectViewOneClickMergeAction` | 项目视图右键 - 一键合并所有环境 |
| `ProjectViewCreateTagAction` | 项目视图右键 - 打 Tag |

### Git 操作

| 类名 | 说明 |
|------|------|
| `GitOperations` | Git 底层操作封装（fetch, checkout, merge, push, tag 等） |
| `MergeExecutor` | 合并流程执行器，包含完整的合并逻辑 |
| `TagManager` | Tag 管理器，智能序号计算和 Tag 创建 |

### 数据模型

| 类名 | 说明 |
|------|------|
| `MergeConfig` | 合并配置（源分支、目标分支、选项） |
| `MergeTarget` | 合并目标枚举（DEV, TEST, MAIN） |
| `MergeResult` | 合并结果（Success, Conflict, Error） |
| `TagConfig` | Tag 配置（基础版本、类型、描述） |
| `TagType` | Tag 类型枚举（NORMAL, PATCH, HOTFIX） |
| `TagResult` | Tag 创建结果 |
| `BatchOperationResult` | 批量操作结果 |
| `OperationHistory` | 操作历史记录 |

### UI 组件

| 类名 | 说明 |
|------|------|
| `TagDialog` | Tag 创建对话框，支持多仓库独立 Tag 名称 |
| `MergeDialog` | 合并配置对话框 |
| `ResultDialog` | 操作结果展示对话框 |
| `TagResultDialog` | Tag 创建结果对话框 |
| `HistoryDialog` | 操作历史对话框 |
| `TagListDialog` | Tag 列表查看对话框 |
| `RepositoryTagInfo` | 仓库 Tag 信息数据类 |

### 服务

| 类名 | 说明 |
|------|------|
| `RepositoryService` | 仓库管理，扫描和加载 Git 仓库 |
| `HistoryService` | 操作历史持久化服务 |
| `NotificationService` | 通知消息服务 |

### 配置

| 类名 | 说明 |
|------|------|
| `EasyGitSettings` | 应用级全局配置 |
| `EasyGitProjectSettings` | 项目级配置 |
| `EasyGitConfigurable` | 设置页面 UI |

---

## 开发指南

### 环境要求
- JDK 17+
- IntelliJ IDEA 2023.3+
- Gradle 8.5+

### 构建和运行

```bash
# 在 IDEA 中打开项目后

# 运行插件测试（启动新的 IDEA 实例）
Gradle → Tasks → intellij platform → runIde

# 构建插件安装包
Gradle → Tasks → intellij platform → buildPlugin
# 输出: build/distributions/EasyGit-1.0.0.zip

# 安装到正式 IDEA
Settings → Plugins → ⚙️ → Install Plugin from Disk
```

### 添加新功能

#### 添加新的合并目标
1. 在 `MergeTarget` 枚举中添加新值
2. 在 `EasyGitSettings` 中添加对应配置项
3. 创建新的 Action 类
4. 在 `plugin.xml` 中注册 Action

#### 添加新的 Tag 类型
1. 在 `TagType` 枚举中添加新值
2. 在 `TagManager.getNextSequence()` 中添加匹配逻辑
3. 创建新的快速 Tag Action（可选）

---

## 配置文件说明

### plugin.xml

插件的核心配置文件，定义：
- 插件 ID、名称、依赖
- 扩展点（工具窗口、服务、设置页面）
- Action 注册和菜单位置

### gradle.properties

```properties
pluginGroup=com.github.easygit
pluginName=EasyGit
pluginVersion=1.0.0
platformType=IC
platformVersion=2023.3
pluginSinceBuild=233
pluginUntilBuild=243.*
```

---

## 版本历史

### v1.0.0
- 初始版本发布
- 支持分支合并（dev/test/main）
- 支持一键合并所有环境（并行合并策略）
- 支持智能打 Tag（正常版本/临时需求/Hotfix）
- 支持批量操作多个仓库
- 支持为每个仓库设置独立的 Tag 名称
- 批量设置 Tag 名称和重置功能
- 支持操作历史记录
- 项目视图右键菜单集成
- VCS 菜单集成
- 底部工具窗口
