package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketRepositoryRemoteUrlTest {
    @Test
    fun `parse - should parse an SSH url with git suffix`() {
        val result = BitbucketRepositoryRemoteUrl.parse("git@bitbucket.org:sample-workspace/sample-repo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an SSH url without git suffix`() {
        val result = BitbucketRepositoryRemoteUrl.parse("git@bitbucket.org:sample-workspace/sample-repo")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an HTTPS url with git suffix`() {
        val result = BitbucketRepositoryRemoteUrl.parse("https://bitbucket.org/sample-workspace/sample-repo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an HTTPS url without git suffix`() {
        val result = BitbucketRepositoryRemoteUrl.parse("https://bitbucket.org/sample-workspace/sample-repo")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an HTTPS url with a user segment`() {
        val result = BitbucketRepositoryRemoteUrl.parse("https://user@bitbucket.org/sample-workspace/sample-repo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an SSH url on the alternate ssh host`() {
        // GIVEN Git's alternate SSH host used for cloning over port 443
        val result = BitbucketRepositoryRemoteUrl.parse("git@ssh.bitbucket.org:sample-workspace/sample-repo.git")

        // THEN the workspace and slug are parsed as for the primary host
        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an HTTPS url on a bitbucket subdomain`() {
        val result = BitbucketRepositoryRemoteUrl.parse("https://altssh.bitbucket.org/sample-workspace/sample-repo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an ssh scheme url with a port`() {
        // GIVEN the ssh:// URL form where the ':' is a port and '/' starts the path
        val result = BitbucketRepositoryRemoteUrl.parse("ssh://git@altssh.bitbucket.org:443/sample-workspace/sample-repo.git")

        // THEN the port is dropped and the workspace and slug are parsed from the path
        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an ssh scheme url without a port`() {
        val result = BitbucketRepositoryRemoteUrl.parse("ssh://git@bitbucket.org/sample-workspace/sample-repo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should parse an SSH url on a future unknown subdomain`() {
        // GIVEN a hypothetical new Bitbucket SSH host prefix that does not exist today
        val result = BitbucketRepositoryRemoteUrl.parse("git@vcs-ssh.bitbucket.org:sample-workspace/sample-repo.git")

        // THEN it still parses, because the match is any subdomain of bitbucket.org rather than a fixed allowlist
        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }

    @Test
    fun `parse - should return null for a host that only ends with bitbucket org as a suffix`() {
        // GIVEN a lookalike host that is not a bitbucket.org subdomain (no dot boundary before bitbucket.org)
        BitbucketRepositoryRemoteUrl.parse("git@evilbitbucket.org:sample-workspace/sample-repo.git") shouldBe null
    }

    @Test
    fun `parse - should return null for a GitHub SSH url`() {
        BitbucketRepositoryRemoteUrl.parse("git@github.com:sample-workspace/sample-repo.git") shouldBe null
    }

    @Test
    fun `parse - should return null for a GitHub HTTPS url`() {
        BitbucketRepositoryRemoteUrl.parse("https://github.com/sample-workspace/sample-repo.git") shouldBe null
    }

    @Test
    fun `parse - should return null for an empty string`() {
        BitbucketRepositoryRemoteUrl.parse("") shouldBe null
    }

    @Test
    fun `parse - should return null for a blank string`() {
        BitbucketRepositoryRemoteUrl.parse("   ") shouldBe null
    }

    @Test
    fun `parse - should return null for a non-URL string`() {
        BitbucketRepositoryRemoteUrl.parse("myrepo") shouldBe null
    }

    @Test
    fun `parse - should return null for a url with only one path segment`() {
        BitbucketRepositoryRemoteUrl.parse("https://bitbucket.org/sample-workspace") shouldBe null
    }

    @Test
    fun `parse - should return null for a url with three path segments`() {
        BitbucketRepositoryRemoteUrl.parse("https://bitbucket.org/sample-workspace/sample-repo/sub") shouldBe null
    }

    @Test
    fun `parse - should preserve case in workspace and repo slug`() {
        val result = BitbucketRepositoryRemoteUrl.parse("git@bitbucket.org:MyWorkspace/MyRepo.git")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "MyWorkspace"
            repo.repoSlug shouldBe "MyRepo"
        }
    }

    @Test
    fun `parse - should trim surrounding whitespace`() {
        val result = BitbucketRepositoryRemoteUrl.parse("  git@bitbucket.org:sample-workspace/sample-repo.git  ")

        result.shouldBeInstanceOf<BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository>().let { repo ->
            repo.workspace shouldBe "sample-workspace"
            repo.repoSlug shouldBe "sample-repo"
        }
    }
}