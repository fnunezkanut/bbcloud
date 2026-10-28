package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class BitbucketPullRequestResponseParserTest {
    @Test
    fun `buildListPullRequestsUrl - should include open merged declined states sort and pagelen 50`() {
        val url = BitbucketPullRequestResponseParser.buildListPullRequestsUrl(
            workspace = "my-workspace",
            repoSlug = "examplerepo"
        )

        url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests" +
            "?state=OPEN&state=MERGED&state=DECLINED&sort=-updated_on&pagelen=50"
    }

    @Test
    fun `buildListOpenPullRequestsUrl - should include only open state sort and pagelen 50`() {
        val url = BitbucketPullRequestResponseParser.buildListOpenPullRequestsUrl(
            workspace = "my-workspace",
            repoSlug = "examplerepo"
        )

        url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests" +
            "?state=OPEN&sort=-updated_on&pagelen=50"
        url.contains("state=MERGED") shouldBe false
        url.contains("state=DECLINED") shouldBe false
    }

    @Test
    fun `parsePullRequests - should map values in response order`() {
        val json =
            """
            {
              "values": [
                {
                  "id": 273,
                  "title": "ABC-663 test",
                  "state": "OPEN",
                  "comment_count": 1,
                  "created_on": "2026-09-28T19:28:03.123456+00:00",
                  "updated_on": "2026-09-29T19:28:03.123456+00:00",
                  "author": {
                    "display_name": "John Smith",
                    "links": {
                      "avatar": {
                        "href": "https://avatar.example/1"
                      }
                    }
                  },
                  "source": { "branch": { "name": "examplerepo" } },
                  "destination": { "branch": { "name": "master" } },
                  "links": {
                    "html": {
                      "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/273"
                    }
                  }
                },
                {
                  "id": 100,
                  "title": "Second PR",
                  "state": "MERGED",
                  "comment_count": 0,
                  "updated_on": "2026-09-28T10:00:00+00:00",
                  "author": { "display_name": "John Smith" },
                  "source": { "branch": { "name": "feature" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": {
                    "html": {
                      "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/100"
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

        pullRequests shouldHaveSize 2
        pullRequests[0].id shouldBe 273L
        pullRequests[0].title shouldBe "ABC-663 test"
        pullRequests[0].authorDisplayName shouldBe "John Smith"
        pullRequests[0].authorAvatarUrl shouldBe "https://avatar.example/1"
        pullRequests[0].sourceBranch shouldBe "examplerepo"
        pullRequests[0].destinationBranch shouldBe "master"
        pullRequests[0].commentCount shouldBe 1
        pullRequests[0].webUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/273"
        pullRequests[0].updatedOn shouldBe Instant.parse("2026-09-29T19:28:03.123456+00:00")
        pullRequests[0].state shouldBe PullRequestState.Open
        pullRequests[1].id shouldBe 100L
        pullRequests[1].state shouldBe PullRequestState.Merged
    }

    @Nested
    inner class CreatedOn {
        @Test
        fun `parsePullRequests - should read created_on when present`() {
            // GIVEN
            val json = """{"values":[${pullRequestJson(createdOnField = """"created_on": "2026-09-28T10:00:00+00:00",""")}]}"""

            // WHEN
            val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

            // THEN
            assertSoftly {
                pullRequests shouldHaveSize 1
                pullRequests[0].createdOn shouldBe Instant.parse("2026-09-28T10:00:00Z")
                pullRequests[0].updatedOn shouldBe Instant.parse("2026-09-29T10:00:00Z")
            }
        }

        @Test
        fun `parsePullRequests - should fall back to updated_on when created_on is absent`() {
            // GIVEN
            val json = """{"values":[${pullRequestJson(createdOnField = "")}]}"""

            // WHEN
            val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

            // THEN
            assertSoftly {
                pullRequests shouldHaveSize 1
                pullRequests[0].createdOn shouldBe Instant.parse("2026-09-29T10:00:00Z")
            }
        }

        private fun pullRequestJson(createdOnField: String): String {
            return """
                {
                  $createdOnField
                  "id": 1,
                  "title": "PR 1",
                  "state": "OPEN",
                  "updated_on": "2026-09-29T10:00:00+00:00",
                  "author": { "display_name": "John Smith" },
                  "source": { "branch": { "name": "feature" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": { "html": { "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/1" } }
                }
                """.trimIndent()
        }
    }

    @Nested
    inner class StateAndDraft {
        @Test
        fun `parsePullRequests - should map open merged and declined states`() {
            // GIVEN
            val json = valuesJson(
                pullRequestJson(id = 1, stateField = """"state": "OPEN""""),
                pullRequestJson(id = 2, stateField = """"state": "MERGED""""),
                pullRequestJson(id = 3, stateField = """"state": "DECLINED"""")
            )

            // WHEN
            val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

            // THEN
            pullRequests.map { it.state } shouldBe listOf(
                PullRequestState.Open,
                PullRequestState.Merged,
                PullRequestState.Declined
            )
        }

        @Test
        fun `parsePullRequests - should read draft true and default missing draft to false`() {
            // GIVEN
            val json = valuesJson(
                pullRequestJson(id = 1, stateField = """"state": "OPEN", "draft": true"""),
                pullRequestJson(id = 2, stateField = """"state": "OPEN"""")
            )

            // WHEN
            val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

            // THEN
            assertSoftly {
                pullRequests shouldHaveSize 2
                pullRequests[0].isDraft shouldBe true
                pullRequests[1].isDraft shouldBe false
            }
        }

        @Test
        fun `parsePullRequests - should skip superseded and missing states`() {
            // GIVEN
            val json = valuesJson(
                pullRequestJson(id = 1, stateField = """"state": "SUPERSEDED""""),
                pullRequestJson(id = 2, stateField = null),
                pullRequestJson(id = 3, stateField = """"state": "DECLINED"""")
            )

            // WHEN
            val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests(json)

            // THEN
            pullRequests.map { it.id } shouldBe listOf(3L)
        }

        private fun valuesJson(vararg entries: String): String {
            return """{"values":[${entries.joinToString(separator = ",")}]}"""
        }

        private fun pullRequestJson(id: Long, stateField: String?): String {
            val extra = stateField?.let { "$it," }.orEmpty()
            return """
                {
                  $extra
                  "id": $id,
                  "title": "PR $id",
                  "updated_on": "2026-09-28T10:00:00+00:00",
                  "source": { "branch": { "name": "feature" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": { "html": { "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/$id" } }
                }
                """.trimIndent()
        }
    }

    @Test
    fun `parsePullRequests - should return empty list when values missing`() {
        val pullRequests = BitbucketPullRequestResponseParser.parsePullRequests("""{"values":[]}""")

        pullRequests shouldHaveSize 0
    }

    @Test
    fun `parsePullRequestsPage - should return next url when present`() {
        // GIVEN
        val expectedNextUrl = "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests?page=2"
        val json = """{"values":[],"next":"$expectedNextUrl"}"""

        // WHEN
        val page = BitbucketPullRequestResponseParser.parsePullRequestsPage(json)

        // THEN
        assertSoftly(page) {
            pullRequests shouldHaveSize 0
            nextUrl shouldBe expectedNextUrl
        }
    }

    @Test
    fun `parsePullRequestsPage - should return null next url when absent`() {
        // GIVEN
        val json = """{"values":[]}"""

        // WHEN
        val page = BitbucketPullRequestResponseParser.parsePullRequestsPage(json)

        // THEN
        page.nextUrl.shouldBeNull()
    }

    @Nested
    inner class Counts {
        @Test
        fun `buildDiffstatUrl - should request size next and status fields with pagelen 100`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildDiffstatUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                pullRequestId = 273
            )

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/diffstat" +
                "?pagelen=100&fields=size,next,values.status"
        }

        @Test
        fun `buildCommitsUrl - should request next and hash fields with pagelen 100`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildCommitsUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                pullRequestId = 273
            )

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/commits" +
                "?pagelen=100&fields=next,values.hash"
        }

        @Test
        fun `parseCountPage - should read size when present`() {
            // GIVEN
            val json = """{"size":12,"values":[{"status":"modified"},{"status":"added"}]}"""

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCountPage(json)

            // THEN
            assertSoftly(page) {
                size shouldBe 12
                valueCount shouldBe 2
                nextUrl.shouldBeNull()
            }
        }

        @Test
        fun `parseCountPage - should count values and return next url when size absent`() {
            // GIVEN
            val expectedNextUrl = "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/commits?page=abc"
            val json = """{"values":[{"hash":"a"},{"hash":"b"},{"hash":"c"}],"next":"$expectedNextUrl"}"""

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCountPage(json)

            // THEN
            assertSoftly(page) {
                size.shouldBeNull()
                valueCount shouldBe 3
                nextUrl shouldBe expectedNextUrl
            }
        }
    }

    @Nested
    inner class Overview {
        @Test
        fun `buildCommitsBehindUrl - should include destination and exclude source branch`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildCommitsBehindUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                sourceBranch = "feature/ABC-663 test",
                destinationBranch = "main"
            ).toHttpUrl()

            // THEN
            assertSoftly(url) {
                encodedPath shouldBe "/2.0/repositories/my-workspace/examplerepo/commits"
                queryParameter("include") shouldBe "main"
                queryParameter("exclude") shouldBe "feature/ABC-663 test"
                queryParameter("pagelen") shouldBe "100"
                queryParameter("fields") shouldBe "next,values.hash"
            }
        }

        @Test
        fun `buildStatusesUrl - should request status fields with pagelen 100`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildStatusesUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                pullRequestId = 273
            )

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/statuses" +
                "?pagelen=100&fields=next,values.key,values.name,values.state,values.url,values.description,values.updated_on," +
                "values.commit.hash,values.links.commit.href"
        }

        @Test
        fun `parseBuildStatusesPage - should map every state and read next url`() {
            // GIVEN
            val expectedNextUrl = "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/statuses?page=2"
            val json =
                """
                {
                  "values": [
                    {"key":"jenkins","name":"examplerepo » ABC-663_test #1","state":"FAILED","url":"https://ci/1",
                     "description":"There was a failure building this commit.","updated_on":"2026-09-29T20:00:00.000000+00:00"},
                    {"key":"codacy","name":"Codacy/PR Quality Review","state":"SUCCESSFUL","url":"https://codacy/1"},
                    {"key":"running","name":"Running","state":"INPROGRESS","url":"https://ci/2"},
                    {"key":"stopped","name":"Stopped","state":"STOPPED","url":"https://ci/3"}
                  ],
                  "next": "$expectedNextUrl"
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseBuildStatusesPage(json)

            // THEN
            assertSoftly(page) {
                statuses.map { it.state } shouldBe listOf(BuildState.Failed, BuildState.Successful, BuildState.InProgress, BuildState.Stopped)
                statuses[0] shouldBe PullRequestBuildStatus(
                    key = "jenkins",
                    name = "examplerepo » ABC-663_test #1",
                    state = BuildState.Failed,
                    url = "https://ci/1",
                    description = "There was a failure building this commit.",
                    updatedOn = Instant.parse("2026-09-29T20:00:00Z")
                )
                statuses[1].description.shouldBeNull()
                statuses[1].updatedOn.shouldBeNull()
                nextUrl shouldBe expectedNextUrl
            }
        }

        @Test
        fun `parseBuildStatusesPage - should drop entries without name state or url`() {
            // GIVEN
            val json =
                """
                {"values":[
                  {"key":"a","state":"FAILED","url":"https://ci/a"},
                  {"key":"b","name":"B","state":"UNKNOWN","url":"https://ci/b"},
                  {"key":"c","name":"C","state":"SUCCESSFUL"},
                  {"name":"D","state":"SUCCESSFUL","url":"https://ci/d"}
                ]}
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseBuildStatusesPage(json)

            // THEN
            assertSoftly(page) {
                statuses shouldHaveSize 1
                statuses[0].key shouldBe "D"
                nextUrl.shouldBeNull()
            }
        }

        @Test
        fun `parseBuildStatusesPage - should read the commit hash and fall back to the commit link`() {
            // GIVEN
            val json =
                """
                {"values":[
                  {"key":"a","name":"A","state":"SUCCESSFUL","url":"https://ci/a","commit":{"hash":"abc123"},
                   "links":{"commit":{"href":"https://api.bitbucket.org/2.0/repositories/w/r/commit/ignored"}}},
                  {"key":"b","name":"B","state":"FAILED","url":"https://ci/b",
                   "links":{"commit":{"href":"https://api.bitbucket.org/2.0/repositories/w/r/commit/def456"}}},
                  {"key":"c","name":"C","state":"INPROGRESS","url":"https://ci/c"}
                ]}
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseBuildStatusesPage(json)

            // THEN
            assertSoftly(page) {
                statuses shouldHaveSize 3
                statuses[0].commitHash shouldBe "abc123"
                statuses[1].commitHash shouldBe "def456"
                statuses[2].commitHash.shouldBeNull()
            }
        }
    }

    @Nested
    inner class CommentFields {
        @Test
        fun `parseCommentsPage - should read the raw markdown when Bitbucket reports it`() {
            // GIVEN
            val json =
                """
                {"values":[
                  {
                    "id": 17,
                    "created_on": "2026-09-29T09:00:00+00:00",
                    "content": { "html": "<p>Look here</p>", "raw": "Look **here**" },
                    "user": { "display_name": "John Smith", "uuid": "{u1}" }
                  }
                ]}
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCommentsPage(json)

            // THEN the editor is seeded with the Markdown the author wrote, not the rendered HTML
            assertSoftly {
                page.comments shouldHaveSize 1
                page.comments[0].raw shouldBe "Look **here**"
            }
        }

        @Test
        fun `parseCommentsPage - should leave raw null when Bitbucket omits it`() {
            // GIVEN
            val json =
                """
                {"values":[
                  {
                    "id": 18,
                    "created_on": "2026-09-29T09:00:00+00:00",
                    "content": { "html": "<p>HTML only</p>" }
                  }
                ]}
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCommentsPage(json)

            // THEN
            page.comments[0].raw.shouldBeNull()
        }
    }

    @Nested
    inner class CommentUrls {
        @Test
        fun `buildCommentUrl - should address the single comment`() {
            // GIVEN
            val workspace = "my-workspace"
            val repoSlug = "examplerepo"
            val pullRequestId = 273L
            val commentId = 17L

            // WHEN
            val url = BitbucketPullRequestResponseParser.buildCommentUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId,
                commentId = commentId
            )

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/comments/17"
        }

        @Test
        fun `buildCommentResolveUrl - should address the resolve resource of the comment`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildCommentResolveUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                pullRequestId = 273,
                commentId = 17
            )

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/comments/17/resolve"
        }
    }

    @Nested
    inner class CommentBodies {
        @Test
        fun `buildCommentCreateBody - should omit the parent for a top level comment`() {
            // GIVEN
            val content = "Hello"

            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(content = content)

            // THEN
            body shouldBe """{"content":{"raw":"Hello"}}"""
        }

        @Test
        fun `buildCommentCreateBody - should carry the parent id of a reply`() {
            // GIVEN
            val content = "Hello"
            val parentCommentId = 17L

            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(content = content, parentCommentId = parentCommentId)

            // THEN
            body shouldBe """{"content":{"raw":"Hello"},"parent":{"id":17}}"""
        }

        @Test
        fun `buildCommentCreateBody - should include only the path for a file-scoped comment`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(
                content = "Review this file",
                inlinePath = "src/Foo.kt"
            )

            // THEN
            body shouldBe """{"content":{"raw":"Review this file"},"inline":{"path":"src/Foo.kt"}}"""
        }

        @Test
        fun `buildCommentCreateBody - should anchor an AFTER-side comment with inline to`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(
                content = "Nit here",
                inlinePath = "src/Foo.kt",
                inlineLine = 42,
                inlineSide = InlineCommentSide.AFTER
            )

            // THEN
            body shouldBe """{"content":{"raw":"Nit here"},"inline":{"path":"src/Foo.kt","to":42}}"""
        }

        @Test
        fun `buildCommentCreateBody - should anchor a BEFORE-side comment with inline from`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(
                content = "Was this removed on purpose?",
                inlinePath = "src/Foo.kt",
                inlineLine = 7,
                inlineSide = InlineCommentSide.BEFORE
            )

            // THEN
            body shouldBe """{"content":{"raw":"Was this removed on purpose?"},"inline":{"path":"src/Foo.kt","from":7}}"""
        }

        @Test
        fun `buildCommentCreateBody - should keep the path only when the line is not positive`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(
                content = "File note",
                inlinePath = "src/Foo.kt",
                inlineLine = 0,
                inlineSide = InlineCommentSide.AFTER
            )

            // THEN
            body shouldBe """{"content":{"raw":"File note"},"inline":{"path":"src/Foo.kt"}}"""
        }

        @Test
        fun `buildCommentUpdateBody - should send only the raw content`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentUpdateBody(content = "Edited")

            // THEN
            body shouldBe """{"content":{"raw":"Edited"}}"""
        }

        @Test
        fun `buildDescriptionUpdateBody - should retain the title and send description as a plain string`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildDescriptionUpdateBody(
                title = "Keep this title",
                description = "## Edited description"
            )

            // THEN
            body shouldBe """{"title":"Keep this title","description":"## Edited description"}"""
        }

        @Test
        fun `buildDescriptionUpdateBody - should JSON-escape multi-line Markdown rather than nest a raw object`() {
            // GIVEN
            val description = "line1\n\nline2"

            // WHEN
            val result = BitbucketPullRequestResponseParser.buildDescriptionUpdateBody(
                title = "t",
                description = description
            )

            // THEN
            val expected = buildJsonObject {
                put(key = "title", value = "t")
                put(key = "description", value = description)
            }.toString()
            result shouldBe expected
        }
    }

    @Test
    fun `parseErrorMessage - should combine message and detail from error response`() {
        // GIVEN
        val json =
            """
            {
              "type": "error",
              "error": {
                "message": "Bad request",
                "detail": "Invalid pagelen",
                "id": "d23a1cc5178f7637f3d9bf2d13824258"
              }
            }
            """.trimIndent()

        // WHEN
        val message = BitbucketPullRequestResponseParser.parseErrorMessage(json)

        // THEN
        message shouldBe "Bad request (Invalid pagelen)"
    }

    @Test
    fun `parseErrorMessage - should return message only when detail missing`() {
        // GIVEN
        val json = """{"type":"error","error":{"message":"Bad request"}}"""

        // WHEN
        val message = BitbucketPullRequestResponseParser.parseErrorMessage(json)

        // THEN
        message shouldBe "Bad request"
    }

    @Test
    fun `parseErrorMessage - should return null for non json body`() {
        // GIVEN
        val body = "<html>Bad Request</html>"

        // WHEN
        val message = BitbucketPullRequestResponseParser.parseErrorMessage(body)

        // THEN
        message.shouldBeNull()
    }

    @Nested
    inner class NewPullRequest {
        @Test
        fun `buildBranchesUrl - should include pagelen 100 sort by newest and name fields`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildBranchesUrl(workspace = "my-workspace", repoSlug = "examplerepo")

            // THEN
            val parsed = url.toHttpUrl()
            assertSoftly {
                parsed.encodedPath shouldBe "/2.0/repositories/my-workspace/examplerepo/refs/branches"
                parsed.queryParameter("pagelen") shouldBe "100"
                parsed.queryParameter("sort") shouldBe "-target.date"
                parsed.queryParameter("fields") shouldBe "next,values.name"
            }
        }

        @Test
        fun `buildOpenPullRequestForBranchesUrl - should build the open filter and escape quotes and backslashes`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildOpenPullRequestForBranchesUrl(
                workspace = "my-workspace",
                repoSlug = "examplerepo",
                sourceBranch = "feature/\"x\\y",
                destinationBranch = "main"
            )

            // THEN the decoded q reflects the raw, escaped branch names
            val q = url.toHttpUrl().queryParameter("q")
            assertSoftly {
                q shouldBe "source.branch.name=\"feature/\\\"x\\\\y\" AND destination.branch.name=\"main\" AND state=\"OPEN\""
                url.toHttpUrl().queryParameter("pagelen") shouldBe "1"
            }
        }

        @Test
        fun `escapeQueryValue - should escape backslashes before quotes`() {
            // WHEN
            val escaped = BitbucketPullRequestResponseParser.escapeQueryValue("a\\b\"c")

            // THEN
            escaped shouldBe "a\\\\b\\\"c"
        }

        @Test
        fun `buildCreatePullRequestBody - should carry title description branches reviewers and flags`() {
            // GIVEN
            val request = com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest(
                title = "ABC-1 do the thing",
                description = "Line one\nLine two with a \"quote\"",
                sourceBranch = "feature/x",
                destinationBranch = "main",
                reviewerUuids = listOf("{uuid-1}", "{uuid-2}"),
                closeSourceBranch = true,
                isDraft = false
            )

            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCreatePullRequestBody(request = request)
            val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject

            // THEN
            assertSoftly {
                json["title"]!!.jsonPrimitive.content shouldBe "ABC-1 do the thing"
                // description is a plain JSON string, not an object
                (json["description"] is kotlinx.serialization.json.JsonPrimitive) shouldBe true
                json["description"]!!.jsonPrimitive.content shouldBe "Line one\nLine two with a \"quote\""
                json["source"]!!.jsonObject["branch"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "feature/x"
                json["destination"]!!.jsonObject["branch"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "main"
                val reviewerUuids = json["reviewers"]!!.jsonArray.map { it.jsonObject["uuid"]!!.jsonPrimitive.content }
                reviewerUuids shouldBe listOf("{uuid-1}", "{uuid-2}")
                json["close_source_branch"]!!.jsonPrimitive.content shouldBe "true"
                json["draft"]!!.jsonPrimitive.content shouldBe "false"
            }
        }

        @Test
        fun `buildCreatePullRequestBody - should omit a blank description`() {
            // GIVEN
            val request = com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest(
                title = "no description",
                description = "   ",
                sourceBranch = "feature/x",
                destinationBranch = "main",
                reviewerUuids = emptyList(),
                closeSourceBranch = false,
                isDraft = true
            )

            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCreatePullRequestBody(request = request)
            val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject

            // THEN
            assertSoftly {
                json.containsKey("description") shouldBe false
                json["draft"]!!.jsonPrimitive.content shouldBe "true"
                json["close_source_branch"]!!.jsonPrimitive.content shouldBe "false"
            }
        }

        @Test
        fun `parseBranchNamesPage - should return the names and next and drop blanks`() {
            // GIVEN
            val body = """{"values":[{"name":"main"},{"name":""},{"name":"feature/x"}],"next":"https://api/next"}"""

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseBranchNamesPage(body)

            // THEN
            assertSoftly {
                page.names shouldBe listOf("main", "feature/x")
                page.nextUrl shouldBe "https://api/next"
            }
        }

        @Test
        fun `parseBranchNamesPage - should have a null next on the last page`() {
            // GIVEN
            val body = """{"values":[{"name":"main"}]}"""

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseBranchNamesPage(body)

            // THEN
            page.nextUrl.shouldBeNull()
        }

        @Test
        fun `parseMainBranchName - should return the name when present and null when absent or blank`() {
            // THEN
            assertSoftly {
                BitbucketPullRequestResponseParser.parseMainBranchName("""{"mainbranch":{"name":"develop"}}""") shouldBe "develop"
                BitbucketPullRequestResponseParser.parseMainBranchName("""{}""").shouldBeNull()
                BitbucketPullRequestResponseParser.parseMainBranchName("""{"mainbranch":{"name":""}}""").shouldBeNull()
            }
        }

        @Test
        fun `parseCreatedPullRequest - should return the summary for a created pull request body`() {
            // GIVEN a minimal 201-shaped body
            val body =
                """
                {
                  "id": 42,
                  "title": "ABC-1 do the thing",
                  "state": "OPEN",
                  "created_on": "2026-09-28T19:28:03.123456+00:00",
                  "updated_on": "2026-09-29T19:28:03.123456+00:00",
                  "source": { "branch": { "name": "feature/x" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": { "html": { "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42" } }
                }
                """.trimIndent()

            // WHEN
            val summary = BitbucketPullRequestResponseParser.parseCreatedPullRequest(body)

            // THEN
            assertSoftly {
                summary?.id shouldBe 42L
                summary?.sourceBranch shouldBe "feature/x"
                summary?.destinationBranch shouldBe "main"
                summary?.webUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42"
            }
        }

        @Test
        fun `parseCreatedPullRequest - should return null for a malformed body`() {
            // WHEN
            val summary = BitbucketPullRequestResponseParser.parseCreatedPullRequest("""{"id":1}""")

            // THEN missing required fields yield null
            summary.shouldBeNull()
        }
    }

    @Nested
    inner class UserLookupTests {
        @Test
        fun `buildUserUrl - should encode the braced account key as one path segment`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildUserUrl(uuid = "{11111111-1111-1111-1111-111111111111}").toHttpUrl()

            // THEN
            assertSoftly(url) {
                encodedPathSegments.last() shouldBe "%7B11111111-1111-1111-1111-111111111111%7D"
                queryParameter("fields") shouldBe "uuid,account_id,display_name,links.avatar.href"
            }
        }

        @Test
        fun `buildUserUrl - should keep an Atlassian account id unbraced in one path segment`() {
            // GIVEN
            val accountId = "712020:00000000-0000-0000-0000-000000000000"

            // WHEN
            val url = BitbucketPullRequestResponseParser.buildUserUrl(uuid = accountId).toHttpUrl()

            // THEN
            assertSoftly(url) {
                encodedPathSegments.last() shouldBe accountId
                encodedPathSegments.last().contains("%7B") shouldBe false
                encodedPathSegments.last().contains("%7D") shouldBe false
                queryParameter("fields") shouldBe "uuid,account_id,display_name,links.avatar.href"
            }
        }

        @Test
        fun `parseUser - should read the uuid, account id, display name, and avatar of an account`() {
            // GIVEN
            val body =
                """
                {
                  "uuid": "{11111111-1111-1111-1111-111111111111}",
                  "account_id": "712020:00000000-0000-0000-0000-000000000000",
                  "display_name": "Anna",
                  "links": {"avatar": {"href": "https://avatars.example/anna.png"}}
                }
                """.trimIndent()

            // WHEN
            val user = BitbucketPullRequestResponseParser.parseUser(responseBody = body)

            // THEN
            user shouldBe ActivityUser(
                uuid = "{11111111-1111-1111-1111-111111111111}",
                displayName = "Anna",
                avatarUrl = "https://avatars.example/anna.png"
            )
        }

        @Test
        fun `parseUser - should return null when the account has no display name`() {
            // GIVEN
            val body = """{"uuid": "{11111111-1111-1111-1111-111111111111}"}"""

            // WHEN
            val user = BitbucketPullRequestResponseParser.parseUser(responseBody = body)

            // THEN
            user.shouldBeNull()
        }
    }
}