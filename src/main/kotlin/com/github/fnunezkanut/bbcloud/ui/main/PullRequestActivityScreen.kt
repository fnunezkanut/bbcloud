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
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val ACTIVITY_AVATAR_SIZE = 24.dp
private val ACTIVITY_GUTTER_WIDTH = 28.dp
private val ACTIVITY_GUTTER_DIAMETER = 28.dp
private val ACTIVITY_GUTTER_ICON_SIZE = 16.dp
private val ACTIVITY_GUTTER_LINE_WIDTH = 2.dp
private val ACTIVITY_ENTRY_GAP = 16.dp
private const val ACTIVITY_SHORT_HASH_LENGTH = 7

/**
 * The Jewel activity feed of the Changes tab. It renders the same information as the old Swing activity feed
 * section: the day headers, the entry rows with their avatar, actor, action and relative time, and the
 * partial-failure and truncation notices.
 */
@Composable
internal fun PullRequestActivityFeedScreen(viewModel: PullRequestActivityViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = state.title,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (state.showPartialFailure) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(key = AllIconsKeys.General.BalloonWarning, contentDescription = null)
                Text(
                    text = BbCloudBundle.message("main.branch.activity.partialFailure"),
                    color = JewelTheme.globalColors.text.info
                )
            }
        }
        val groups = state.groups
        groups.forEachIndexed { groupIndex, group ->
            Text(
                text = activityDayHeaderText(group.day),
                fontWeight = FontWeight.Bold,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            group.entries.forEachIndexed { entryIndex, entry ->
                val activityEntry = entry as? ActivityFeedEntry ?: return@forEachIndexed
                val isLast = groupIndex == groups.lastIndex && entryIndex == group.entries.lastIndex
                ActivityEntryRow(
                    entry = activityEntry,
                    isLast = isLast,
                    avatarRevision = state.avatarRevision,
                    viewModel = viewModel
                )
            }
        }
        if (state.showTruncation) {
            Text(
                text = BbCloudBundle.message("main.branch.activity.truncated"),
                color = JewelTheme.globalColors.text.info
            )
        }
    }
}

@Composable
private fun ActivityEntryRow(entry: ActivityFeedEntry, isLast: Boolean, avatarRevision: Int, viewModel: PullRequestActivityViewModel) {
    val gutterLineColor = JewelTheme.globalColors.borders.normal
    val gutterCircleColor = JewelTheme.globalColors.panelBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawActivityGutter(
                    isLast = isLast,
                    lineColor = gutterLineColor,
                    circleColor = gutterCircleColor
                )
            }
            .padding(bottom = ACTIVITY_ENTRY_GAP)
    ) {
        Box(
            modifier = Modifier.width(ACTIVITY_GUTTER_WIDTH),
            contentAlignment = Alignment.TopCenter
        ) {
            Icon(
                key = activityEntryIcon(entry),
                contentDescription = null,
                tint = activityEntryIconTint(entry) ?: Color.Unspecified,
                modifier = Modifier
                    .padding(top = (ACTIVITY_GUTTER_DIAMETER - ACTIVITY_GUTTER_ICON_SIZE) / 2)
                    .size(ACTIVITY_GUTTER_ICON_SIZE)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (entry !is TimelineEntry.BuildUpdated) {
                val avatarUrl = entry.actor?.avatarUrl
                val bitmap = remember(avatarUrl, avatarRevision) {
                    viewModel.avatarIconFor(avatarUrl).toComposeImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = "Author avatar",
                    modifier = Modifier.size(ACTIVITY_AVATAR_SIZE),
                    filterQuality = FilterQuality.High
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                ActivityHeadline(entry = entry, viewModel = viewModel)
                ActivityBody(entry = entry, viewModel = viewModel)
            }
        }
    }
}

/**
 * Draws the connecting line and the circle that carries the entry icon; [isLast] stops the line at the circle.
 * The line is drawn behind the icon, so the circle keeps the icon readable.
 */
private fun DrawScope.drawActivityGutter(isLast: Boolean, lineColor: Color, circleColor: Color) {
    val diameter = ACTIVITY_GUTTER_DIAMETER.toPx()
    val lineWidth = ACTIVITY_GUTTER_LINE_WIDTH.toPx()
    // The gutter sits at the start of the row, so the line is drawn at the center of the gutter column.
    val centerX = ACTIVITY_GUTTER_WIDTH.toPx() / 2f
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
private fun ActivityHeadline(entry: ActivityFeedEntry, viewModel: PullRequestActivityViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (entry) {
            is TimelineEntry.Opened -> {
                ActivityActorName(entry.actor)
                ActivityStatePill(
                    text = BbCloudBundle.message("main.branch.activity.pill.opened"),
                    state = PullRequestState.Open
                )
                Text(text = BbCloudBundle.message("main.branch.activity.action.pullRequest"))
            }
            is TimelineEntry.StateChanged -> {
                ActivityActorName(entry.actor)
                ActivityStatePill(text = activityStatePillText(entry.state), state = entry.state)
                Text(text = BbCloudBundle.message("main.branch.activity.action.pullRequest"))
            }
            is TimelineEntry.Approved -> {
                ActivityActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.activity.action.approved"))
            }
            is TimelineEntry.TaskCreated -> {
                ActivityActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.activity.action.taskCreated"))
            }
            is TimelineEntry.TaskResolved -> {
                ActivityActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.activity.action.taskResolved"))
            }
            is TimelineEntry.CommitsPushed -> {
                ActivityActorName(entry.actor)
                Text(text = BbCloudBundle.message("main.branch.activity.action.commitsPushed", entry.commits.size))
            }
            is TimelineEntry.BuildUpdated -> {
                Link(
                    text = entry.status.name,
                    onClick = { openBuild(status = entry.status, viewModel = viewModel) }
                )
                Text(text = activityBuildStateText(entry.status.state))
            }
        }
        Text(
            text = PullRequestRelativeTime.format(entry.date),
            color = JewelTheme.globalColors.text.info
        )
    }
}

@Composable
private fun ActivityBody(entry: ActivityFeedEntry, viewModel: PullRequestActivityViewModel) {
    when (entry) {
        is TimelineEntry.TaskCreated -> {
            ActivityTaskCheckBox(task = entry.task)
        }
        is TimelineEntry.TaskResolved -> {
            ActivityTaskCheckBox(task = entry.task)
        }
        is TimelineEntry.CommitsPushed -> {
            ActivityCommitsBody(commits = entry.commits, viewModel = viewModel)
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
private fun RowScope.ActivityActorName(actor: ActivityUser?) {
    Text(
        text = actor?.displayName ?: BbCloudBundle.message("main.branch.activity.unknownUser"),
        fontWeight = FontWeight.Bold,
        // The weight lets a long name shrink instead of pushing the pill, the action and the time out of the row.
        modifier = Modifier.weight(1f, fill = false),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )
}

@Composable
private fun ActivityStatePill(text: String, state: PullRequestState) {
    Badge(style = PullRequestBadgeStyles.state(state)) {
        Text(text = text)
    }
}

@Composable
private fun ActivityTaskCheckBox(task: PullRequestTask) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.isResolved, onCheckedChange = {}, enabled = false)
        Text(text = task.content)
    }
}

@Composable
private fun ActivityCommitsBody(commits: List<PullRequestCommit>, viewModel: PullRequestActivityViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        commits.forEach { commit -> ActivityCommitRow(commit = commit, viewModel = viewModel) }
    }
}

