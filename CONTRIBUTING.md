# 贡献指南

感谢您对 EasyGit 项目的关注！本文档将指导您如何参与项目贡献。

## 开发环境准备

### 必需工具
- JDK 17+
- IntelliJ IDEA 2023.3+
- Gradle 8.5+
- Git

### 项目设置

```bash
# 克隆仓库
git clone https://github.com/ddgao/EasyGit.git
cd EasyGit

# 在 IDEA 中打开项目
# File → Open → 选择项目目录

# 运行插件测试
./gradlew runIde
```

## Git 工作流

### 分支策略

```
main（生产分支，受保护）
  ├── develop（开发主分支）
  │     ├── feature/xxx（功能分支）
  │     ├── bugfix/xxx（修复分支）
  │     └── refactor/xxx（重构分支）
  └── hotfix/xxx（紧急修复分支）
```

### 分支命名规范

- `feature/功能描述` - 新功能开发
- `bugfix/问题描述` - Bug 修复
- `hotfix/紧急修复` - 生产环境紧急修复
- `refactor/重构描述` - 代码重构
- `docs/文档更新` - 文档更新

**示例：**
```bash
feature/batch-tag-description
bugfix/merge-conflict-detection
hotfix/critical-npe-fix
refactor/git-operations-cleanup
docs/update-installation-guide
```

## 提交规范

### Conventional Commits 格式

```
<类型>(<范围>): <简短描述>

[可选的详细描述]

[可选的关联 Issue]
```

### 提交类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(tag): 添加批量设置 Tag 描述功能` |
| `fix` | Bug 修复 | `fix(merge): 修复合并冲突检测失败问题` |
| `docs` | 文档更新 | `docs(readme): 更新安装指南` |
| `style` | 代码格式（不影响功能） | `style(ui): 统一对话框按钮样式` |
| `refactor` | 重构代码 | `refactor(git): 简化 GitOperations 接口` |
| `perf` | 性能优化 | `perf(tag): 优化 Tag 序号计算算法` |
| `test` | 测试相关 | `test(merge): 添加合并冲突检测单元测试` |
| `chore` | 构建/工具配置 | `chore(gradle): 更新依赖版本` |
| `ci` | CI/CD 配置 | `ci(github): 添加自动构建工作流` |

### 提交示例

```bash
# 单行提交
git commit -m "feat(tag): 支持批量设置 Tag 描述"

# 多行提交
git commit -m "fix(merge): 修复一键合并时分支切换失败

- 在合并前检查工作区状态
- 如果有未提交的更改，提示用户处理
- 添加合并失败时的分支回滚逻辑

Closes #42"
```

## 开发流程

### 1. 创建功能分支

```bash
# 从 develop 分支创建新功能分支
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

### 2. 开发和测试

```bash
# 编写代码
# ...

# 运行插件测试
./gradlew runIde

# 构建插件
./gradlew buildPlugin
```

### 3. 提交代码

```bash
# 添加修改
git add .

# 提交（遵循 Conventional Commits 规范）
git commit -m "feat(scope): 简短描述"

# 推送到远程
git push origin feature/your-feature-name
```

### 4. 创建 Pull Request

1. 访问 GitHub 仓库页面
2. 点击 **Pull Request** → **New Pull Request**
3. 选择 `base: develop` ← `compare: feature/your-feature-name`
4. 填写 PR 描述（使用提供的模板）
5. 等待代码审查和 CI 检查

### 5. 代码审查

- 至少需要 1 位维护者审批
- 所有 CI 检查必须通过
- 解决所有审查意见后合并

## 代码规范

### Kotlin 编码风格

遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

**关键要点：**
- 使用 4 个空格缩进
- 每行最多 120 字符
- 类名使用 PascalCase
- 函数和变量使用 camelCase
- 常量使用 UPPER_SNAKE_CASE

### 代码质量要求

- **SOLID 原则**：单一职责、开闭原则等
- **DRY 原则**：避免重复代码
- **清晰命名**：变量和函数名要有描述性
- **必要注释**：复杂逻辑添加注释说明
- **错误处理**：妥善处理异常和边界情况

### 示例代码结构

```kotlin
/**
 * Tag 管理器，负责 Tag 的创建和序号计算
 */
class TagManager(private val project: Project) {

    /**
     * 获取下一个 Tag 序号
     *
     * @param repository Git 仓库
     * @param baseVersion 基础版本号（如 v1.0.0）
     * @param tagType Tag 类型
     * @return 完整的 Tag 名称（如 v1.0.0-P01）
     */
    fun getNextTagName(
        repository: GitRepository,
        baseVersion: String,
        tagType: TagType
    ): String {
        // 实现逻辑...
    }
}
```

## 测试要求

### 手动测试清单

提交 PR 前，请确保完成以下测试：

- [ ] 插件能正常加载（`./gradlew runIde`）
- [ ] 所有菜单项可正常访问
- [ ] 核心功能按预期工作
- [ ] 没有异常日志输出
- [ ] UI 界面显示正常

### 单元测试（推荐）

虽然当前项目暂无单元测试，但我们鼓励为关键逻辑添加测试：

```kotlin
class TagManagerTest {
    @Test
    fun `should calculate next patch tag correctly`() {
        // 测试逻辑...
    }
}
```

## 发布流程

### 版本号规范（Semantic Versioning）

格式：`MAJOR.MINOR.PATCH`

- **MAJOR**：不兼容的 API 修改
- **MINOR**：向后兼容的功能新增
- **PATCH**：向后兼容的问题修复

**示例：**
- `1.0.0` → `1.0.1`（修复 Bug）
- `1.0.1` → `1.1.0`（添加新功能）
- `1.1.0` → `2.0.0`（不兼容的重大更新）

### 发布步骤

1. **更新版本号**
   ```bash
   # 编辑 gradle.properties
   pluginVersion=1.1.0
   ```

2. **更新 CHANGELOG.md**
   ```markdown
   ## [1.1.0] - 2026-01-15

   ### Added
   - 批量设置 Tag 描述功能

   ### Fixed
   - 修复一键合并时分支切换失败问题
   ```

3. **提交版本更新**
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "chore(release): 发布 v1.1.0"
   git push origin develop
   ```

4. **合并到 main 并打 Tag**
   ```bash
   git checkout main
   git merge develop
   git tag -a v1.1.0 -m "Release v1.1.0"
   git push origin main --tags
   ```

5. **在 GitHub 创建 Release**
   - 访问仓库的 Releases 页面
   - 点击 **Draft a new release**
   - 选择刚创建的 Tag `v1.1.0`
   - 填写 Release 标题和说明（从 CHANGELOG 复制）
   - 上传构建产物 `build/distributions/EasyGit-1.1.0.zip`

## 问题反馈

### 提交 Issue

发现问题或有功能建议时，请：

1. 检查是否已有类似 Issue
2. 使用 Issue 模板创建新 Issue
3. 提供详细的描述和复现步骤
4. 附上相关的日志或截图

### Issue 标签

- `bug` - Bug 报告
- `enhancement` - 功能请求
- `documentation` - 文档相关
- `good first issue` - 适合新手
- `help wanted` - 需要帮助

## 联系方式

- **GitHub Issues**: https://github.com/ddgao/EasyGit/issues
- **Email**: 1073134503@qq.com

---

再次感谢您的贡献！🎉
