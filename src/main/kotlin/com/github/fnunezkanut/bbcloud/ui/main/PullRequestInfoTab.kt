@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

@Composable
internal fun PullRequestInfoTab(
    viewModel: PullRequestInfoViewModel,
    configurationService: BbCloudConfigurationService,
    coroutineScope: CoroutineScope,
    actions: List<PullRequestAction>,
    actionsEnabled: Boolean,
    disabledActions: Set<PullRequestAction>,
    actionTooltips: Map<PullRequestAction, String>,
    onAction: (PullRequestAction) -> Unit
) {
    val state by viewModel.state
    val row = state.row ?: return
    if (!state.hasContent) {
        return
    }
    val pullRequest = row.pullRequest
    val summary = pullRequest.summary
    PullRequestInfoCard(
        row = row,
        avatarRevision = state.avatarRevision,
        avatarIconFor = viewModel::avatarIconFor,
        actions = PullRequestCardActions(
            onOpenTitle = {
                LOG.info("User opened the title for pull request #${summary.id} in the browser")
                browseIfAvailable(summary.webUrl, summary.id, "pull request")
            },
            onOpenPrOnWeb = {
                LOG.info("User opened pull request #${summary.id} in the browser")
                browseIfAvailable(summary.webUrl, summary.id, "pull request")
            },
            // The detail card opens the repository locally with a web fallback; listing tables keep their own behavior.
            onOpenRepository = {
                openRepositoryLocally(
                    row = row,
                    configurationService = configurationService,
                    coroutineScope = coroutineScope
                )
            }
        ),
        // The action buttons live in the trailing column of the info card, and only once the action list is populated.
        trailingContent = if (actions.isNotEmpty()) {
            {
                PullRequestActionsRow(
                    actions = actions,
                    enabled = actionsEnabled,
                    disabledActions = disabledActions,
                    actionTooltips = actionTooltips,
                    onAction = onAction
                )
            }
        } else {
            null
        }
    )
}

private fun openRepositoryLocally(row: WorkspacePullRequestRow, configurationService: BbCloudConfigurationService, coroutineScope: CoroutineScope) {
    val pullRequestId = row.pullRequest.summary.id
    val repositoryUrl = row.repositoryWebUrl
    val repoSlug = row.pullRequest.repoSlug.takeIf { it.isNotBlank() } ?: row.repoSlug
    if (repoSlug.isBlank()) {
        LOG.info("Cannot open the repository for pull request #$pullRequestId locally: repository slug is unavailable")
        browseRepositoryIfAvailable(repositoryUrl, pullRequestId)
        return
    }

    val localPath = configurationService.localCheckoutPathForRepo(repoSlug)
    when (val decision = repositoryOpenDecision(repoSlug = repoSlug, localPath = localPath, repositoryWebUrl = repositoryUrl)) {
        is RepositoryOpenDecision.BrowseWeb -> {
            LOG.info("No local checkout for $repoSlug; opening the repository for pull request #$pullRequestId on the web")
            browseRepositoryIfAvailable(decision.url, pullRequestId)
        }
        is RepositoryOpenDecision.OpenLocal -> {
            coroutineScope.launch {
                val opened = LocalRepositoryProjectOpener.openOrFocus(
                    repoSlug = repoSlug,
                    checkoutPath = Path.of(decision.path)
                )
                if (opened != null) {
                    LOG.info("Opened local project $repoSlug from the pull request header")
                } else {
                    LOG.warn("Could not open local project $repoSlug from the pull request header; falling back to the Bitbucket web page")
                    ApplicationManager.getApplication().invokeLater {
                        browseRepositoryIfAvailable(repositoryUrl, pullRequestId)
                    }
                }
            }
        }
    }
}

internal sealed interface RepositoryOpenDecision {
    data class OpenLocal(val path: String) : RepositoryOpenDecision

    data class BrowseWeb(val url: String?) : RepositoryOpenDecision
}

internal fun repositoryOpenDecision(repoSlug: String, localPath: String, repositoryWebUrl: String?): RepositoryOpenDecision {
    return if (repoSlug.isBlank() || localPath.isBlank()) {
        RepositoryOpenDecision.BrowseWeb(url = repositoryWebUrl)
    } else {
        RepositoryOpenDecision.OpenLocal(path = localPath)
    }
}

private fun browseRepositoryIfAvailable(url: String?, pullRequestId: Long) {
    if (url.isNullOrBlank()) {
        LOG.info("Repository link unavailable for pull request #$pullRequestId")
        return
    }
    BrowserUtil.browse(url)
}

private fun browseIfAvailable(url: String, pullRequestId: Long, target: String) {
    if (url.isBlank()) {
        LOG.info("Cannot open $target for pull request #$pullRequestId: URL is unavailable")
        return
    }
    BrowserUtil.browse(url)
}

private val LOG = logger<PullRequestInfoViewModel>()