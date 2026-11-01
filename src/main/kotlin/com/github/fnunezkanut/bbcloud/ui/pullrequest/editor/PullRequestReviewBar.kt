package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestViewedFiles
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Weighted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

        /** How many times the bar looks up the chain of its diff before it gives up. */
        private const val CHAIN_LOOKUP_ATTEMPTS = 10

        /** The pause between two chain lookups. The first lookup can run before the diff tab is registered. */
        private const val CHAIN_LOOKUP_DELAY_MILLIS = 100L

        /**
         * Builds the bar of a file of the pull request.
         *
         * The "Viewed" state comes from the project-level store and is retained across editor and IDE restarts. A
         * stored state is shown only when its revision matches the file at the pull request source revision.
         *
         * The [action] is the action of the [surface]; it is absent when there is nothing to open, and the bar then
         * shows no button at all.
         *
         * The [navigator] reads the chain of a diff, so the bar can offer previous and next unviewed-file buttons. It is
         * null on the normal-file surface, and the bar then shows no navigation buttons.
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
            project: Project,
            navigator: ReviewBarDiffChainNavigator?,
            addCommentTooltip: String? = null
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
                var chainPaths by remember { mutableStateOf<List<String?>?>(null) }
                LaunchedEffect(navigator) {
                    if (navigator == null) {
                        return@LaunchedEffect
                    }
                    repeat(CHAIN_LOOKUP_ATTEMPTS) {
                        val paths = withContext(Dispatchers.EDT) { navigator.chainPaths() }
                        if (paths != null) {
                            chainPaths = paths
                            return@LaunchedEffect
                        }
                        delay(CHAIN_LOOKUP_DELAY_MILLIS)
                    }
                    LOG.debug("Review bar of '$relativePath' found no diff chain, so it offers no previous or next buttons")
                }
                val navigation = navigationStateFor(
                    chainPaths = chainPaths,
                    relativePath = relativePath,
                    pullRequestId = pullRequestId,
                    project = project,
                    navigator = navigator,
                    viewedFiles = viewedFiles
                )
                val currentNavigation by rememberUpdatedState(navigation)
                PullRequestReviewBarContent(
                    viewed = viewed,
                    lineCount = lineCount,
                    surface = surface,
                    action = action,
                    navigation = navigation,
                    onViewedToggled = { checked ->
                        viewedFiles.setViewed(
                            pullRequestId = pullRequestId,
                            relativePath = relativePath,
                            revision = revision,
                            viewed = checked
                        )
                        LOG.info("User set 'Viewed' to $checked for $relativePath in pull request #$pullRequestId")
                        if (checked) {
                            moveToNextUnviewedAfterViewed(
                                surface = surface,
                                navigation = currentNavigation,
                                navigator = navigator,
                                relativePath = relativePath,
                                pullRequestId = pullRequestId
                            )
                        }
                    },
                    onAddComment = onAddComment,
                    addCommentTooltip = addCommentTooltip
                )
            }
            bar.composePanel = panel
            bar.component.add(content, BorderLayout.CENTER)
            return bar
        }

        /**
         * The previous and next navigation of the bar, or null when the bar offers none.
         *
         * The navigation needs the chain of a diff with more than one file, and the file of the bar must be one of
         * the files of that chain. A null [navigator] means the bar is not over a diff.
         */
        private fun navigationStateFor(
            chainPaths: List<String?>?,
            relativePath: String,
            pullRequestId: Long,
            project: Project,
            navigator: ReviewBarDiffChainNavigator?,
            viewedFiles: BbCloudPullRequestViewedFiles
        ): ReviewBarFileNavigationState? {
            if (navigator == null || chainPaths == null || chainPaths.size <= 1) {
                return null
            }
            val currentIndex = chainPaths.indexOf(PullRequestPathMatcher.normalize(relativePath))
            if (currentIndex < 0) {
                return null
            }
            return ReviewBarFileNavigationState(
                paths = chainPaths,
                currentIndex = currentIndex,
                isViewed = { path ->
                    viewedFiles.isViewed(
                        pullRequestId = pullRequestId,
                        relativePath = path,
                        revision = BbCloudWorktreePullRequestContext.getInstance(project).fileRevisionFor(relativePath = path)
                    )
                },
                onNavigate = { index, direction ->
                    LOG.info(
                        "User clicked '${direction.name} unviewed file' for $relativePath in pull request #$pullRequestId " +
                            "(target index $index of ${chainPaths.size})"
                    )
                    navigator.navigateLater(index = index)
                }
            )
        }

        /**
         * Moves a diff to the next unviewed file after the file of the bar was marked as viewed.
         *
         * Only a diff with a navigation moves. When every other file is viewed, the diff stays on the file.
         */
        private fun moveToNextUnviewedAfterViewed(
            surface: ReviewBarSurface,
            navigation: ReviewBarFileNavigationState?,
            navigator: ReviewBarDiffChainNavigator?,
            relativePath: String,
            pullRequestId: Long
        ) {
            if (surface != ReviewBarSurface.Diff || navigation == null || navigator == null) {
                return
            }
            val index = navigation.unviewedTargetIndex(direction = ReviewBarFileNavigationDirection.Next)
            if (index == null) {
                LOG.info("Staying on $relativePath: no unviewed file is left in pull request #$pullRequestId")
                return
            }
            LOG.info(
                "Moving to the next unviewed file (index $index of ${navigation.paths.size}) after $relativePath " +
                    "was marked as viewed in pull request #$pullRequestId"
            )
            navigator.navigateLater(index = index)
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