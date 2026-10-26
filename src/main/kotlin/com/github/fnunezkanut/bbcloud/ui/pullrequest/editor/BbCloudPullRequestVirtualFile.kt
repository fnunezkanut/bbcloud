package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.testFramework.LightVirtualFile

class BbCloudPullRequestVirtualFile internal constructor(
    val project: Project,
    val pullRequestPath: BbCloudPullRequestPath,
    private val fileSystem: BbCloudPullRequestVirtualFileSystem
) : LightVirtualFile("${pullRequestPath.pullRequestId}.pr"),
    VirtualFilePathWrapper {
    init {
        putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun getFileSystem(): BbCloudPullRequestVirtualFileSystem {
        return fileSystem
    }

    override fun getFileType(): FileType {
        return BbCloudPullRequestFileType
    }

    override fun getPath(): String {
        return fileSystem.getPath(pullRequestPath)
    }

    override fun getName(): String {
        return "${pullRequestPath.pullRequestId}.pr"
    }

    override fun isWritable(): Boolean {
        return false
    }

    override fun isValid(): Boolean {
        return project.isOpen && !project.isDisposed
    }

    /**
     * Returns a human-readable path for the window title instead of the serialized JSON path.
     * This is used by [ComplexPathVirtualFileSystem.extractPresentableUrl] when the file
     * implements [VirtualFilePathWrapper]. The window title (and macOS Dock hover) shows
     * this value instead of the raw serialized path.
     */
    override fun getPresentablePath(): String = presentablePath()

    /**
     * Returns [false] so that [getPresentableName()] continues to return [getName()].
     * When [true], [ProjectUtil.calcRelativeToProjectPath] would use the presentable path
     * for project-relative paths, which is not desired here.
     */
    override fun enforcePresentableName(): Boolean = false

    private fun presentablePath(): String {
        return "${pullRequestPath.pullRequestId}.pr"
    }

    fun setPullRequestSummary(summary: PullRequestSummary) {
        putUserData(PULL_REQUEST_SUMMARY_KEY, summary)
        putUserData(PULL_REQUEST_TITLE_KEY, sanitizeTitle(summary.title))
    }

    fun setPullRequestTitle(title: String) {
        putUserData(PULL_REQUEST_TITLE_KEY, sanitizeTitle(title))
    }

    fun pullRequestTitle(): String? {
        return getUserData(PULL_REQUEST_TITLE_KEY)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BbCloudPullRequestVirtualFile) {
            return false
        }
        return project === other.project && pullRequestPath.identity() == other.pullRequestPath.identity()
    }

    override fun hashCode(): Int {
        return 31 * System.identityHashCode(project) + pullRequestPath.identity().hashCode()
    }

    private fun BbCloudPullRequestPath.identity(): List<Any> {
        return listOf(
            workspaceUrl,
            repositorySlug,
            pullRequestId
        )
    }

    companion object {
        val PULL_REQUEST_SUMMARY_KEY: Key<PullRequestSummary> = Key.create("bbcloud.pullRequest.summary")
        val PULL_REQUEST_TITLE_KEY: Key<String> = Key.create("bbcloud.pullRequest.title")
        val REPOSITORY_WEB_URL_KEY: Key<String> = Key.create("bbcloud.pullRequest.repositoryWebUrl")
        val BRANCH_NAME_KEY: Key<String> = Key.create("bbcloud.pullRequest.branchName")
        val ORGANIZATION_URL_KEY: Key<String> = Key.create("bbcloud.pullRequest.organizationUrl")

        fun sanitizeTitle(title: String): String {
            return StringUtil.collapseWhiteSpace(StringUtil.stripHtml(title, " ")).trim()
        }
    }
}