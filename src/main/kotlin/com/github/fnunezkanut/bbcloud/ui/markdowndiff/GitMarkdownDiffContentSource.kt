package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils

/**
 * Compares a Markdown file that git tracks across the last commit (`HEAD`), the index (staged), and the working tree.
 *
 * The working tree side reads the open document, so unsaved edits are included.
 */
class GitMarkdownDiffContentSource(private val project: Project, private val file: VirtualFile) : MarkdownDiffContentSource {
    override val key: String = "git:${file.url}"

    override val fileName: String = file.name

    override val comparisons: List<MarkdownDiffComparison> = GitComparison.entries.map { comparison ->
        MarkdownDiffComparison(id = comparison.id, label = BbCloudBundle.message(comparison.labelKey))
    }

    override fun load(comparison: MarkdownDiffComparison): MarkdownDiffContentResult {
        val gitComparison = GitComparison.entries.firstOrNull { entry -> entry.id == comparison.id }
            ?: return MarkdownDiffContentResult.Failed(message = BbCloudBundle.message("markdownDiff.error.unknownComparison"))
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file)
            ?: return MarkdownDiffContentResult.Failed(message = BbCloudBundle.message("markdownDiff.error.notInRepository"))
        val relativePath = VfsUtilCore.getRelativePath(file, repository.root)
            ?: return MarkdownDiffContentResult.Failed(message = BbCloudBundle.message("markdownDiff.error.notInRepository"))
        return try {
            MarkdownDiffContentResult.Loaded(
                left = side(repository = repository, relativePath = relativePath, revision = gitComparison.left),
                right = side(repository = repository, relativePath = relativePath, revision = gitComparison.right)
            )
        } catch (exception: VcsException) {
            LOG.debug("Could not read a git revision of $relativePath", exception)
            MarkdownDiffContentResult.Failed(message = BbCloudBundle.message("markdownDiff.error.gitFailed", exception.message.orEmpty()))
        }
    }

    private fun side(repository: GitRepository, relativePath: String, revision: GitRevision): MarkdownDiffSide {
        val label = BbCloudBundle.message(revision.labelKey)
        val text = when (revision) {
            GitRevision.HEAD -> readRevision(repository = repository, relativePath = relativePath, revision = HEAD_REVISION)
            GitRevision.INDEX -> readRevision(repository = repository, relativePath = relativePath, revision = INDEX_REVISION)
            GitRevision.WORKING_TREE -> readWorkingTree()
        }
        return MarkdownDiffSide(label = label, text = text)
    }

    /**
     * Reads `<revision>:<path>` with `git show`. A path that the revision does not have reads as an empty document, so
     * a new file shows as fully added.
     */
    private fun readRevision(repository: GitRepository, relativePath: String, revision: String): String {
        if (revision == HEAD_REVISION && repository.currentRevision == null) {
            return ""
        }
        return try {
            GitFileUtils.getFileContent(project, repository.root, revision, relativePath).toString(file.charset)
        } catch (exception: VcsException) {
            if (isMissingPath(exception = exception)) {
                LOG.debug("$relativePath does not exist at '$revision'; comparing against an empty document")
                ""
            } else {
                throw exception
            }
        }
    }

    private fun readWorkingTree(): String {
        return ReadAction.computeBlocking<String, RuntimeException> {
            val document = FileDocumentManager.getInstance().getDocument(file)
            document?.text ?: VfsUtilCore.loadText(file)
        }
    }

    private fun isMissingPath(exception: VcsException): Boolean {
        val message = exception.message.orEmpty()
        return MISSING_PATH_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
    }

    private enum class GitRevision(val labelKey: String) {
        HEAD("markdownDiff.side.head"),
        INDEX("markdownDiff.side.staged"),
        WORKING_TREE("markdownDiff.side.workingTree")
    }

    private enum class GitComparison(val id: String, val labelKey: String, val left: GitRevision, val right: GitRevision) {
        HEAD_VS_WORKING_TREE("head-working", "markdownDiff.comparison.headWorkingTree", GitRevision.HEAD, GitRevision.WORKING_TREE),
        HEAD_VS_STAGED("head-staged", "markdownDiff.comparison.headStaged", GitRevision.HEAD, GitRevision.INDEX),
        STAGED_VS_WORKING_TREE("staged-working", "markdownDiff.comparison.stagedWorkingTree", GitRevision.INDEX, GitRevision.WORKING_TREE)
    }

    companion object {
        private val LOG = logger<GitMarkdownDiffContentSource>()
        private const val HEAD_REVISION = "HEAD"

        /** `git show :<path>` reads the staged version of the path. */
        private const val INDEX_REVISION = ""
        private val MISSING_PATH_MARKERS = listOf("does not exist", "exists on disk, but not in", "invalid object name", "bad revision")
    }
}