package com.github.easygit.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File

/**
 * 仓库服务类
 * 提供仓库查找和管理功能
 */
@Service(Service.Level.PROJECT)
class RepositoryService(private val project: Project) {

    /**
     * 获取所有 Git 仓库信息
     */
    fun getAllRepositories(): List<RepositoryInfo> {
        val repoManager = GitRepositoryManager.getInstance(project)
        return repoManager.repositories.map { repo ->
            RepositoryInfo(
                name = repo.root.name,
                path = repo.root.path,
                currentBranch = repo.currentBranch?.name ?: "unknown",
                repository = repo,
                selected = true
            )
        }
    }

    /**
     * 根据路径列表加载仓库
     */
    fun loadRepositories(paths: List<String>): List<GitRepository> {
        val repoManager = GitRepositoryManager.getInstance(project)
        val fileSystem = LocalFileSystem.getInstance()

        return paths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isDirectory) {
                val virtualFile = fileSystem.findFileByPath(path)
                virtualFile?.let { vf ->
                    repoManager.getRepositoryForRoot(vf)
                        ?: repoManager.repositories.find { it.root.path == path }
                }
            } else {
                null
            }
        }
    }

    /**
     * 获取项目中的所有 Git 仓库
     */
    fun getProjectRepositories(): List<GitRepository> {
        val repoManager = GitRepositoryManager.getInstance(project)
        return repoManager.repositories
    }

    /**
     * 根据路径列表获取仓库信息
     */
    fun getRepositoriesByPaths(paths: List<String>): List<RepositoryInfo> {
        val repoManager = GitRepositoryManager.getInstance(project)
        return paths.mapNotNull { path ->
            repoManager.repositories.find { it.root.path == path }?.let { repo ->
                RepositoryInfo(
                    name = repo.root.name,
                    path = repo.root.path,
                    currentBranch = repo.currentBranch?.name ?: "unknown",
                    repository = repo,
                    selected = true
                )
            }
        }
    }

    fun getRepositoryForFile(file: VirtualFile): GitRepository? {
        val repoManager = GitRepositoryManager.getInstance(project)
        return repoManager.getRepositoryForFile(file)
            ?: repoManager.getRepositoryForRoot(file)
    }

    companion object {
        fun getInstance(project: Project): RepositoryService {
            return project.getService(RepositoryService::class.java)
        }
    }
}
