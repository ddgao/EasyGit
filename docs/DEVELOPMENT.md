# EasyGit 开发指南

## 开发环境搭建

### 环境要求
- JDK 17+
- IntelliJ IDEA 2023.3+
- Gradle 8.5+（使用 Gradle Wrapper）

### 项目导入

1. 打开 IntelliJ IDEA
2. `File → Open` 选择项目目录
3. 等待 Gradle 同步完成

### 运行和调试

```bash
# 运行插件（启动新的 IDEA 实例）
./gradlew runIde

# 构建插件包
./gradlew buildPlugin
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

---

## 添加新功能

### 添加新的合并目标

1. **修改枚举**：`model/MergeConfig.kt`
2. **添加配置**：`settings/EasyGitSettings.kt`
3. **更新设置页面**：`settings/EasyGitConfigurable.kt`
4. **创建 Action**：`actions/MergeActions.kt` 和 `actions/ProjectViewActions.kt`
5. **注册 Action**：`plugin.xml`

### 添加新的 Tag 类型

1. **修改枚举**：`model/TagConfig.kt`
2. **创建快捷 Action（可选）**：`actions/ProjectViewActions.kt`
3. **注册 Action**：`plugin.xml`

---

## 后台任务

```kotlin
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "任务标题", true) {
    override fun run(indicator: ProgressIndicator) {
        indicator.fraction = 0.5  // 进度 50%
        indicator.text = "正在处理..."
        
        // 执行操作
    }
})
```

### UI 线程操作

```kotlin
ApplicationManager.getApplication().invokeLater {
    // UI 操作
}
```

---

## 调试技巧

### 日志输出

```kotlin
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(MyClass::class.java)
LOG.info("信息日志")
LOG.error("错误日志", exception)
```

### 查看日志

- `Help → Show Log in Finder/Explorer`

---

## 发布

```bash
# 构建
./gradlew buildPlugin

# 更新版本号
# 编辑 gradle.properties: pluginVersion=1.0.1
```
