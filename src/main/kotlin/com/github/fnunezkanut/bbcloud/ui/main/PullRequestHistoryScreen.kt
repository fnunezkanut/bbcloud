@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.BuildState
import com.github.fnunezkanut.bbcloud.bitbucket.CommentEmojiResolver
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestBuildStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCommit
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestTask
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.DateFormatUtil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Badge
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val HISTORY_AVATAR_SIZE = 24.dp
private val HISTORY_GUTTER_WIDTH = 28.dp
private val HISTORY_GUTTER_DIAMETER = 28.dp
private val HISTORY_GUTTER_ICON_SIZE = 16.dp
private val HISTORY_GUTTER_LINE_WIDTH = 2.dp
private val HISTORY_ENTRY_GAP = 16.dp
private const val HISTORY_SHORT_HASH_LENGTH = 7

/** The gap between the "Activity" title, the top-level composer, and the first notice or entry. */
private val ACTIVITY_COMPOSER_GAP = 12.dp

/**
 * The Jewel Activity timeline of the pull-request page. The card title, the top-level comment composer, and then the
 * activity entries and the comment threads of each day, interleaved by time. It renders the same information as the old
 * Swing activity feed section, plus the comment threads, and the partial-failure and truncation notices.
 */
@Composable
internal fun PullRequestActivityFeedScreen(
    historyViewModel: PullRequestHistoryViewModel,
    conversationViewModel: PullRequestConversationViewModel,
    modifier: Modifier = Modifier
) {
    val state by historyViewModel.state
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = state.title,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        TopLevelCommentComposer(conversationViewModel = conversationViewModel)
        ActivityBody(state = state, historyViewModel = historyViewModel, conversationViewModel = conversationViewModel)
    }
}

/** The composer of a new top-level comment, always above every notice and timeline entry. */
@Composable
private fun TopLevelCommentComposer(conversationViewModel: PullRequestConversationViewModel) {
    val conversationState by conversationViewModel.state
    val pullRequestId = conversationViewModel.currentPullRequestId()
    val composerViewModel = remember(pullRequestId) { conversationViewModel.newTopLevelComposer() }
    CommentComposerScreen(
        viewModel = composerViewModel,
        avatarIcon = conversationViewModel.avatarIconFor(conversationState.currentUserAvatarUrl),
        modifier = Modifier.padding(bottom = ACTIVITY_COMPOSER_GAP),
        showsDottedBorder = true
    )
}

/** The part of the card below the composer: a loading or error message, or the notices and the timeline. */
@Composable
private fun ActivityBody(
    state: PullRequestHistoryUiState,
    historyViewModel: PullRequestHistoryViewModel,
    conversationViewModel: PullRequestConversationViewModel
) {
    when (state.loadState) {
        PullRequestHistoryLoadState.Loading -> ActivityMessage(BbCloudBundle.message("main.branch.history.loading"))
        PullRequestHistoryLoadState.Error -> ActivityMessage(BbCloudBundle.message("main.branch.history.error"))
        PullRequestHistoryLoadState.Loaded -> ActivityTimeline(
            state = state,
            historyViewModel = historyViewModel,
            conversationViewModel = conversationViewModel
        )
    }
}

@Composable
private fun ActivityTimeline(
    state: PullRequestHistoryUiState,
    historyViewModel: PullRequestHistoryViewModel,
    conversationViewModel: PullRequestConversationViewModel
) {
    if (state.showPartialFailure) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(key = AllIconsKeys.General.BalloonWarning, contentDescription = null)
            Text(
                text = BbCloudBundle.message("main.branch.history.partialFailure"),
                color = JewelTheme.globalColors.text.info
            )
        }
    }
    state.groups.forEachIndexed { groupIndex, group ->
        Text(
            text = historyDayHeaderText(group.day),
            fontWeight = FontWeight.Bold,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        val isLastGroup = groupIndex == state.groups.lastIndex
        activityRows(group = group, isLastGroup = isLastGroup).forEach { row ->
            when (row) {
                is ActivityRow.Activity -> HistoryEntryRow(
                    entry = row.entry,
                    isLast = row.isLast,
                    avatarRevision = state.avatarRevision,
                    viewModel = historyViewModel
                )
                // The key keeps an open reply or edit composer alive when a refresh inserts rows above it.
                is ActivityRow.Thread -> key(row.thread.root.id) {
                    HistoryThreadRow(
                        thread = row.thread,
                        isLast = row.isLast,
                        host = conversationViewModel
                    )
                }
            }
        }
    }
    if (state.showTruncation) {
        Text(
            text = BbCloudBundle.message("main.branch.history.truncated"),
            color = JewelTheme.globalColors.text.info
        )
    }
}

