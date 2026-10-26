package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketWorkspacePullRequestResponseParserTest {
    @Nested
    inner class ListAuthoredPullRequestsUrl {
        @Test
        fun `buildListAuthoredPullRequestsUrl - should ask the workspace for the open pull requests of the user`() {
            // WHEN
            val url = BitbucketWorkspacePullRequestResponseParser.buildListAuthoredPullRequestsUrl(
                workspace = "my-workspace",
                selectedUserUuid = CURRENT_USER_UUID,
                pageLen = 50
            )

            // THEN
            assertSoftly(url) {
                shouldContain("/2.0/workspaces/my-workspace/pullrequests/")
                shouldContain("state=OPEN")
                shouldContain("sort=-updated_on")
                shouldContain("pagelen=50")
            }
        }

        @Test
        fun `buildListAuthoredPullRequestsUrl - should keep the braces of the user uuid out of the path`() {
            // WHEN
            val url = BitbucketWorkspacePullRequestResponseParser.buildListAuthoredPullRequestsUrl(
                workspace = "my-workspace",
                selectedUserUuid = CURRENT_USER_UUID,
                pageLen = 50
            )

            // THEN the uuid is a path segment, so its braces are percent-encoded rather than left raw
            url shouldContain "%7B5b10ac8d%7D"
        }
    }

    @Nested
    inner class ParseAuthoredPullRequestPage {
        @Test
        fun `parseAuthoredPullRequestPage - should pair each pull request with the repository of its self link`() {
            // GIVEN a workspace listing, which carries no repository object of its own
            val body =
                """
                {
                  "pagelen": 50,
                  "values": [$PULL_REQUEST_JSON, ${authoredPullRequestJson(id = 274, repoSlug = "third-repo")}],
                  "next": "https://api.bitbucket.org/2.0/workspaces/my-workspace/pullrequests/%7B5b10ac8d%7D?page=2"
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(responseBody = body)

            // THEN
            assertSoftly(page) {
                entries.map { entry -> entry.summary.id } shouldContainExactly listOf(273L, 274L)
                entries.map { entry -> entry.repoSlug } shouldContainExactly listOf("other-repo", "third-repo")
                entries.map { entry -> entry.summary.title } shouldContainExactly listOf("ABC-663 test", "PR 274")
                isTruncated shouldBe true
            }
        }

        @Test
        fun `parseAuthoredPullRequestPage - should fall back to the html link when there is no self link`() {
            // GIVEN
            val body = """{"values":[$PULL_REQUEST_JSON_WITHOUT_SELF_LINK]}"""

            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(responseBody = body)

            // THEN
            page.entries.single().repoSlug shouldBe "other-repo"
        }

        @Test
        fun `parseAuthoredPullRequestPage - should keep a row whose links name no repository`() {
            // GIVEN a pull request whose links name no repository anywhere in their path
            val body =
                """
                {
                  "values": [
                    {
                      "id": 1,
                      "title": "PR 1",
                      "state": "OPEN",
                      "comment_count": 0,
                      "created_on": "$CREATED_ON",
                      "updated_on": "$UPDATED_ON",
                      "author": { "display_name": "John Smith" },
                      "source": { "branch": { "name": "feature" } },
                      "destination": { "branch": { "name": "main" } },
                      "links": { "html": { "href": "https://bitbucket.org/pull-requests/1" } }
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(responseBody = body)

            // THEN the row survives with no repository, so the caller can report it rather than lose it silently
            assertSoftly(page) {
                entries.size shouldBe 1
                entries.single().repoSlug.shouldBeNull()
            }
        }

        @Test
        fun `parseAuthoredPullRequestPage - should drop entries that are not readable pull requests`() {
            // GIVEN a non-object value and an entry without the fields a summary needs
            val body = """{"values":[42,{"id":7}]}"""

            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(responseBody = body)

            // THEN
            page.entries shouldBe emptyList()
        }

        @Test
        fun `parseAuthoredPullRequestPage - should read an empty page as no pull requests and no truncation`() {
            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(
                responseBody = """{"values":[]}"""
            )

            // THEN
            assertSoftly(page) {
                entries shouldBe emptyList()
                isTruncated shouldBe false
            }
        }

        @Test
        fun `parseAuthoredPullRequestPage - should read a page without values as no pull requests`() {
            // WHEN
            val page = BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(responseBody = "{}")

            // THEN
            assertSoftly(page) {
                entries shouldBe emptyList()
                isTruncated shouldBe false
            }
        }
    }

    @Nested
    inner class ParsePullRequestSummary {
        @Test
        fun `parsePullRequestSummary - should read a single value on its own`() {
            // GIVEN one value of a paginated listing
            val value = Json.parseToJsonElement("[$PULL_REQUEST_JSON]").jsonArray.first()

            // WHEN
            val summary = BitbucketPullRequestResponseParser.parsePullRequestSummary(value = value).shouldNotBeNull()

            // THEN
            assertSoftly(summary) {
                id shouldBe 273L
                title shouldBe "ABC-663 test"
                webUrl shouldBe "https://bitbucket.org/my-workspace/other-repo/pull-requests/273"
            }
        }

        @Test
        fun `parsePullRequestSummary - should be null for a value that is not a pull request`() {
            // GIVEN
            val value = Json.parseToJsonElement("""{"id":7}""")

            // WHEN
            val summary = BitbucketPullRequestResponseParser.parsePullRequestSummary(value = value)

            // THEN
            summary.shouldBeNull()
        }
    }

    companion object {
        private const val CURRENT_USER_UUID = "{5b10ac8d}"
        private const val CREATED_ON = "2026-09-28T19:28:03.123456+00:00"
        private const val UPDATED_ON = "2026-09-29T19:28:03.123456+00:00"

        private val PULL_REQUEST_JSON = authoredPullRequestJson(id = 273, repoSlug = "other-repo", title = "ABC-663 test")

        private val PULL_REQUEST_JSON_WITHOUT_SELF_LINK =
            """
            {
              "id": 273,
              "title": "ABC-663 test",
              "state": "OPEN",
              "comment_count": 1,
              "created_on": "$CREATED_ON",
              "updated_on": "$UPDATED_ON",
              "author": { "display_name": "John Smith" },
              "source": { "branch": { "name": "feature" } },
              "destination": { "branch": { "name": "main" } },
              "links": { "html": { "href": "https://bitbucket.org/my-workspace/other-repo/pull-requests/273" } }
            }
            """.trimIndent()

        /** A workspace listing entry: it names its repository only through its links. */
        private fun authoredPullRequestJson(id: Long, repoSlug: String, title: String = "PR $id"): String {
            return """
                {
                  "id": $id,
                  "title": "$title",
                  "state": "OPEN",
                  "comment_count": 1,
                  "created_on": "$CREATED_ON",
                  "updated_on": "$UPDATED_ON",
                  "author": { "display_name": "John Smith" },
                  "source": { "branch": { "name": "feature" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": {
                    "self": { "href": "https://api.bitbucket.org/2.0/repositories/my-workspace/$repoSlug/pullrequests/$id" },
                    "html": { "href": "https://bitbucket.org/my-workspace/$repoSlug/pull-requests/$id" }
                  }
                }
                """.trimIndent()
        }
    }
}