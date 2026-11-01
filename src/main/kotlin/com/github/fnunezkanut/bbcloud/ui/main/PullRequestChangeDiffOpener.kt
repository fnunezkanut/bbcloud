package com.github.fnunezkanut.bbcloud.ui.main

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction

/**
 * Opens the platform diff of a changed file, the way the old changes browser did when a file was double clicked.
 */
internal object PullRequestChangeDiffOpener {
    fun open(project: Project, change: Change) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val producer = ChangeDiffRequestProducer.create(project, change)
                if (producer == null) {
                    LOG.info("Cannot open the diff of the selected change: no diff producer is available")
                    return@invokeLater
                }
                // The pull-request bars of the diff are attached by PullRequestDiffBarsDiffExtension, so the producer is used as is.
                val chain = SimpleDiffRequestChain.fromProducer(producer)
                DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
                LOG.info("Opened the diff of a changed file of the pull request")
            } catch (exception: Exception) {
                LOG.warn("Failed to open the diff of a changed file", exception)
            }
        }
    }

    /** Opens the diff chain of all [changes] positioned at [index], so the file navigation of the diff covers the whole pull request. */
    fun openInChain(project: Project, changes: List<Change>, index: Int) {
        ApplicationManager.getApplication().invokeLater {
            try {
                ShowDiffAction.showDiffForChange(project, changes, index)
                LOG.info("Opened the diff chain of ${changes.size} changed file(s) of the pull request at index $index")
            } catch (exception: Exception) {
                LOG.warn("Failed to open the diff chain of the changed files", exception)
            }
        }
    }

    private val LOG = logger<PullRequestChangeDiffOpener>()
}