@Composable
private fun ActivityMessage(text: String) {
    Text(text = text, color = JewelTheme.globalColors.text.info)
}

@Composable
private fun HistoryEntryRow(entry: ActivityFeedEntry, isLast: Boolean, avatarRevision: Int, viewModel: PullRequestHistoryViewModel) {
    HistoryTimelineRow(
        isLast = isLast,
        icon = activityEntryIcon(entry),
        iconTint = activityEntryIconTint(entry) ?: Color.Unspecified
    ) {
        if (entry !is TimelineEntry.BuildUpdated) {
            val avatarUrl = entry.actor?.avatarUrl
            val bitmap = remember(avatarUrl, avatarRevision) {
                viewModel.avatarIconFor(avatarUrl).toComposeImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = "Author avatar",
                modifier = Modifier.size(HISTORY_AVATAR_SIZE),
                filterQuality = FilterQuality.High
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            HistoryHeadline(entry = entry, viewModel = viewModel)
            HistoryBody(entry = entry, viewModel = viewModel)
        }
    }
}

/** A comment thread as a timeline row: the same gutter as the activity entries, with the thread card as its content. */
@Composable
private fun HistoryThreadRow(thread: TimelineEntry.CommentThread, isLast: Boolean, host: CommentThreadCardHost) {
    HistoryTimelineRow(
        isLast = isLast,
        icon = AllIconsKeys.General.Balloon,
        iconTint = Color.Unspecified
    ) {
        // The thread card shows the author avatar, name, time and location itself, so no headline is drawn here.
        PullRequestCommentThreadCard(
            thread = thread,
            host = host,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One row of the timeline: the gutter with the entry icon, then the [content] beside it. Activity entries and comment
 * threads share this layout, so their gutters line up.
 */
@Composable
private fun HistoryTimelineRow(isLast: Boolean, icon: IconKey, iconTint: Color, content: @Composable RowScope.() -> Unit) {
    val gutterLineColor = JewelTheme.globalColors.borders.normal
    val gutterCircleColor = JewelTheme.globalColors.panelBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawHistoryGutter(
                    isLast = isLast,
                    lineColor = gutterLineColor,
                    circleColor = gutterCircleColor
                )
            }
            .padding(bottom = HISTORY_ENTRY_GAP)
    ) {
        Box(
            modifier = Modifier.width(HISTORY_GUTTER_WIDTH),
            contentAlignment = Alignment.TopCenter
        ) {
            Icon(
                key = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = (HISTORY_GUTTER_DIAMETER - HISTORY_GUTTER_ICON_SIZE) / 2)
                    .size(HISTORY_GUTTER_ICON_SIZE)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.Top,
            content = content
        )
    }
}

/**
 * Draws the connecting line and the circle that carries the entry icon; [isLast] stops the line at the circle.
 * The line is drawn behind the icon, so the circle keeps the icon readable.
 */
private fun DrawScope.drawHistoryGutter(isLast: Boolean, lineColor: Color, circleColor: Color) {
    val diameter = HISTORY_GUTTER_DIAMETER.toPx()
    val lineWidth = HISTORY_GUTTER_LINE_WIDTH.toPx()
    // The gutter sits at the start of the row, so the line is drawn at the center of the gutter column.
    val centerX = HISTORY_GUTTER_WIDTH.toPx() / 2f
    val centerY = diameter / 2f
    if (!isLast) {
        drawRect(
            color = lineColor,
            topLeft = Offset(centerX - lineWidth / 2f, centerY),
            size = Size(lineWidth, (size.height - centerY).coerceAtLeast(0f))
        )
    }
    drawCircle(color = circleColor, radius = diameter / 2f, center = Offset(centerX, centerY))
    drawCircle(
        color = lineColor,
        radius = diameter / 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = lineWidth / 2f)
    )
}

@Composable
private fun HistoryHeadline(entry: ActivityFeedEntry, viewModel: PullRequestHistoryViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (entry) {
            is TimelineEntry.Opened -> {
                HistoryActorName(entry.actor)
                HistoryStatePill(
                    text = BbCloudBundle.message("main.branch.history.pill.opened"),
                    state = PullRequestState.Open
                )
                Text(text = BbCloudBundle.message("main.branch.history.action.pullRequest"))
            }
            is TimelineEntry.StateChanged -> {
                HistoryActorName(entry.actor)
                HistoryStatePill(text = historyStatePillText(entry.state), state = entry.state)
                Text(text = BbCloudBundle.message("main.branch.history.action.pullRequest"))
            }
            is TimelineEntry.Approved -> {
                HistoryActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.history.action.approved"))
            }
            is TimelineEntry.TaskCreated -> {
                HistoryActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.history.action.taskCreated"))
            }
            is TimelineEntry.TaskResolved -> {
                HistoryActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.history.action.taskResolved"))
            }
            is TimelineEntry.CommitsPushed -> {
                HistoryActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.history.action.commitsPushed", entry.commits.size))
            }
            is TimelineEntry.BuildUpdated -> {
                // The weight lets a long build name wrap instead of pushing the state text and the time into a narrow column.
                Tooltip(
                    tooltip = { Text(text = entry.status.name) },
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Link(
                        text = entry.status.name,
                        onClick = { openBuild(status = entry.status, viewModel = viewModel) }
                    )
                }
                Text(
                    text = activityBuildStateText(entry.status.state),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Text(
            text = PullRequestRelativeTime.format(entry.date),
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun HistoryBody(entry: ActivityFeedEntry, viewModel: PullRequestHistoryViewModel) {
    when (entry) {
        is TimelineEntry.TaskCreated -> {
            HistoryTaskCheckBox(task = entry.task)
        }
        is TimelineEntry.TaskResolved -> {
            HistoryTaskCheckBox(task = entry.task)
        }
        is TimelineEntry.CommitsPushed -> {
            HistoryCommitsBody(commits = entry.commits, viewModel = viewModel)
        }
        is TimelineEntry.BuildUpdated -> {
            entry.status.description?.let { description ->
                Text(text = description, color = JewelTheme.globalColors.text.info)
            }
        }
        is TimelineEntry.Opened, is TimelineEntry.StateChanged, is TimelineEntry.Approved -> {
            // These entries have no body.
        }
    }
}

@Composable
private fun RowScope.HistoryActorName(actor: ActivityUser?) {
    Text(
        text = actor?.displayName ?: BbCloudBundle.message("main.branch.history.unknownUser"),
        fontWeight = FontWeight.Bold,
        // The weight lets a long name shrink instead of pushing the pill, the action and the time out of the row.
        modifier = Modifier.weight(1f, fill = false),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )
}

@Composable
private fun HistoryStatePill(text: String, state: PullRequestState) {
    Badge(style = PullRequestBadgeStyles.state(state)) {
        Text(text = text)
    }
}

@Composable
private fun HistoryTaskCheckBox(task: PullRequestTask) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.isResolved, onCheckedChange = {}, enabled = false)
        SelectableText(text = remember(task.content) { CommentEmojiResolver.resolveEmoji(body = task.content) })
    }
}

@Composable
private fun HistoryCommitsBody(commits: List<PullRequestCommit>, viewModel: PullRequestHistoryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        commits.forEach { commit -> HistoryCommitRow(commit = commit, viewModel = viewModel) }
    }
}

@Composable
private fun HistoryCommitRow(commit: PullRequestCommit, viewModel: PullRequestHistoryViewModel) {
    val shortHash = commit.hash.take(HISTORY_SHORT_HASH_LENGTH)
    val url = commit.url
    val hashTextStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (url != null) {
            Link(
                text = shortHash,
                onClick = { openCommit(commit = commit, viewModel = viewModel) },
                textStyle = hashTextStyle
            )
        } else {
            SelectableText(text = shortHash, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = commit.message.lineSequence().firstOrNull().orEmpty(),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

internal fun historyDayHeaderText(day: HistoryDay): String {
    return when (day) {
        HistoryDay.Today -> BbCloudBundle.message("main.branch.history.day.today")
        HistoryDay.Yesterday -> BbCloudBundle.message("main.branch.history.day.yesterday")
        is HistoryDay.Earlier -> activityFormatDate(day.date)
    }
}

private fun activityFormatDate(date: LocalDate): String {
    return DateFormatUtil.formatDate(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds())
}

private fun historyStatePillText(state: PullRequestState): String {
    return when (state) {
        PullRequestState.Open -> BbCloudBundle.message("main.branch.history.pill.opened")
        PullRequestState.Merged -> BbCloudBundle.message("main.branch.history.pill.merged")
        PullRequestState.Declined -> BbCloudBundle.message("main.branch.history.pill.declined")
    }
}

private fun activityBuildStateText(state: BuildState): String {
    return when (state) {
        BuildState.Successful -> BbCloudBundle.message("main.branch.history.build.successful")
        BuildState.Failed -> BbCloudBundle.message("main.branch.history.build.failed")
        BuildState.InProgress -> BbCloudBundle.message("main.branch.history.build.inProgress")
        BuildState.Stopped -> BbCloudBundle.message("main.branch.history.build.stopped")
    }
}

private fun activityEntryIcon(entry: ActivityFeedEntry): IconKey {
    return when (entry) {
        is TimelineEntry.Opened -> AllIconsKeys.Vcs.Branch
        is TimelineEntry.StateChanged -> activityStateIcon(entry.state)
        is TimelineEntry.Approved -> AllIconsKeys.RunConfigurations.TestPassed
        is TimelineEntry.TaskCreated, is TimelineEntry.TaskResolved -> AllIconsKeys.Actions.Checked
        is TimelineEntry.CommitsPushed -> AllIconsKeys.Vcs.CommitNode
        is TimelineEntry.BuildUpdated -> buildStateIconKey(entry.status.state)
    }
}

/** The state entries use the state color, like the colorized Swing icons of the old activity feed. */
private fun activityEntryIconTint(entry: ActivityFeedEntry): Color? {
    return when (entry) {
        is TimelineEntry.Opened -> PullRequestStateStyle.color(PullRequestState.Open)
        is TimelineEntry.StateChanged -> PullRequestStateStyle.color(entry.state)
        else -> null
    }
}

private fun activityStateIcon(state: PullRequestState): IconKey {
    return when (state) {
        PullRequestState.Open -> AllIconsKeys.Vcs.Branch
        PullRequestState.Merged -> AllIconsKeys.Vcs.Merge
        PullRequestState.Declined -> AllIconsKeys.Actions.Cancel
    }
}

private fun openBuild(status: PullRequestBuildStatus, viewModel: PullRequestHistoryViewModel) {
    LOG.info("User opened build ${status.key} from the activity of pull request #${viewModel.currentPullRequestId()} in browser")
    BrowserUtil.browse(status.url)
}

private fun openCommit(commit: PullRequestCommit, viewModel: PullRequestHistoryViewModel) {
    LOG.info("User opened commit ${commit.hash} from the activity of pull request #${viewModel.currentPullRequestId()} in browser")
    commit.url?.let { url -> BrowserUtil.browse(url) }
}

private val LOG = logger<PullRequestHistoryViewModel>()