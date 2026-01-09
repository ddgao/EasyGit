package com.github.easygit.service

import git4idea.repo.GitRepository

/**
 * 仓库信息类
 * 用于在 UI 中展示和选择仓库
 */
data class RepositoryInfo(
    /** 仓库名称 */
    val name: String,

    /** 仓库路径 */
    val path: String,

    /** 当前分支 */
    val currentBranch: String,

    /** Git 仓库对象 */
    val repository: GitRepository?,

    /** 是否选中 */
    var selected: Boolean = true,

    /** 操作状态 */
    var status: String = ""
)
