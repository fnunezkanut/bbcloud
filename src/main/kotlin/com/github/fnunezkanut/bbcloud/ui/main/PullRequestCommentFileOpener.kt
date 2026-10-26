package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/** Opens the working tree file that a pull request inline comment points at. */
internal object PullRequestCommentFileOpener {
    /** Null when the path is blank or escapes the project root. */
    fun resolve(project: Project, inlinePath: String): VirtualFile? {
        val relativePath = projectRelativePath(inlinePath = inlinePath) ?: return null
        val basePath = project.basePath ?: return null
        return ReadAction.computeBlocking<VirtualFile?, RuntimeException> {
            val baseFile = LocalFileSystem.getInstance().findFileByPath(basePath)
            baseFile?.findFileByRelativePath(relativePath)
        }.also { file ->
            LOG.debug("Activity comment location '$inlinePath' resolved to ${file?.path ?: "no local file"}")
        }
    }

    /**
     * Opens [file] in an editor tab, placing the caret on [fileLine] when it still exists in the file. Runs on the EDT.
     * The document itself is read under a read action, while the navigation that follows stays on the EDT.
     */
    fun open(project: Project, file: VirtualFile, fileLine: Int?) {
        val document = ReadAction.computeBlocking<Document?, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(file)
        }
        if (document == null) {
            LOG.debug("Activity comment location '${file.path}' has no text document; opening it at the first line")
        }
        val lineCount = document?.lineCount ?: 0
        val targetLine = targetEditorLine(fileLine = fileLine, lineCount = lineCount)
        if (fileLine != null && targetLine == 0 && fileLine != FIRST_LINE) {
            LOG.debug("Activity comment location '${file.path}' no longer has line $fileLine; opening it at the first line")
        }
        OpenFileDescriptor(project, file, targetLine, 0).navigate(true)
    }

    /** Bitbucket paths are repository relative; `..` segments would escape the project root, so they are rejected. */
    internal fun projectRelativePath(inlinePath: String): String? {
        val normalized = PullRequestPathMatcher.normalize(inlinePath)
        if (normalized.isEmpty() || normalized.split('/').contains(PARENT_SEGMENT)) {
            return null
        }
        return normalized
    }

    /** Clamps to the first line so a stale comment line never points past the end of the file. */
    internal fun targetEditorLine(fileLine: Int?, lineCount: Int): Int {
        val editorLine = (fileLine ?: return 0) - 1
        return if (editorLine in 0 until lineCount) editorLine else 0
    }

    private const val PARENT_SEGMENT = ".."
    private const val FIRST_LINE = 1

    private val LOG = logger<PullRequestCommentFileOpener>()
}