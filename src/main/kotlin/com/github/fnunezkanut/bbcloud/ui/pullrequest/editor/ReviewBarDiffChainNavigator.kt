package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the files of the diff chain that shows a review bar and moves the chain to another of them.
 *
 * The chain is found through the [diffContext] of the bar, which the platform shares with the processor that owns the
 * chain. When no processor has that context, the [viewer] of the bar identifies the processor instead. The lookup runs
 * each time it is needed, because the processor and its editor tab exist only after the first viewer is created.
 */
internal class ReviewBarDiffChainNavigator(private val project: Project, private val diffContext: DiffContext, private val viewer: FrameDiffTool.DiffViewer?) {
    /** True while a navigation scheduled by [navigateLater] has not run yet. */
    private val navigationPending = AtomicBoolean(false)

    /**
     * The relative path of every request of the chain, in chain order, or null when the chain is not found.
     *
     * A request that is not a change of the pull request has a null path, so the indices stay aligned with the chain.
     */
    @RequiresEdt
    fun chainPaths(): List<String?>? {
        val processor = findProcessor() ?: return null
        return processor.requestChain.requests.map { producer -> relativePathOf(producer) }
    }

    /**
     * Moves the chain to the request at [index] after the current event has finished.
     *
     * Moving the chain replaces the viewer, which disposes the review bar that made the call. A bar click or a checkbox
     * change must therefore not move the chain while its own event is still running. A call made while an earlier
     * navigation is pending is ignored.
     */
    fun navigateLater(index: Int) {
        if (!navigationPending.compareAndSet(false, true)) {
            LOG.debug("Skipped the move to file index $index: an earlier move of the review bar is pending")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            try {
                if (project.isDisposed) {
                    LOG.info("Cannot move to file index $index: the project is disposed")
                    return@invokeLater
                }
                if (!navigateTo(index)) {
                    LOG.info("Cannot move to file index $index: the diff chain is no longer open")
                }
            } finally {
                navigationPending.set(false)
            }
        }
    }

    /** Moves the chain to the request at [index]. Returns false when the chain is gone or the index is out of range. */
    @RequiresEdt
    fun navigateTo(index: Int): Boolean {
        val processor = findProcessor() ?: return false
        if (index !in processor.requestChain.requests.indices) {
            return false
        }
        processor.setCurrentRequest(index)
        return true
    }

    @RequiresEdt
    private fun findProcessor(): CacheDiffRequestChainProcessor? {
        val processors = FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<DiffEditorViewerFileEditor>()
            .mapNotNull { editor -> editor.editorViewer as? CacheDiffRequestChainProcessor }
        val processor = processors.firstOrNull { candidate -> candidate.context === diffContext }
            ?: viewer?.let { currentViewer ->
                processors.firstOrNull { candidate -> candidate.activeViewer === currentViewer }
            }
        if (processor == null) {
            LOG.debug("No diff chain processor found for the review bar, so the diff is probably shown in a separate window")
        }
        return processor
    }

    private fun relativePathOf(producer: DiffRequestProducer): String? {
        if (producer !is ChangeDiffRequestProducer) {
            return null
        }
        val absolutePath = ChangesUtil.getFilePath(producer.change).path
        return PullRequestPathMatcher.normalize(
            PullRequestPathMatcher.relativeProjectPath(
                projectBasePath = project.basePath,
                absoluteOrRelativePath = absolutePath
            )
        )
    }

    companion object {
        private val LOG = logger<ReviewBarDiffChainNavigator>()
    }
}