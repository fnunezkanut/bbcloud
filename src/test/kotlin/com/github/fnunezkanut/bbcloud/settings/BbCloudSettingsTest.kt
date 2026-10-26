package com.github.fnunezkanut.bbcloud.settings

import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudSettingsTest {
    @Test
    fun `watchedProjects - should round-trip repository slugs to watched booleans`() {
        // GIVEN
        val settings = BbCloudSettings()
        val mappings = listOf(
            WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true),
            WatchedProjectMapping(localFolderPath = "/local/b", remoteRepoSlug = "repo-b", workspace = "my-workspace", watched = false)
        )

        // WHEN
        settings.replaceWatchedProjects(mappings)

        // THEN the flat map keys by repository slug only, so the workspace and local folder path are not persisted and
        // reload empty; the watched booleans survive, sorted by slug.
        settings.watchedProjects shouldBe listOf(
            WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = true),
            WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-b", workspace = "", watched = false)
        )
    }

    @Test
    fun `replaceWatchedProjects - should let the last entry win when a repository slug repeats`() {
        // GIVEN two mappings that share a repository slug (the workspace is no longer a persistence key, so they
        // collide on the single flat key)
        val settings = BbCloudSettings()
        val mappings = listOf(
            WatchedProjectMapping(localFolderPath = "/local/one", remoteRepoSlug = "shared-repo", workspace = "workspace-b", watched = true),
            WatchedProjectMapping(localFolderPath = "/local/two", remoteRepoSlug = "shared-repo", workspace = "workspace-a", watched = false)
        )

        // WHEN
        settings.replaceWatchedProjects(mappings)

        // THEN a single entry remains, carrying the last-written watched flag.
        settings.watchedProjects shouldBe listOf(
            WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "shared-repo", workspace = "", watched = false)
        )
    }

    @Test
    fun `clearWatchedProjects - should empty the persisted map`() {
        // GIVEN
        val settings = BbCloudSettings()
        settings.replaceWatchedProjects(
            listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
        )

        // WHEN
        settings.clearWatchedProjects()

        // THEN
        settings.watchedProjects shouldBe emptyList()
    }

    @Test
    fun `replaceWatchedProjects - should default a mapping with no explicit flag to unwatched`() {
        // GIVEN a mapping that does not pass watched, so it takes the unchecked default
        val settings = BbCloudSettings()
        settings.replaceWatchedProjects(
            listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
        )

        // WHEN
        val stored = settings.watchedProjects.single()

        // THEN
        assertSoftly {
            stored.remoteRepoSlug shouldBe "repo-a"
            stored.watched shouldBe false
        }
    }

    @Test
    fun `replaceRepoAvatarUrls - should store only non-null non-blank hrefs`() {
        // GIVEN
        val settings = BbCloudSettings()

        // WHEN a mix of a real href, a null, and a blank is replaced in
        settings.replaceRepoAvatarUrls(
            mapOf(
                "repo-a" to "https://cdn.example.com/a.png",
                "repo-b" to null,
                "repo-c" to "   "
            )
        )

        // THEN only the real href survives
        settings.repoAvatarUrls shouldBe mapOf("repo-a" to "https://cdn.example.com/a.png")
    }

    @Test
    fun `replaceRepoAvatarUrls - should replace the whole set, dropping entries not present in the new map`() {
        // GIVEN a stored set
        val settings = BbCloudSettings()
        settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png", "repo-b" to "https://cdn.example.com/b.png"))

        // WHEN replaced with a smaller set
        settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a-new.png"))

        // THEN repo-b is gone and repo-a is updated
        settings.repoAvatarUrls shouldBe mapOf("repo-a" to "https://cdn.example.com/a-new.png")
    }

    @Test
    fun `mergeRepoAvatarUrls - should upsert hrefs without dropping other entries`() {
        // GIVEN a stored set
        val settings = BbCloudSettings()
        settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png", "repo-b" to "https://cdn.example.com/b.png"))

        // WHEN merging an update for repo-a, a new repo-c, and a null for repo-d
        settings.mergeRepoAvatarUrls(
            mapOf(
                "repo-a" to "https://cdn.example.com/a-new.png",
                "repo-c" to "https://cdn.example.com/c.png",
                "repo-d" to null
            )
        )

        // THEN repo-b is untouched, repo-a is updated, repo-c is added, repo-d is skipped
        settings.repoAvatarUrls shouldBe mapOf(
            "repo-a" to "https://cdn.example.com/a-new.png",
            "repo-b" to "https://cdn.example.com/b.png",
            "repo-c" to "https://cdn.example.com/c.png"
        )
    }

    @Test
    fun `clearWatchedProjects - should empty both the watched map and the avatar URL map`() {
        // GIVEN both maps populated
        val settings = BbCloudSettings()
        settings.replaceWatchedProjects(
            listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
        )
        settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png"))

        // WHEN
        settings.clearWatchedProjects()

        // THEN
        assertSoftly {
            settings.watchedProjects shouldBe emptyList()
            settings.repoAvatarUrls shouldBe emptyMap()
        }
    }

    @Test
    fun `loadState - should keep workspaceSlug and projectsDirectory when a legacy two-level watchedProjects element is present`() {
        // GIVEN a persisted XML written by an earlier build, where watchedProjects is the old two-level shape keyed by
        // workspace. Deserializing that nested WorkspaceWatchedProjectsState into the new flat Boolean map used to
        // fault and corrupt the whole component load, silently dropping workspaceSlug and projectsDirectory with it.
        // The option is now named watchedRepos, so the stale watchedProjects element must simply be ignored.
        val legacyXml =
            """
            <component name="BbCloudSettings">
              <option name="organizationUrl" />
              <option name="projectsDirectory" value="/home/dev/Projects" />
              <option name="watchedProjects">
                <map>
                  <entry key="sample-workspace">
                    <value>
                      <WorkspaceWatchedProjectsState />
                    </value>
                  </entry>
                </map>
              </option>
              <option name="workspaceSlug" value="sample-workspace" />
            </component>
            """.trimIndent()
        val element = JDOMUtil.load(legacyXml)

        // WHEN the persisted element is deserialized into the current State shape
        val state = XmlSerializer.deserialize(element, BbCloudSettings.State::class.java)

        // THEN the sibling settings survive the legacy element, and the renamed flat map starts empty (rebuilt on the
        // next rescan rather than migrated).
        assertSoftly {
            state.workspaceSlug shouldBe "sample-workspace"
            state.projectsDirectory shouldBe "/home/dev/Projects"
            state.watchedRepos.isEmpty() shouldBe true
        }
    }
}