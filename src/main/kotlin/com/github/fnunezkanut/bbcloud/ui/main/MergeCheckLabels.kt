package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestMergeCheck
import com.intellij.openapi.util.text.StringUtil

sealed interface MergeCheckLabel {
    /** A bundle message whose optional [count] is the configured threshold of the check. */
    data class Message(val key: String, val count: Int? = null) : MergeCheckLabel

    /** Plain text that is escaped before it is shown as HTML. */
    data class Text(val text: String) : MergeCheckLabel
}

data class MergeCheckSummary(val passed: Int, val total: Int, val isBlocking: Boolean) {
    companion object {
        fun of(checks: List<PullRequestMergeCheck>): MergeCheckSummary {
            return MergeCheckSummary(
                passed = checks.count { check -> check.status == MergeCheckStatus.Passed },
                total = checks.size,
                isBlocking = checks.any { check -> check.isBlocking }
            )
        }
    }
}

object MergeCheckLabels {
    fun labelFor(check: PullRequestMergeCheck): MergeCheckLabel {
        return when (check.kind) {
            PullRequestMergeCheck.CUSTOM_KIND -> {
                check.name?.let { name -> MergeCheckLabel.Text(name) } ?: MergeCheckLabel.Message(key = "main.branch.checks.kind.custom")
            }
            "minimum_approvals" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.minimumApprovals", count = check.requiredValue ?: 1)
            }
            "minimum_default_reviewer_approvals" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.minimumDefaultReviewerApprovals", count = check.requiredValue ?: 1)
            }
            "minimum_successful_builds" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.minimumSuccessfulBuilds", count = check.requiredValue ?: 1)
            }
            "maximum_commits_behind" -> {
                val maximum = check.requiredValue ?: 0
                if (maximum == 0) {
                    MergeCheckLabel.Message(key = "main.branch.checks.kind.noCommitsBehind")
                } else {
                    MergeCheckLabel.Message(key = "main.branch.checks.kind.maximumCommitsBehind", count = maximum)
                }
            }
            "resolved_tasks" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.resolvedTasks")
            }
            "no_failed_builds" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.noFailedBuilds")
            }
            "no_in_progress_builds" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.noInProgressBuilds")
            }
            "no_changes_requested" -> {
                MergeCheckLabel.Message(key = "main.branch.checks.kind.noChangesRequested")
            }
            else -> {
                MergeCheckLabel.Text(humanize(check.kind))
            }
        }
    }

    /** Turns an undocumented `check.kind` such as `no_failed_builds` into "No failed builds". */
    fun humanize(kind: String): String {
        val words = kind.split('_', '-', ' ').filter { word -> word.isNotBlank() }
        return words.joinToString(separator = " ").lowercase().replaceFirstChar { first -> first.uppercase() }
    }

    fun toHtml(label: MergeCheckLabel): String {
        return when (label) {
            is MergeCheckLabel.Message -> {
                if (label.count == null) BbCloudBundle.message(label.key) else BbCloudBundle.message(label.key, label.count)
            }
            is MergeCheckLabel.Text -> {
                StringUtil.escapeXmlEntities(label.text)
            }
        }
    }
}