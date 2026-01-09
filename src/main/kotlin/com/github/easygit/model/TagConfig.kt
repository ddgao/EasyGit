package com.github.easygit.model

/**
 * Tag 类型
 */
enum class TagType(val prefix: String, val displayName: String) {
    NORMAL("", "正常版本"),
    PATCH("-P", "临时需求"),
    HOTFIX("-H", "Hotfix")
}

/**
 * Tag 配置
 */
data class TagConfig(
    val baseVersion: String,      // 基础版本号，如 v1.0.0
    val tagType: TagType,         // Tag 类型
    val description: String = "", // Tag 描述
    val autoPush: Boolean = false // 是否自动推送
) {
    /**
     * 生成完整的 Tag 名称
     * @param sequence 序号（仅用于 PATCH 和 HOTFIX 类型）
     */
    fun generateTagName(sequence: Int = 1): String {
        return when (tagType) {
            TagType.NORMAL -> baseVersion
            TagType.PATCH -> "$baseVersion${tagType.prefix}${String.format("%02d", sequence)}"
            TagType.HOTFIX -> "$baseVersion${tagType.prefix}${String.format("%02d", sequence)}"
        }
    }
}

/**
 * Tag 创建结果
 */
data class TagResult(
    val repositoryName: String,
    val tagName: String,
    val success: Boolean,
    val message: String
)
