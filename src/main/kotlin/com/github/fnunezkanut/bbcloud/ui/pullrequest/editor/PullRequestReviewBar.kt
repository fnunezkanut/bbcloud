package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestViewedFiles
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Weighted
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The review bar the plugin shows at the top of the editor of a file the pull request changes.
 *
 * The bar is one Jewel/Compose tree hosted in a Swing component, so it follows the IDE theme the same way the rest
 * of the plugin UI does.
 *
 * The bar offers the action of the surface it is shown over. Comment threads and the file-scoped composer render in the
 * editor body.
 */
internal class PullRequestReviewBar internal constructor(
    val relativePath: String,
    val pullRequestId: Long,
    val lineCount: FileLineCount?,
    val revision: String?,
    val surface: ReviewBarSurface
) : Disposable {
    private var composePanel: ComposePanel? = null
    private var disposed = false

    /**
     * The Swing component the platform shows at the top of the editor.
     *
     * The panel is the border layout the Compose tree lives in, so the Compose content fills the space the platform
     * gives the bar.
     */
    val component: JComponent = PullRequestReviewBarPanel()

    /** Releases the Compose tree of the bar. */
    @OptIn(ExperimentalComposeUiApi::class)
    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        composePanel?.dispose()
    }

    companion object {
        private val LOG = logger<PullRequestReviewBar>()

        /**
         * Builds the bar of a file of the pull request.
         *
         * The "Viewed" state comes from the project-level store and is retained across editor and IDE restarts. A
         * stored state is shown only when its revision matches the file at the pull request source revision.
         *
         * The [action] is the action of the [surface]; it is absent when there is nothing to open, and the bar then
         * shows no button at all.
         */
        fun create(
            relativePath: String,
            pullRequestId: Long,
            lineCount: FileLineCount?,
            /** The source-branch blob id, or null for a deleted or unknown revision. */
            revision: String?,
            surface: ReviewBarSurface,
            action: ReviewBarAction?,
            onAddComment: () -> Unit,
            project: com.intellij.openapi.project.Project
        ): PullRequestReviewBar {
            val bar = PullRequestReviewBar(
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                lineCount = lineCount,
                revision = revision,
                surface = surface
            )
            val viewedFiles = BbCloudPullRequestViewedFiles.getInstance(project)
            var panel: ComposePanel? = null
            val content = compose(config = { panel = this }) {
                val viewed = viewedFiles.isViewed(
                    pullRequestId = pullRequestId,
                    relativePath = relativePath,
                    revision = revision
                )
                PullRequestReviewBarContent(
                    viewed = viewed,
                    lineCount = lineCount,
                    surface = surface,
                    action = action,
                    onViewedToggled = { checked ->
                        viewedFiles.setViewed(
                            pullRequestId = pullRequestId,
                            relativePath = relativePath,
                            revision = revision,
                            viewed = checked
                        )
                        LOG.info("User set 'Viewed' to $checked for $relativePath in pull request #$pullRequestId")
                    },
                    onAddComment = onAddComment
                )
            }
            bar.composePanel = panel
            bar.component.add(content, BorderLayout.CENTER)
            return bar
        }
    }
}

/**
 * Root panel for [PullRequestReviewBar].
 *
 * A higher [getWeight] than [WorktreePrViewBlueBarPanel] keeps this bar below the pull-request view blue bar when both
 * are visible.
 */
private class PullRequestReviewBarPanel :
    JPanel(BorderLayout()),
    Weighted {
    override fun getWeight(): Double {
        return WEIGHT
    }

    companion object {
        private const val WEIGHT = 1.0
    }
}