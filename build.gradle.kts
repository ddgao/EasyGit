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

            <h3>核心功能</h3>
            <ul>
                <li><b>分支合并（基于远端分支）</b>：将功能分支合并到 dev / test / main，目标使用远端分支</li>
                <li><b>一键合并</b>：一次操作依次合并到所有环境</li>
                <li><b>智能打 Tag（基于远端分支）</b>：Tag 基于配置的远端分支创建，支持正常版本、临时需求(-P)、Hotfix(-H)</li>
                <li><b>批量操作</b>：支持同时选择多个 Git 仓库进行批量合并或打 Tag</li>
                <li><b>独立 Tag 名称</b>：每个仓库可设置不同的 Tag 名称</li>
                <li><b>操作历史</b>：记录每次操作的详细信息</li>
            </ul>

            <h3>使用方式</h3>
            <ul>
                <li><b>项目视图右键菜单（推荐）</b>：选中项目目录 → 右键 → EasyGit（支持多选批量操作）</li>
                <li><b>VCS 菜单</b>：VCS → EasyGit</li>
                <li><b>工具窗口</b>：底部 EasyGit 面板，自动加载项目中的 Git 仓库</li>
            </ul>

            <h3>配置</h3>
            <p>Settings → Tools → EasyGit，可配置：</p>
            <ul>
                <li>环境分支名称（dev / test / main）</li>
                <li>打 Tag 基准分支（默认 main）</li>
                <li>自动 Fetch / Push 选项</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <h3>1.0.6</h3>
            <ul>
                <li>移除筛选区域的排序下拉，仅保留表格列头点击排序</li>
                <li>分支清理表格支持点击列头切换升序/降序排序</li>
                <li>分支清理新增距今天数排序（默认/升序/降序）</li>
                <li>分支清理新增按已合并到 dev/test/main 的组合筛选</li>
                <li>修复分支清理对话框在模态状态下 UI 回调被阻塞导致持续加载的问题</li>
                <li>分支清理页增加独立超时守卫，确保异常时不会无限加载</li>
                <li>修复分支清理页面可能持续加载的问题（增加超时与异常兜底）</li>
                <li>初始版本发布</li>
                <li>支持分支合并（基于远端分支）</li>
                <li>支持智能打 Tag（基于配置的远端分支）</li>
                <li>支持批量操作多个仓库</li>
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

// 自动递增 patch 版本号（+0.0.1）并打包
tasks.register("bumpAndBuild") {
    group = "build"
    description = "自动递增 patch 版本号并打包插件"

    doFirst {
        val propsFile = file("gradle.properties")
        val props = propsFile.readText()
        val regex = Regex("""pluginVersion=(\d+)\.(\d+)\.(\d+)""")
        val match = regex.find(props) ?: error("未找到 pluginVersion")
        val (major, minor, patch) = match.destructured
        val newVersion = "$major.$minor.${patch.toInt() + 1}"
        propsFile.writeText(props.replace(match.value, "pluginVersion=$newVersion"))
        println("版本号: ${match.value.substringAfter("=")} → $newVersion")
    }

    doLast {
        // 用新进程构建，确保读取更新后的版本号
        exec {
            workingDir = projectDir
            commandLine("./gradlew", "clean", "buildPlugin")
        }
    }
}
