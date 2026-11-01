package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.LocalProjectRepositorySlug
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestReviewState
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.review.PullRequestCommentDecorationContext
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications

/** Opens checkout-backed pull request editors and keeps the project review state aligned with editor selection. */
@Service(Service.Level.PROJECT)
class BbCloudPullRequestEditorOpener(private val project: Project) : Disposable {
    private val configurationService = BbCloudConfigurationService.getInstance()

    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    synchronizeSelection(
                        fileManager = event.manager,
                        fallbackToOpenFile = false
                    )
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    val fallbackToOpenFile = BbCloudPullRequestEditorActivation.shouldFallbackToOpenFile(
                        closedFile = file,
                        selectedFile = source.selectedVirtualFile()
                    )
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            synchronizeSelection(
                                fileManager = source,
                                fallbackToOpenFile = fallbackToOpenFile
                            )
                        }
                    }
                }
            }
        )
    }

    /**
     * Opens or focuses the editor identified by the pull request id.
     *
     * The repository slug is taken from [repositorySlug] when it is provided; otherwise the project's checkout folder
     * name is used as a fallback, which may not match the Bitbucket slug when they differ.
     */
    fun open(summary: PullRequestSummary, branchName: String, repositoryWebUrl: String, repositorySlug: String? = null): Boolean {
        val resolvedFile = resolveManagedFile(pullRequestId = summary.id, repositorySlug = repositorySlug)
        if (resolvedFile == null) {
            return false
        }
        val managedFile = resolvedFile.file
        managedFile.setPullRequestSummary(summary)
        managedFile.putUserData(BbCloudPullRequestVirtualFile.REPOSITORY_WEB_URL_KEY, repositoryWebUrl)
        managedFile.putUserData(BbCloudPullRequestVirtualFile.BRANCH_NAME_KEY, branchName)
        managedFile.putUserData(BbCloudPullRequestVirtualFile.ORGANIZATION_URL_KEY, resolvedFile.organizationUrl)
        val fileManager = FileEditorManager.getInstance(project)
        fileManager.openFile(managedFile, true)
        fileManager.updateFilePresentation(managedFile)
        // The platform may select a restored editor after this open call; re-select the PR tab on the next EDT cycle.
        // This focus re-assertion is verified manually in the sandbox IDE because it is a platform interaction.
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            FileEditorManager.getInstance(project).openFile(managedFile, true)
        }
        LOG.debug("Opened pull request editor for #${summary.id}")
        return true
    }

    /**
     * Opens or selects the `<prNum>.pr` editor tab of [pullRequestId] without a Bitbucket summary.
     *
     * When the tab is already open it is selected and [PullRequestPageOpenResult.SelectedExisting] is returned, so the
     * caller can trigger a reload to pick up new activity. Otherwise the tab is opened without a summary and the
     * session's restored-editor path loads the pull request (including its conversation), so the first display already
     * shows the new activity and scrolls to the top; this returns [PullRequestPageOpenResult.OpenedNew]. When the
     * repository metadata is missing or the virtual file cannot be created, [PullRequestPageOpenResult.Failed] is
     * returned.
     */
    fun openById(pullRequestId: Long): PullRequestPageOpenResult {
        val fileManager = FileEditorManager.getInstance(project)
        val alreadyOpen = fileManager.openFiles
            .filterIsInstance<BbCloudPullRequestVirtualFile>()
            .firstOrNull { openFile ->
                openFile.pullRequestPath.pullRequestId == pullRequestId &&
                    openFile.pullRequestPath.projectHash == project.locationHash
            }
        if (alreadyOpen != null) {
            fileManager.openFile(alreadyOpen, true)
            LOG.info("Selected the already-open page of pull request #$pullRequestId")
            return PullRequestPageOpenResult.SelectedExisting
        }
        val resolvedFile = resolveManagedFile(pullRequestId = pullRequestId, repositorySlug = null)
        if (resolvedFile == null) {
            return PullRequestPageOpenResult.Failed
        }
        // The summary is not set here on purpose: the session's restored-editor path fetches the details and binds the
        // page, which loads the conversation with the new comment and forces a scroll to the top.
        fileManager.openFile(resolvedFile.file, true)
        fileManager.updateFilePresentation(resolvedFile.file)
        LOG.info("Opened the page of pull request #$pullRequestId")
        return PullRequestPageOpenResult.OpenedNew
    }

    /**
     * Resolves the managed `<prNum>.pr` virtual file for [pullRequestId], reusing an already-open file when one exists.
     *
     * Returns null, after an `info` log, when the organization URL, project base path, repository slug or workspace
     * URL is missing, or when the virtual file cannot be created.
     */
    private fun resolveManagedFile(pullRequestId: Long, repositorySlug: String?): ResolvedManagedFile? {
        val organizationUrl = configurationService.organizationUrlOrNull()
        val projectBasePath = project.basePath
        val repositorySlugResolved = LocalProjectRepositorySlug.resolve(explicitSlug = repositorySlug, basePath = projectBasePath)
        val workspaceUrl = organizationUrl?.let { url -> BitbucketRepositoryUrls.workspaceFromOrganizationUrl(url) }
        if (organizationUrl == null || projectBasePath == null || repositorySlugResolved == null || workspaceUrl == null) {
            LOG.info("Cannot open pull request #$pullRequestId: project repository metadata is unavailable")
            return null
        }
        val path = BbCloudPullRequestPath(
            sessionId = BbCloudPullRequestVirtualFileSystem.sessionId(project),
            projectHash = project.locationHash,
            workspaceUrl = organizationUrl,
            repositorySlug = repositorySlugResolved,
            pullRequestId = pullRequestId
        )
        val fileSystem = BbCloudPullRequestVirtualFileSystem.getInstance()
        val pathString = fileSystem.getPath(path)
        val file = fileSystem.refreshAndFindFileByPath(pathString) as? BbCloudPullRequestVirtualFile
        if (file == null) {
            LOG.info("Cannot open pull request #$pullRequestId: the editor virtual file was not created")
            return null
        }
        val managedFile = FileEditorManager.getInstance(project).openFiles
            .firstOrNull { openFile -> openFile == file } as? BbCloudPullRequestVirtualFile ?: file
        return ResolvedManagedFile(file = managedFile, organizationUrl = organizationUrl)
    }

    /** The managed virtual file of a pull request page, together with the organization URL it was resolved against. */
    private data class ResolvedManagedFile(val file: BbCloudPullRequestVirtualFile, val organizationUrl: String)

    private fun synchronizeSelection(fileManager: FileEditorManager, fallbackToOpenFile: Boolean) {
        val openFiles = fileManager.openFiles.filterIsInstance<BbCloudPullRequestVirtualFile>()
        val selectedFile = fileManager.selectedVirtualFile() as? BbCloudPullRequestVirtualFile
        val pullRequestId = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = selectedFile,
            openFiles = openFiles,
            fallbackToOpenFile = fallbackToOpenFile
        )
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        val previousPullRequestId = reviewState.activePullRequestId
        if (pullRequestId == null) {
            val keepAliveId = PullRequestCommentDecorationContext.openPullRequestTabId(project = project)
                ?: BbCloudWorktreePullRequestContext.getInstance(project).binding?.pullRequestId
            if (keepAliveId != null) {
                PullRequestCommentDecorationContext.syncInlineIndexFromWorktree(project = project, pullRequestId = keepAliveId)
                BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
                if (previousPullRequestId != keepAliveId) {
                    refreshEditorNotifications()
                }
                return
            }
            if (previousPullRequestId != null) {
                reviewState.clear()
                BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
                refreshEditorNotifications()
            }
            return
        }
        if (previousPullRequestId != pullRequestId) {
            reviewState.bindPullRequest(pullRequestId)
            PullRequestCommentDecorationContext.syncInlineIndexFromWorktree(project = project, pullRequestId = pullRequestId)
            BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
            refreshEditorNotifications()
        }
    }

    /**
     * Rebuilds the editor notifications of the project, so the review bar of the editor follows the active pull
     * request.
     */
    private fun refreshEditorNotifications() {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    override fun dispose() {
        // The message-bus connection is disposed with this project service.
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestEditorOpener>()
    }
}

