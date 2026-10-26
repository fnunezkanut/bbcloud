package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class BitbucketGitRemoteReaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `readRemoteUrl - should read origin url from a normal git directory`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(
            dir = checkout,
            sections = mapOf(
                "origin" to mapOf("url" to "git@bitbucket.org:workspace/myrepo.git")
            )
        )

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    @Test
    fun `readRemoteUrl - should fall back to pushurl when url is absent`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(
            dir = checkout,
            sections = mapOf(
                "origin" to mapOf("pushurl" to "git@bitbucket.org:workspace/myrepo.git")
            )
        )

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    @Test
    fun `readRemoteUrl - should fall back to the first remote when origin is absent`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(
            dir = checkout,
            sections = mapOf(
                "upstream" to mapOf("url" to "git@bitbucket.org:workspace/myrepo.git")
            )
        )

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    @Test
    fun `readRemoteUrl - should prefer origin over other remotes`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(
            dir = checkout,
            sections = mapOf(
                "origin" to mapOf("url" to "git@bitbucket.org:workspace/myrepo.git"),
                "upstream" to mapOf("url" to "git@bitbucket.org:workspace/other.git")
            )
        )

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    @Test
    fun `readRemoteUrl - should return null when no remote exists`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(dir = checkout, sections = emptyMap())

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe null
    }

    @Test
    fun `readRemoteUrl - should return null when no git directory is present`() {
        val checkout = tempDir.resolve("no-git-dir")
        Files.createDirectory(checkout)

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe null
    }

    @Test
    fun `readRemoteUrl - should read gitfile for a worktree checkout`() {
        val checkout = createCheckout(checkoutName = "myrepo", dotGitIsFile = true)
        val gitDir = tempDir.resolve("myrepo.git")
        Files.createDirectory(gitDir)
        writeGitConfig(
            dir = gitDir,
            sections = mapOf("origin" to mapOf("url" to "git@bitbucket.org:workspace/myrepo.git"))
        )
        writeGitfile(checkout = checkout, gitDirPath = gitDir)

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    @Test
    fun `readRemoteUrl - should ignore non-remote ini sections`() {
        val checkout = createCheckout(checkoutName = "myrepo")
        writeGitConfig(
            dir = checkout,
            sections = mapOf(
                "core" to mapOf("repositoryformatversion" to "0"),
                "origin" to mapOf("url" to "git@bitbucket.org:workspace/myrepo.git")
            )
        )

        BitbucketGitRemoteReader.readRemoteUrl(checkout) shouldBe "git@bitbucket.org:workspace/myrepo.git"
    }

    private fun createCheckout(checkoutName: String, dotGitIsFile: Boolean = false): Path {
        val checkout = tempDir.resolve(checkoutName)
        Files.createDirectory(checkout)
        if (dotGitIsFile) {
            Files.createTempFile(checkout, ".git", "")
        } else {
            Files.createDirectory(checkout.resolve(".git"))
        }
        return checkout
    }

    private fun writeGitConfig(dir: Path, sections: Map<String, Map<String, String>>) {
        val configPath = if (Files.isDirectory(dir.resolve(".git"))) {
            dir.resolve(".git").resolve("config")
        } else {
            dir.resolve("config")
        }
        val lines = sections.flatMap { (name, kv) ->
            listOf("[remote \"$name\"]") + kv.map { (k, v) -> "$k = $v" }
        }
        Files.write(configPath, lines)
    }

    private fun writeGitfile(checkout: Path, gitDirPath: Path) {
        val gitfile = checkout.resolve(".git")
        Files.writeString(gitfile, "gitdir: ${gitDirPath.toAbsolutePath()}")
    }
}