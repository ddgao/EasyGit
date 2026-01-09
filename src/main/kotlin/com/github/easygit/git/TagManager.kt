package com.github.easygit.git

import com.github.easygit.model.TagConfig
import com.github.easygit.model.TagResult
import com.github.easygit.model.TagType
import com.github.easygit.settings.EasyGitSettings
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository

/**
 * Tag 管理器
 * 负责智能 Tag 命名和创建
 */
class TagManager(private val project: Project) {

    private val gitOps = GitOperations(project)
    private val settings = EasyGitSettings.getInstance()

    /**
     * 获取最新的 Tag（支持多种格式）
     * 例如：v1.0.0, R_3.1.6, R_EMALL_STOCK_2406
     */
    fun getLatestVersionTag(repository: GitRepository): String? {
        val tags = gitOps.getAllTags(repository)
        // 返回第一个（最新的）Tag，如果没有则返回 null
        return tags.firstOrNull()
    }

    /**
     * 使用配置的正则表达式从 Tag 中提取基础版本号
     * @param tag 完整的 Tag 名称，如 R_3.1.6-H02
     * @return 提取的基础版本号，如 R_3.1.6，如果不匹配则返回 null
     */
    fun extractBaseVersionFromTag(tag: String): String? {
        val pattern = settings.state.tagVersionPattern
        return try {
            val regex = Regex(pattern)
            val match = regex.find(tag)
            // 返回第一个捕获组，如果没有捕获组则返回整个匹配
            match?.groups?.get(1)?.value ?: match?.value
        } catch (e: Exception) {
            // 正则表达式错误，返回 null
            null
        }
    }