@Composable
private fun ActivityCommitRow(commit: PullRequestCommit, viewModel: PullRequestActivityViewModel) {
    val shortHash = commit.hash.take(ACTIVITY_SHORT_HASH_LENGTH)
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
            Text(text = shortHash, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = commit.message.lineSequence().firstOrNull().orEmpty(),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

internal fun activityDayHeaderText(day: ActivityDay): String {
    return when (day) {
        ActivityDay.Today -> BbCloudBundle.message("main.branch.activity.day.today")
        ActivityDay.Yesterday -> BbCloudBundle.message("main.branch.activity.day.yesterday")
        is ActivityDay.Earlier -> activityFormatDate(day.date)
    }
}

private fun activityFormatDate(date: LocalDate): String {
    return DateFormatUtil.formatDate(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds())
}

private fun activityStatePillText(state: PullRequestState): String {
    return when (state) {
        PullRequestState.Open -> BbCloudBundle.message("main.branch.activity.pill.opened")
        PullRequestState.Merged -> BbCloudBundle.message("main.branch.activity.pill.merged")
        PullRequestState.Declined -> BbCloudBundle.message("main.branch.activity.pill.declined")
    }
}

private fun activityBuildStateText(state: BuildState): String {
    return when (state) {
        BuildState.Successful -> BbCloudBundle.message("main.branch.activity.build.successful")
        BuildState.Failed -> BbCloudBundle.message("main.branch.activity.build.failed")
        BuildState.InProgress -> BbCloudBundle.message("main.branch.activity.build.inProgress")
        BuildState.Stopped -> BbCloudBundle.message("main.branch.activity.build.stopped")
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

private fun openBuild(status: PullRequestBuildStatus, viewModel: PullRequestActivityViewModel) {
    LOG.info("User opened build ${status.key} from the activity of pull request #${viewModel.currentPullRequestId()} in browser")
    BrowserUtil.browse(status.url)
}

private fun openCommit(commit: PullRequestCommit, viewModel: PullRequestActivityViewModel) {
    LOG.info("User opened commit ${commit.hash} from the activity of pull request #${viewModel.currentPullRequestId()} in browser")
    commit.url?.let { url -> BrowserUtil.browse(url) }
}

private val LOG = logger<PullRequestActivityViewModel>()