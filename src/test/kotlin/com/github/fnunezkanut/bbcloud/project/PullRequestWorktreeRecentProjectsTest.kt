package com.github.fnunezkanut.bbcloud.project

import com.intellij.ide.ProjectGroup
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class PullRequestWorktreeRecentProjectsTest {
    private val groupName = "PR work trees"
    private val worktreePath: Path = Path.of("/tmp/.worktrees/clone--pr-9")
    private val worktreeKey = "/tmp/.worktrees/clone--pr-9"

    @Test
    fun `addToGroup - should create the group and move the worktree into it when the group does not exist`() {
        // GIVEN a gateway with no groups
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)

        // WHEN
        recentProjects.addToGroup(worktreePath = worktreePath, groupName = groupName)

        // THEN the group is created once and holds the worktree
        assertSoftly(gateway) {
            addedGroups shouldHaveSize 1
            addedGroups.single().name shouldBe groupName
            addedGroups.single().projects shouldContainExactly listOf(worktreeKey)
        }
    }

    @Test
    fun `addToGroup - should reuse the existing group when one with the same name exists`() {
        // GIVEN a gateway that already holds a group with the same name
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)
        val otherWorktreePath = Path.of("/tmp/.worktrees/clone--pr-10")

        // WHEN two worktrees are added
        recentProjects.addToGroup(worktreePath = worktreePath, groupName = groupName)
        recentProjects.addToGroup(worktreePath = otherWorktreePath, groupName = groupName)

        // THEN the group is created only once and holds both worktrees
        assertSoftly(gateway) {
            addedGroups shouldHaveSize 1
            groups.single().projects shouldContainExactly listOf(worktreeKey, "/tmp/.worktrees/clone--pr-10")
        }
    }

    @Test
    fun `addToGroup - should ignore a folder that is not a pull request worktree`() {
        // GIVEN a plain source clone folder
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)

        // WHEN the folder is added to a group
        recentProjects.addToGroup(worktreePath = Path.of("/tmp/clone"), groupName = groupName)

        // THEN the gateway is not called
        assertSoftly(gateway) {
            groups.shouldBeEmpty()
            addedGroups.shouldBeEmpty()
            movedPaths.shouldBeEmpty()
        }
    }

    @Test
    fun `addToGroup - should pass an invariant separators absolute path to the gateway`() {
        // GIVEN a worktree path that contains a relative segment
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)

        // WHEN
        recentProjects.addToGroup(worktreePath = Path.of("/tmp/work/../.worktrees/clone--pr-9"), groupName = groupName)

        // THEN the gateway receives the normalized absolute path
        gateway.movedPaths shouldContainExactly listOf(worktreeKey)
    }

    @Test
    fun `addToGroup - should swallow a gateway failure`() {
        // GIVEN a gateway that throws on every call
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = FailingRecentProjectsGateway())

        // WHEN / THEN the failure does not escape
        shouldNotThrowAny {
            recentProjects.addToGroup(worktreePath = worktreePath, groupName = groupName)
        }
    }

    @Test
    fun `forget - should remove the path and the now empty group`() {
        // GIVEN a group that holds only the worktree
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)
        recentProjects.addToGroup(worktreePath = worktreePath, groupName = groupName)

        // WHEN the worktree is forgotten
        recentProjects.forget(worktreePath = worktreePath, groupName = groupName)

        // THEN the path is removed and the empty group is deleted
        assertSoftly(gateway) {
            removedPaths shouldContainExactly listOf(worktreeKey)
            removedGroups shouldHaveSize 1
            groups.shouldBeEmpty()
        }
    }

    @Test
    fun `forget - should keep the group when it still contains other worktrees`() {
        // GIVEN a group that holds two worktrees
        val gateway = FakeRecentProjectsGateway()
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)
        val otherWorktreePath = Path.of("/tmp/.worktrees/clone--pr-10")
        recentProjects.addToGroup(worktreePath = worktreePath, groupName = groupName)
        recentProjects.addToGroup(worktreePath = otherWorktreePath, groupName = groupName)

        // WHEN one worktree is forgotten
        recentProjects.forget(worktreePath = worktreePath, groupName = groupName)

        // THEN the group stays and keeps the other worktree
        assertSoftly(gateway) {
            removedGroups.shouldBeEmpty()
            groups.single().projects shouldContainExactly listOf("/tmp/.worktrees/clone--pr-10")
        }
    }

    @Test
    fun `forget - should swallow a gateway failure`() {
        // GIVEN a gateway that throws on every call
        val recentProjects = PullRequestWorktreeRecentProjects(gateway = FailingRecentProjectsGateway())

        // WHEN / THEN the failure does not escape
        shouldNotThrowAny {
            recentProjects.forget(worktreePath = worktreePath, groupName = groupName)
        }
    }

    /** Stores groups in memory and applies the same rules as the platform: a path lives in at most one group. */
    private class FakeRecentProjectsGateway : RecentProjectsGateway {
        val groups = mutableListOf<ProjectGroup>()
        val addedGroups = mutableListOf<ProjectGroup>()
        val removedGroups = mutableListOf<ProjectGroup>()
        val movedPaths = mutableListOf<String>()
        val removedPaths = mutableListOf<String>()

        override fun findGroup(name: String): ProjectGroup? {
            return groups.firstOrNull { group -> group.name == name }
        }

        override fun addGroup(group: ProjectGroup) {
            if (!groups.contains(group)) {
                groups.add(group)
                addedGroups.add(group)
            }
        }

        override fun removeGroup(group: ProjectGroup) {
            groups.remove(group)
            removedGroups.add(group)
        }

        override fun moveProjectToGroup(path: String, group: ProjectGroup) {
            groups.forEach { existing -> existing.removeProject(path) }
            group.addProject(path)
            movedPaths.add(path)
        }

        override fun removePath(path: String) {
            groups.forEach { group -> group.removeProject(path) }
            removedPaths.add(path)
        }
    }

    private class FailingRecentProjectsGateway : RecentProjectsGateway {
        override fun findGroup(name: String): ProjectGroup? {
            throw IllegalStateException("platform unavailable")
        }

        override fun addGroup(group: ProjectGroup) {
            throw IllegalStateException("platform unavailable")
        }

        override fun removeGroup(group: ProjectGroup) {
            throw IllegalStateException("platform unavailable")
        }

        override fun moveProjectToGroup(path: String, group: ProjectGroup) {
            throw IllegalStateException("platform unavailable")
        }

        override fun removePath(path: String) {
            throw IllegalStateException("platform unavailable")
        }
    }
}