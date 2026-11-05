package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.project.RecentProjectGroupSnapshot
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSnapshot
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class RecentProjectsUiStateTest {
    private fun group(name: String, vararg paths: String, expanded: Boolean = false): RecentProjectGroupSnapshot {
        return RecentProjectGroupSnapshot(
            name = name,
            projectPaths = paths.toList(),
            isExpanded = expanded
        )
    }

    private fun row(name: String, path: String = "/p/$name", branch: String? = null): RecentProjectRow {
        return RecentProjectRow(
            path = path,
            name = name,
            displayPath = path,
            branch = branch,
            exists = true,
            isGrouped = false
        )
    }

    @Nested
    inner class BuildTree {
        @Test
        fun `groups - should be ordered by most recent member then name`() {
            // GIVEN
            val snapshot = RecentProjectsSnapshot(
                recentPaths = listOf("/a", "/b", "/c", "/d"),
                groups = listOf(
                    group("Zeta", "/c"),
                    group("Beta", "/d"),
                    group("alpha", "/d"),
                    group("Newest", "/b", "/a"),
                    group("Unlisted", "/gone")
                )
            )

            // WHEN
            val tree = buildRecentProjectsTree(snapshot = snapshot)

            // THEN
            tree.groups.map { (group, _) -> group.name } shouldBe listOf("Newest", "Zeta", "alpha", "Beta", "Unlisted")
        }

        @Test
        fun `ungrouped paths - should exclude grouped ones and keep recent order`() {
            // GIVEN
            val snapshot = RecentProjectsSnapshot(
                recentPaths = listOf("/a", "/b", "/c"),
                groups = listOf(group("G", "/b"))
            )

            // WHEN
            val tree = buildRecentProjectsTree(snapshot = snapshot)

            // THEN
            tree.ungroupedPaths shouldBe listOf("/a", "/c")
        }

        @Test
        fun `duplicates and empty groups - should be removed`() {
            // GIVEN
            val snapshot = RecentProjectsSnapshot(
                recentPaths = listOf("/a"),
                groups = listOf(group("G", "/a", "/a"), group("Empty"))
            )

            // WHEN
            val tree = buildRecentProjectsTree(snapshot = snapshot)

            // THEN
            assertSoftly {
                tree.groups.size shouldBe 1
                tree.groups.single().second shouldBe listOf("/a")
            }
        }
    }

    @Nested
    inner class Filter {
        private val groups = listOf(
            RecentProjectGroupRow(
                name = "PR work trees",
                isExpanded = false,
                projects = listOf(row(name = "api--pr-1", branch = "feature/x"), row(name = "web--pr-2"))
            ),
            RecentProjectGroupRow(
                name = "Other",
                isExpanded = false,
                projects = listOf(row(name = "tool"))
            )
        )
        private val ungrouped = listOf(row(name = "Solo", path = "/home/solo"))

        @Test
        fun `blank query - should return the input unchanged`() {
            // WHEN
            val result = filterRecentProjects(groups = groups, ungrouped = ungrouped, query = "  ")

            // THEN
            result shouldBe (groups to ungrouped)
        }

        @Test
        fun `query - should match name path and branch case-insensitively`() {
            // WHEN / THEN
            filterRecentProjects(groups, ungrouped, "SOLO").second shouldBe ungrouped
            filterRecentProjects(groups, ungrouped, "/home/").second shouldBe ungrouped
            filterRecentProjects(groups, ungrouped, "FEATURE").first.single().projects.map { it.name } shouldBe listOf("api--pr-1")
        }

        @Test
        fun `group name match - should keep all children and force expansion`() {
            // WHEN
            val (filteredGroups, _) = filterRecentProjects(groups, ungrouped, "work")

            // THEN
            assertSoftly {
                filteredGroups.single().projects.size shouldBe 2
                filteredGroups.single().isExpanded shouldBe true
            }
        }

        @Test
        fun `group without matching children - should be dropped`() {
            // WHEN
            val (filteredGroups, filteredUngrouped) = filterRecentProjects(groups, ungrouped, "tool")

            // THEN
            assertSoftly {
                filteredGroups.map { it.name } shouldBe listOf("Other")
                filteredUngrouped shouldBe emptyList()
            }
        }
    }

    @Nested
    inner class ShortenHomePath {
        @Test
        fun `paths - should shorten only whole segments under home`() {
            // WHEN / THEN
            assertSoftly {
                shortenHomePath("/Users/me/app", "/Users/me") shouldBe "~/app"
                shortenHomePath("/Users/me", "/Users/me") shouldBe "~"
                shortenHomePath("/opt/app", "/Users/me") shouldBe "/opt/app"
                shortenHomePath("/Users/mex/app", "/Users/me") shouldBe "/Users/mex/app"
                shortenHomePath("/Users/me/app", "/Users/me/") shouldBe "~/app"
            }
        }
    }
}