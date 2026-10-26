package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BuildState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestBuildStatus

enum class BuildSummaryKind {
    Empty,
    Failed,
    InProgress,
    Passed
}

data class BuildStatusSummary(val kind: BuildSummaryKind, val count: Int, val total: Int) {
    companion object {
        fun of(builds: List<PullRequestBuildStatus>): BuildStatusSummary {
            val total = builds.size
            val failed = builds.count { it.state == BuildState.Failed || it.state == BuildState.Stopped }
            val inProgress = builds.count { it.state == BuildState.InProgress }
            return when {
                total == 0 -> BuildStatusSummary(kind = BuildSummaryKind.Empty, count = 0, total = 0)
                failed > 0 -> BuildStatusSummary(kind = BuildSummaryKind.Failed, count = failed, total = total)
                inProgress > 0 -> BuildStatusSummary(kind = BuildSummaryKind.InProgress, count = inProgress, total = total)
                else -> BuildStatusSummary(kind = BuildSummaryKind.Passed, count = total, total = total)
            }
        }
    }
}