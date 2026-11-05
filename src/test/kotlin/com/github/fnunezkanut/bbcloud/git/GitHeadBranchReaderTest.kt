package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class GitHeadBranchReaderTest {
    @Nested
    inner class ParseHeadFileContent {
        @Test
        fun `branch ref - should return the branch name`() {
            // GIVEN
            val content = "ref: refs/heads/main\n"

            // WHEN
            val result = parseHeadFileContent(content = content)

            // THEN
            result shouldBe HeadFileContent.Branch(name = "main")
        }

        @Test
        fun `branch ref with slashes - should keep the slashes`() {
            // GIVEN
            val content = "ref: refs/heads/feature/FLEX-663\r\n"

            // WHEN
            val result = parseHeadFileContent(content = content)

            // THEN
            result shouldBe HeadFileContent.Branch(name = "feature/FLEX-663")
        }

        @Test
        fun `sha1 and sha256 - should be detached`() {
            // GIVEN
            val sha1 = "a".repeat(40)
            val sha256 = "0123456789abcdef".repeat(4)

            // WHEN / THEN
            parseHeadFileContent(content = sha1) shouldBe HeadFileContent.Detached
            parseHeadFileContent(content = sha256 + "\n") shouldBe HeadFileContent.Detached
        }

        @Test
        fun `reftable stub - should be reported as a stub`() {
            // GIVEN
            val content = "ref: refs/heads/.invalid"

            // WHEN
            val result = parseHeadFileContent(content = content)

            // THEN
            result shouldBe HeadFileContent.ReftableStub
        }

        @Test
        fun `blank or odd content - should be unknown`() {
            // WHEN / THEN
            parseHeadFileContent(content = "") shouldBe HeadFileContent.Unknown
            parseHeadFileContent(content = "  \n") shouldBe HeadFileContent.Unknown
            parseHeadFileContent(content = "ref: refs/tags/v1") shouldBe HeadFileContent.Unknown
            parseHeadFileContent(content = "zzzz") shouldBe HeadFileContent.Unknown
        }
    }

    @Nested
    inner class ParseGitDirPointer {
        @Test
        fun `absolute and relative paths - should be returned trimmed`() {
            // WHEN / THEN
            parseGitDirPointer(content = "gitdir: /repo/.git/worktrees/a\n") shouldBe "/repo/.git/worktrees/a"
            parseGitDirPointer(content = "gitdir: ../repo/.git/modules/a") shouldBe "../repo/.git/modules/a"
        }

        @Test
        fun `missing prefix or blank - should be null`() {
            // WHEN / THEN
            parseGitDirPointer(content = "/repo/.git") shouldBe null
            parseGitDirPointer(content = "") shouldBe null
            parseGitDirPointer(content = "gitdir:   ") shouldBe null
        }
    }

    @Nested
    inner class ReadBranch {
        @TempDir
        lateinit var temp: Path

        @Test
        fun `git directory - should read the branch`() {
            // GIVEN
            val gitDir = Files.createDirectories(temp.resolve("repo/.git"))
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/develop\n")

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = temp.resolve("repo")) }

            // THEN
            branch shouldBe "develop"
        }

        @Test
        fun `worktree file with absolute gitdir - should read the branch`() {
            // GIVEN
            val gitDir = Files.createDirectories(temp.resolve("main/.git/worktrees/wt"))
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/pr-branch\n")
            val worktree = Files.createDirectories(temp.resolve("wt"))
            Files.writeString(worktree.resolve(".git"), "gitdir: $gitDir\n")

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = worktree) }

            // THEN
            branch shouldBe "pr-branch"
        }

        @Test
        fun `worktree file with relative gitdir - should read the branch`() {
            // GIVEN
            val gitDir = Files.createDirectories(temp.resolve("main/.git/worktrees/wt"))
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/rel\n")
            val worktree = Files.createDirectories(temp.resolve("wt"))
            Files.writeString(worktree.resolve(".git"), "gitdir: ../main/.git/worktrees/wt\n")

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = worktree) }

            // THEN
            branch shouldBe "rel"
        }

        @Test
        fun `no git folder - should be null`() {
            // GIVEN
            val folder = Files.createDirectories(temp.resolve("plain"))

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = folder) }

            // THEN
            branch shouldBe null
        }

        @Test
        fun `missing HEAD file - should be null without throwing`() {
            // GIVEN
            val folder = Files.createDirectories(temp.resolve("repo/.git")).parent

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = folder) }

            // THEN
            branch shouldBe null
        }

        @Test
        fun `detached HEAD - should be null`() {
            // GIVEN
            val gitDir = Files.createDirectories(temp.resolve("repo/.git"))
            Files.writeString(gitDir.resolve("HEAD"), "a".repeat(40) + "\n")

            // WHEN
            val branch = runBlocking { GitHeadBranchReader.readBranch(projectPath = temp.resolve("repo")) }

            // THEN
            branch shouldBe null
        }
    }
}