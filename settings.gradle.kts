pluginManagement {
    repositories {
        // 阿里云镜像优先（国内网络更稳定）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 官方仓库备用
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "EasyGit"