    /**
     * 获取推荐的基础版本号（从最新 Tag 中提取）
     * 使用配置的正则匹配并提取
     */
    fun suggestBaseVersion(repository: GitRepository): String? {
        val tags = gitOps.getAllTags(repository)
        val pattern = settings.state.tagVersionPattern

        return try {
            val regex = Regex(pattern)
            // 找到第一个匹配正则的 Tag
            for (tag in tags) {
                val match = regex.find(tag)
                if (match != null) {
                    // 返回第一个捕获组，如果没有捕获组则返回整个匹配
                    val group1 = if (match.groups.size > 1) match.groups[1] else null
                    return group1?.value ?: match.value
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取最新的语义化版本 Tag
     * 例如：v1.0.0, 1.2.3, R_3.1.6
     */
    fun getLatestSemanticVersionTag(repository: GitRepository): String? {
        val tags = gitOps.getAllTags(repository)
        // 匹配 v1.0.0 或 1.0.0 格式
        val versionPattern = Regex("^v?(\\d+\\.\\d+\\.\\d+).*$")
        // 匹配 R_x.x.x 格式
        val rVersionPattern = Regex("^R_(\\d+\\.\\d+\\.\\d+).*$")

        return tags.firstOrNull {
            versionPattern.matches(it) || rVersionPattern.matches(it)
        }
    }

    /**
     * 获取指定基础版本的最新序号
     * @param baseVersion 基础版本，如 v1.0.0
     * @param tagType Tag 类型（PATCH 或 HOTFIX）
     * @return 下一个序号
     */
    fun getNextSequence(repository: GitRepository, baseVersion: String, tagType: TagType): Int {
        if (tagType == TagType.NORMAL) {
            return 1
        }

        val tags = gitOps.getAllTags(repository)
        val prefix = tagType.prefix
        val pattern = Regex("^${Regex.escape(baseVersion)}${Regex.escape(prefix)}(\\d+)$")

        val maxSequence = tags
            .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0

        return maxSequence + 1
    }

    /**
     * 生成下一个 Tag 名称
     */
    fun generateNextTagName(repository: GitRepository, baseVersion: String, tagType: TagType): String {
        val sequence = getNextSequence(repository, baseVersion, tagType)
        val config = TagConfig(baseVersion, tagType)
        return config.generateTagName(sequence)
    }

    /**
     * 创建 Tag
     */
    fun createTag(
        repository: GitRepository,
        tagConfig: TagConfig,
        autoPush: Boolean = false
    ): TagResult {
        val repoName = repository.root.name
        val sequence = getNextSequence(repository, tagConfig.baseVersion, tagConfig.tagType)
        val tagName = tagConfig.generateTagName(sequence)

        // 创建 Tag
        val createResult = gitOps.createTag(
            repository,
            tagName,
            tagConfig.description.ifBlank { null }
        )

        if (!createResult.success()) {
            return TagResult(
                repositoryName = repoName,
                tagName = tagName,
                success = false,
                message = "创建 Tag 失败: ${createResult.errorOutputAsJoinedString}"
            )
        }

        // 推送 Tag（如果需要）
        if (autoPush) {
            val pushResult = gitOps.pushTag(repository, tagName)
            if (!pushResult.success()) {
                return TagResult(
                    repositoryName = repoName,
                    tagName = tagName,
                    success = false,
                    message = "Tag 已创建，但推送失败: ${pushResult.errorOutputAsJoinedString}"
                )
            }
        }

        return TagResult(
            repositoryName = repoName,
            tagName = tagName,
            success = true,
            message = if (autoPush) "Tag 创建并推送成功" else "Tag 创建成功"
        )
    }

    /**
     * 批量创建 Tag
     */
    fun createTagsForRepositories(
        repositories: List<GitRepository>,
        baseVersion: String,
        tagType: TagType,
        description: String = "",
        autoPush: Boolean = false
    ): List<TagResult> {
        return repositories.map { repo ->
            val config = TagConfig(baseVersion, tagType, description, autoPush)
            createTag(repo, config, autoPush)
        }
    }

    /**
     * 解析版本号，提取主版本号
     * 例如：v1.0.0-P01 -> v1.0.0
     */
    fun extractBaseVersion(tagName: String): String? {
        val pattern = Regex("^(v?\\d+\\.\\d+\\.\\d+)(-[PH]\\d+)?$")
        return pattern.matchEntire(tagName)?.groupValues?.get(1)
    }

    /**
     * 获取推荐的下一个版本号
     * 基于当前最新 Tag 推荐
     */
    fun suggestNextVersion(repository: GitRepository): String {
        val latestTag = getLatestVersionTag(repository) ?: return "v1.0.0"

        val versionPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
        val match = versionPattern.matchEntire(latestTag) ?: return "v1.0.0"

        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        val patch = match.groupValues[3].toInt()

        // 默认递增 patch 版本
        return "v$major.$minor.${patch + 1}"
    }

    /**
     * 验证版本号格式
     */
    fun isValidVersion(version: String): Boolean {
        val pattern = Regex("^v?\\d+\\.\\d+\\.\\d+$")
        return pattern.matches(version)
    }

    /**
     * 格式化版本号（确保以 v 开头）
     */
    fun formatVersion(version: String): String {
        return if (version.startsWith("v")) version else "v$version"
    }

    /**
     * 获取前 N 个 Tag（按时间降序）
     */
    fun getTopTags(repository: GitRepository, count: Int = 20): List<String> {
        return gitOps.getAllTags(repository).take(count)
    }

    /**
     * 智能计算下一个 Tag 名称
     * @param repository Git 仓库
     * @param tagType Tag 类型
     * @return 计算出的下一个 Tag 名称
     */
    fun suggestNextTagName(repository: GitRepository, tagType: TagType): String {
        val tags = gitOps.getAllTags(repository)

        return when (tagType) {
            TagType.NORMAL -> suggestNextNormalTag(tags)
            TagType.PATCH -> suggestNextSuffixTag(tags, "-P")
            TagType.HOTFIX -> suggestNextSuffixTag(tags, "-H")
        }
    }

    /**
     * 计算下一个正常版本 Tag
     * 找到最新的主版本号，小版本号 +1
     * 例如：R_3.1.6 -> R_3.1.7, v1.2.3 -> v1.2.4
     */
    private fun suggestNextNormalTag(tags: List<String>): String {
        // 匹配各种版本号格式
        // R_x.x.x 或 R_x.x.x-Pxx 或 R_x.x.x-Hxx
        val rPattern = Regex("^(R_)(\\d+)\\.(\\d+)\\.(\\d+)(-[PH]\\d+)?$")
        // vx.x.x 或 x.x.x 或 vx.x.x-Pxx 或 vx.x.x-Hxx
        val vPattern = Regex("^(v?)(\\d+)\\.(\\d+)\\.(\\d+)(-[PH]\\d+)?$")

        for (tag in tags) {
            // 尝试匹配 R_ 格式
            var match = rPattern.matchEntire(tag)
            if (match != null) {
                val prefix = match.groupValues[1]  // R_
                val major = match.groupValues[2].toInt()
                val minor = match.groupValues[3].toInt()
                val patch = match.groupValues[4].toInt()
                return "${prefix}$major.$minor.${patch + 1}"
            }

            // 尝试匹配 v 格式
            match = vPattern.matchEntire(tag)
            if (match != null) {
                val prefix = match.groupValues[1].ifEmpty { "v" }  // v 或空
                val major = match.groupValues[2].toInt()
                val minor = match.groupValues[3].toInt()
                val patch = match.groupValues[4].toInt()
                return "${prefix}$major.$minor.${patch + 1}"
            }
        }

        return "v1.0.0"
    }

    /**
     * 计算下一个带后缀的 Tag（P 或 H）
     * 逻辑：
     * 1. 找到最新的 Tag
     * 2. 如果最新 Tag 带有相同后缀(-P 或 -H)：基础版本不变，序号 +1
     * 3. 如果最新 Tag 不带该后缀：基于最新 Tag 的基础版本创建第一个后缀 Tag
     * 例如：
     *   - 最新 Tag 是 R_3.1.6-P02，类型是 Patch -> R_3.1.6-P03
     *   - 最新 Tag 是 R_3.1.7-P01，类型是 Hotfix -> R_3.1.7-H01 (使用 P01 的基础版本 R_3.1.7)
     *   - 最新 Tag 是 R_3.1.6，类型是 Patch -> R_3.1.6-P01
     */
    private fun suggestNextSuffixTag(tags: List<String>, suffix: String): String {
        if (tags.isEmpty()) {
            return "v1.0.0$suffix${String.format("%02d", 1)}"
        }

        // 获取最新的 Tag
        val latestTag = tags.first()

        // 匹配带指定后缀的 Tag: R_x.x.x-Pxx 或 vx.x.x-Pxx
        val suffixPattern = Regex("^(.+)${Regex.escape(suffix)}(\\d+)$")

        // 检查最新 Tag 是否带有相同后缀
        val match = suffixPattern.matchEntire(latestTag)
        if (match != null) {
            // 最新 Tag 带有相同后缀，序号 +1
            val baseVersion = match.groupValues[1]
            val sequence = match.groupValues[2].toInt()
            return "$baseVersion$suffix${String.format("%02d", sequence + 1)}"
        }

        // 最新 Tag 不带该后缀，提取基础版本，创建第一个后缀 Tag
        // 注意：如果最新 Tag 带有其他后缀（如 -P），需要使用该后缀的基础版本
        val baseVersion = extractBaseVersionForNewSuffix(latestTag)
        return "$baseVersion$suffix${String.format("%02d", 1)}"
    }

    /**
     * 从最新 Tag 中提取基础版本号（用于创建新后缀 Tag）
     * 如果 Tag 带有后缀（-P 或 -H），则提取后缀前的基础版本
     * 例如：
     *   - R_3.1.7-P01 -> R_3.1.7 (用于创建 R_3.1.7-H01)
     *   - R_3.1.6 -> R_3.1.6
     *   - v1.2.0-H02 -> v1.2.0
     */
    private fun extractBaseVersionForNewSuffix(tag: String): String {
        // 匹配带后缀的 Tag: xxx-Pxx 或 xxx-Hxx
        val suffixPattern = Regex("^(.+)-[PH](\\d+)$")
        val suffixMatch = suffixPattern.matchEntire(tag)
        if (suffixMatch != null) {
            // 返回后缀前的基础版本，如 R_3.1.7-P01 -> R_3.1.7
            return suffixMatch.groupValues[1]
        }

        // 没有后缀，直接返回原 Tag（如果是有效的版本格式）
        // 匹配 R_x.x.x 格式
        val rPattern = Regex("^(R_\\d+\\.\\d+\\.\\d+)$")
        // 匹配 vx.x.x 或 x.x.x 格式
        val vPattern = Regex("^(v?\\d+\\.\\d+\\.\\d+)$")

        if (rPattern.matches(tag) || vPattern.matches(tag)) {
            return tag
        }

        // 无法匹配，返回默认值
        return "v1.0.0"
    }

    /**
     * 从最新 Tag 中提取基础版本号（去掉后缀）
     * 例如：R_3.1.6-H02 -> R_3.1.6, v1.2.0-P01 -> v1.2.0, R_3.1.6 -> R_3.1.6
     */
    private fun extractBaseVersionFromLatestTag(tag: String): String {
        // 匹配 R_x.x.x 或 R_x.x.x-Pxx 或 R_x.x.x-Hxx
        val rPattern = Regex("^(R_\\d+\\.\\d+\\.\\d+)(-[PH]\\d+)?$")
        // 匹配 vx.x.x 或 x.x.x 或 vx.x.x-Pxx 或 vx.x.x-Hxx
        val vPattern = Regex("^(v?\\d+\\.\\d+\\.\\d+)(-[PH]\\d+)?$")

        var match = rPattern.matchEntire(tag)
        if (match != null) {
            return match.groupValues[1]  // 返回基础版本，如 R_3.1.6
        }

        match = vPattern.matchEntire(tag)
        if (match != null) {
            return match.groupValues[1]  // 返回基础版本，如 v1.2.0
        }

        // 无法匹配，返回默认值
        return "v1.0.0"
    }
}
