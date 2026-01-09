plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// IntelliJ Platform 配置
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
        description = """
            <h2>EasyGit - Git 分支合并助手</h2>
            <p>专为微服务项目开发设计的 IntelliJ IDEA 插件，简化 Git 分支合并和 Tag 管理流程。</p>

            <h3>核心痛点解决</h3>
            <table>
                <tr><td>每次需要手动合并功能分支到 dev/test/main</td><td>→ 一键合并到指定环境或所有环境</td></tr>
                <tr><td>多个微服务项目需要重复操作</td><td>→ 批量选择多个仓库同时操作</td></tr>
                <tr><td>打 Tag 需要手动计算序号</td><td>→ 智能识别已有 Tag，自动累加序号</td></tr>
                <tr><td>合并操作容易遗漏</td><td>→ 操作历史记录，方便追溯</td></tr>
                <tr><td>打完 Tag 需要手动记录</td><td>→ 汇总展示所有创建的 Tag</td></tr>
                <tr><td>多仓库打 Tag 版本不同</td><td>→ 支持为每个仓库设置独立的 Tag 名称</td></tr>
            </table>

            <h3>功能特性</h3>
            <h4>1. 分支合并</h4>
            <ul>
                <li><b>分步合并</b>：功能分支 → dev / test / main</li>
                <li><b>一键合并</b>：功能分支同时合并到 dev、test、main</li>
                <li>合并前自动 fetch 最新代码</li>
                <li>冲突检测和提示，中断流程让用户手动解决</li>
                <li>可配置是否自动 push</li>
                <li>合并完成后自动切回原分支</li>
            </ul>

            <h4>2. 智能打 Tag</h4>
            <p>支持三种 Tag 格式：</p>
            <ul>
                <li><b>正常版本</b>：v1.0.0</li>
                <li><b>临时需求</b>：v1.0.0-P01、v1.0.0-P02（自动累加）</li>
                <li><b>Hotfix</b>：v1.0.0-H01、v1.0.0-H02（自动累加）</li>
            </ul>
            <p>多仓库独立 Tag 名称：</p>
            <ul>
                <li>每个仓库可设置不同的 Tag 名称</li>
                <li>支持批量设置统一 Tag 名称</li>
                <li>支持重置为自动计算的建议值</li>
            </ul>

            <h4>3. 批量操作</h4>
            <ul>
                <li>支持同时选择多个独立 Git 仓库</li>
                <li>进度条显示处理状态</li>
                <li>结果汇总展示</li>
            </ul>

            <h4>4. 操作历史</h4>
            <ul>
                <li>记录每次合并/打 Tag 操作</li>
                <li>包含时间、仓库、操作类型、结果</li>
                <li>支持查看和清空历史</li>
            </ul>

            <h3>使用方式</h3>
            <h4>方式一：项目目录右键菜单（推荐）</h4>
            <p>在 Project 面板中，右键点击项目目录：</p>
            <pre>
右键项目目录 → EasyGit
    ├── 合并到 Dev
    ├── 合并到 Test
    ├── 合并到 Main
    ├── ─────────────
    ├── 一键合并所有环境
    ├── ─────────────
    └── 打 Tag
            </pre>
            <p><b>支持多选</b>：按住 Ctrl/Cmd 选中多个项目，右键可批量操作。</p>

            <h4>方式二：VCS 菜单</h4>
            <p>VCS → EasyGit 下有完整功能菜单：</p>
            <ul>
                <li>合并到 Dev / Test / Main</li>
                <li>一键合并所有环境</li>
                <li>打 Tag</li>
                <li>批量合并</li>
                <li>打开 EasyGit 面板</li>
            </ul>

            <h4>方式三：工具窗口</h4>
            <p>底部 "EasyGit" 面板，提供可视化操作界面：</p>
            <ul>
                <li>仓库列表（勾选要操作的仓库）</li>
                <li>快捷按钮</li>
                <li>操作历史查看</li>
            </ul>

            <h3>配置</h3>
            <p>Settings → Tools → EasyGit，可配置：</p>
            <ul>
                <li>仓库路径列表（支持批量扫描）</li>
                <li>环境分支名称（dev / test / main）</li>
                <li>自动 Fetch / Push 选项</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <h3>1.0.0</h3>
            <ul>
                <li>初始版本发布</li>
                <li>支持批量分支合并</li>
                <li>支持智能打 Tag</li>
                <li>支持操作历史记录</li>
            </ul>
        """.trimIndent()
    }
}

dependencies {
    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        instrumentationTools()
    }
}

tasks {
    // JVM 版本
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    // 运行 IDE 进行测试
    runIde {
        jvmArgs("-Xmx2g")
    }

    // 签名配置（发布到 Marketplace 需要）
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
