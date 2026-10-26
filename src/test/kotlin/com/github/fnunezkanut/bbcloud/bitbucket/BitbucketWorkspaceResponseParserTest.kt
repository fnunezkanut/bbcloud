package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketWorkspaceResponseParserTest {
    @Test
    fun `buildListWorkspacesUrl - should target the user workspaces endpoint`() {
        val url = BitbucketWorkspaceResponseParser.buildListWorkspacesUrl().toHttpUrl()

        assertSoftly(url) {
            scheme shouldBe "https"
            host shouldBe "api.bitbucket.org"
            encodedPath shouldBe "/2.0/user/workspaces"
            queryParameter("pagelen") shouldBe "100"
            queryParameter("sort") shouldBe "slug"
            // role=member came from the removed GET /2.0/repositories route and has no meaning here.
            queryParameter("role").shouldBeNull()
        }
    }

    @Test
    fun `parseWorkspaces - should map workspaces from a user workspaces payload`(): Unit =
        runBlocking {
            // GIVEN
            val body =
                """
                {
                  "pagelen": 100,
                  "page": 1,
                  "size": 2,
                  "values": [
                    {
                      "administrator": true,
                      "type": "workspace_access",
                      "workspace": {
                        "slug": "my-workspace",
                        "type": "workspace_base",
                        "uuid": "{ws-1}"
                      }
                    },
                    {
                      "administrator": false,
                      "type": "workspace_access",
                      "workspace": {
                        "slug": "other",
                        "type": "workspace_base",
                        "uuid": "{ws-2}"
                      }
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val workspaces = BitbucketWorkspaceResponseParser.parseWorkspaces(body)

            // THEN
            workspaces shouldBe listOf(
                BitbucketWorkspace(slug = "my-workspace", uuid = "{ws-1}"),
                BitbucketWorkspace(slug = "other", uuid = "{ws-2}")
            )
        }

    @Test
    fun `parseWorkspaces - should drop duplicate and blank slugs`(): Unit =
        runBlocking {
            // GIVEN
            val body =
                """
                {
                  "values": [
                    { "type": "workspace_access", "workspace": { "slug": "my-workspace" } },
                    { "type": "workspace_access", "workspace": { "slug": "my-workspace" } },
                    { "type": "workspace_access", "workspace": { "slug": "   " } },
                    { "type": "workspace_access", "workspace": null }
                  ]
                }
                """.trimIndent()

            // WHEN
            val workspaces = BitbucketWorkspaceResponseParser.parseWorkspaces(body)

            // THEN
            workspaces.map { workspace -> workspace.slug } shouldBe listOf("my-workspace")
        }

    @Test
    fun `parseWorkspaces - should return an empty list for malformed json`(): Unit =
        runBlocking {
            BitbucketWorkspaceResponseParser.parseWorkspaces("not json at all") shouldBe emptyList()
        }

    @Test
    fun `parseWorkspaces - should return an empty list for an empty page`(): Unit =
        runBlocking {
            BitbucketWorkspaceResponseParser.parseWorkspaces("""{"values":[]}""") shouldBe emptyList()
        }
}