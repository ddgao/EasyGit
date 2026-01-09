# Changelog

本文档记录 EasyGit 项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 计划功能
- [ ] 支持自定义合并策略（fast-forward / squash / rebase）
- [ ] 添加合并前代码审查集成
- [ ] 支持批量删除远程 Tag
- [ ] 添加 Git 操作历史导出功能
- [ ] 支持自定义分支命名规则

---

## [1.0.0] - 2026-01-09

### Added（新增功能）

#### 核心功能
- ✅ **分支合并功能**
  - 支持合并到 dev / test / main 分支
  - 一键合并所有环境（并行合并策略）
  - 合并前自动 fetch 最新代码
  - 冲突检测和提示
  - 可配置自动 push 选项
  - 合并完成后自动切回原分支

- ✅ **智能打 Tag 功能**
  - 支持三种 Tag 格式：
    - 正常版本：`v1.0.0`
    - 临时需求：`v1.0.0-P01`, `v1.0.0-P02`（自动累加）
    - Hotfix：`v1.0.0-H01`, `v1.0.0-H02`（自动累加）
  - 智能识别已有 Tag，自动计算下一个序号
  - 支持为每个仓库设置独立的 Tag 名称
  - 批量设置统一 Tag 名称
  - 重置为自动计算的建议值
  - Tag 创建结果汇总展示

- ✅ **批量操作功能**
  - 支持同时选择多个 Git 仓库
  - 进度条显示处理状态
  - 结果汇总展示
  - 错误处理和提示

- ✅ **操作历史记录**
  - 记录每次合并/打 Tag 操作
  - 包含时间、仓库、操作类型、结果
  - 支持查看和清空历史

#### 用户界面
- ✅ **三种操作方式**
  - 项目目录右键菜单（支持多选）
  - VCS 菜单集成
  - 底部工具窗口

- ✅ **对话框组件**
  - 合并配置对话框
  - Tag 创建对话框（支持独立 Tag 名称设置）
  - 操作结果展示对话框
  - Tag 列表查看对话框
  - 操作历史对话框

#### 配置功能
- ✅ **全局配置**（`Settings → Tools → EasyGit`）
  - 仓库路径配置（手动添加）
  - 批量扫描 Git 仓库
  - 自定义分支名称（dev / test / main）
  - 自动化选项配置

- ✅ **自动化选项**
  - 合并前自动 Fetch（默认开启）
  - 合并后自动 Push（默认关闭）

#### 文档和规范
- ✅ 完整的 README.md 文档
  - 项目概述和功能特性
  - 详细的使用指南
  - 开发指南和项目结构说明
- ✅ 架构文档（docs/ARCHITECTURE.md）
- ✅ API 文档（docs/API.md）
- ✅ 开发文档（docs/DEVELOPMENT.md）
- ✅ Apache 2.0 开源许可证
- ✅ 完整的 .gitignore 配置

### Technical（技术实现）

#### 项目架构
- **语言**: Kotlin
- **构建工具**: Gradle 8.5+
- **平台**: IntelliJ Platform 2023.3+
- **架构模式**: MVVM + Service Layer

#### 代码结构
```
src/main/kotlin/com/github/easygit/
├── actions/              # Action 类（菜单动作）
├── git/                  # Git 操作封装
├── model/                # 数据模型
├── service/              # 服务类
├── settings/             # 配置持久化
└── ui/                   # UI 组件
```

#### 核心组件
- `GitOperations`: Git 底层操作封装
- `MergeExecutor`: 合并流程执行器
- `TagManager`: Tag 管理器
- `RepositoryService`: 仓库管理服务
- `HistoryService`: 历史记录服务

### Dependencies（依赖）
- IntelliJ Platform SDK 2023.3
- Kotlin 1.9+
- Git4Idea（IntelliJ Git 插件）

---

## 版本规划

### [1.1.0] - 计划中
**主题**: 功能增强和用户体验优化

#### 计划新增
- [ ] Tag 批量描述设置
- [ ] 合并策略选择（fast-forward / squash / rebase）
- [ ] 操作历史导出为 CSV/JSON
- [ ] 仓库分组管理
- [ ] 快捷键支持

#### 计划优化
- [ ] 优化大量仓库时的性能
- [ ] 改进错误提示信息
- [ ] 添加操作撤销功能

### [2.0.0] - 未来规划
**主题**: 高级功能和企业级支持

#### 计划新增
- [ ] 多人协作冲突解决辅助
- [ ] GitLab / Bitbucket 集成
- [ ] 自定义工作流模板
- [ ] 代码审查集成
- [ ] 远程 Tag 管理（删除、重命名）

---

## 链接

- [GitHub 仓库](https://github.com/ddgao/EasyGit)
- [问题反馈](https://github.com/ddgao/EasyGit/issues)
- [贡献指南](CONTRIBUTING.md)

---

## 更新说明

### 版本号规则
遵循语义化版本（Semantic Versioning）：

- **MAJOR**（主版本号）：不兼容的 API 修改
- **MINOR**（次版本号）：向后兼容的功能新增
- **PATCH**（修订号）：向后兼容的问题修复

### 变更类型

- **Added**: 新增功能
- **Changed**: 功能变更
- **Deprecated**: 即将废弃的功能
- **Removed**: 已移除的功能
- **Fixed**: Bug 修复
- **Security**: 安全性修复
- **Performance**: 性能优化
- **Technical**: 技术实现细节

### 示例格式

```markdown
## [1.1.0] - 2026-01-15

### Added
- 新增批量设置 Tag 描述功能 (#42)
- 支持自定义合并策略选择 (#45)

### Fixed
- 修复一键合并时分支切换失败问题 (#38)
- 修复 Tag 序号计算错误 (#40)

### Changed
- 优化大量仓库加载性能 (#43)

### Technical
- 重构 GitOperations 接口，简化调用逻辑
- 升级 Kotlin 版本到 1.9.22
```