/**
 * Returns the file of the selected editor. Used instead of `FileEditorManager.getCurrentFile()`, which is
 * `@ApiStatus.Experimental`.
 */
internal fun FileEditorManager.selectedVirtualFile(): VirtualFile? {
    return selectedEditor?.file
}

object BbCloudPullRequestEditorActivation {
    fun activePullRequestId(selectedFile: BbCloudPullRequestVirtualFile?, openFiles: List<BbCloudPullRequestVirtualFile>, fallbackToOpenFile: Boolean): Long? {
        val selectedOpenFile = selectedFile?.takeIf { candidate -> openFiles.any { openFile -> openFile === candidate } }
        val activeFile = selectedOpenFile ?: if (fallbackToOpenFile) openFiles.firstOrNull() else null
        return activeFile?.pullRequestPath?.pullRequestId
    }

    fun activateSelectedPullRequest(
        selectedFile: BbCloudPullRequestVirtualFile?,
        file: BbCloudPullRequestVirtualFile,
        reviewState: BbCloudPullRequestReviewState,
        onReviewStateChanged: () -> Unit
    ): Boolean {
        if (selectedFile !== file) {
            return false
        }
        val pullRequestId = file.pullRequestPath.pullRequestId
        if (reviewState.activePullRequestId != pullRequestId) {
            reviewState.bindPullRequest(pullRequestId)
            onReviewStateChanged()
        }
        return true
    }

    fun shouldFallbackToOpenFile(closedFile: VirtualFile, selectedFile: VirtualFile?): Boolean {
        return closedFile is BbCloudPullRequestVirtualFile && selectedFile === closedFile
    }
}