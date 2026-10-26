package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.PullRequestReviewBarDiffRequestProducer
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer

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
                val chain = SimpleDiffRequestChain.fromProducer(
                    PullRequestReviewBarDiffRequestProducer.wrap(project = project, change = change, delegate = producer)
                )
                DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
                LOG.info("Opened the diff of a changed file of the pull request")
            } catch (exception: Exception) {
                LOG.warn("Failed to open the diff of a changed file", exception)
            }
        }
    }

    private val LOG = logger<PullRequestChangeDiffOpener>()
}