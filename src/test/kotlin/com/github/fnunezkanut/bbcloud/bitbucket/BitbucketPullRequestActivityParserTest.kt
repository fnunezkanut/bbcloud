package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class BitbucketPullRequestActivityParserTest {
    private val userJson =
        """
        {
          "display_name": "Name Lastname",
          "uuid": "{user}",
          "links": { "avatar": { "href": "https://avatar/128" } },
          "type": "user",
          "nickname": "Name"
        }
        """.trimIndent()
    private val expectedUser = ActivityUser(uuid = "{user}", displayName = "Name Lastname", avatarUrl = "https://avatar/128")

    @Nested
    inner class ActivityTests {
        @Test
        fun `parseActivityPage - should parse documented update and approval entries`() {
            // GIVEN
            val json =
                """
                {
                  "pagelen": 20,
                  "next": "https://api.bitbucket.org/2.0/next",
                  "values": [
                    {
                      "update": {
                        "description": "",
                        "title": "Small change",
                        "state": "MERGED",
                        "author": $userJson,
                        "date": "2019-05-10T06:48:25.305565+00:00"
                      }
                    },
                    {
                      "approval": {
                        "date": "2019-09-27T00:37:19.849534+00:00",
                        "user": $userJson
                      }
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseActivityPage(json)

            // THEN
            assertSoftly(page) {
                nextUrl shouldBe "https://api.bitbucket.org/2.0/next"
                items shouldBe listOf(
                    PullRequestActivityItem.Update(
                        date = Instant.parse("2019-05-10T06:48:25.305565Z"),
                        user = expectedUser,
                        state = PullRequestState.Merged
                    ),
                    PullRequestActivityItem.Approval(date = Instant.parse("2019-09-27T00:37:19.849534Z"), user = expectedUser)
                )
            }
        }

        @Test
        fun `parseActivityPage - should skip unknown entry kinds and keep unmodeled states as null`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    { "changes_requested": { "date": "2026-09-30T10:00:00Z", "user": $userJson } },
                    {},
                    { "update": { "state": "SUPERSEDED", "author": $userJson, "date": "2026-09-30T11:00:00Z" } },
                    { "update": { "state": "OPEN", "author": $userJson } }
                  ]
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseActivityPage(json)

            // THEN
            assertSoftly(page) {
                nextUrl.shouldBeNull()
                items shouldBe listOf(
                    PullRequestActivityItem.Update(date = Instant.parse("2026-09-30T11:00:00Z"), user = expectedUser, state = null)
                )
            }
        }

        @Test
        fun `buildActivityUrl - should exclude comments and the embedded pull request`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildActivityUrl(workspace = "ws", repoSlug = "repo", pullRequestId = 273).toHttpUrl()

            // THEN
            assertSoftly(url) {
                encodedPath shouldBe "/2.0/repositories/ws/repo/pullrequests/273/activity"
                queryParameter("fields") shouldBe "-values.comment,-values.pull_request"
                queryParameter("pagelen").shouldBeNull()
            }
        }
    }

    @Nested
    inner class CommentTests {
        @Test
        fun `parseCommentsPage - should parse inline reply with resolution`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    {
                      "id": 2,
                      "parent": { "id": 1 },
                      "created_on": "2026-09-30T10:00:00+00:00",
                      "deleted": false,
                      "pending": false,
                      "content": { "html": "<p>Looks good</p>" },
                      "inline": { "path": "src/Main.kt", "to": 12, "from": null },
                      "resolution": {
                        "type": "comment_resolution",
                        "created_on": "2026-09-30T12:00:00+00:00",
                        "user": {
                          "uuid": "{resolver}",
                          "display_name": "Reviewer",
                          "links": { "avatar": { "href": "https://avatar/32" } }
                        }
                      },
                      "user": $userJson
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCommentsPage(json)

            // THEN
            page.comments shouldBe listOf(
                PullRequestComment(
                    id = 2,
                    parentId = 1,
                    createdOn = Instant.parse("2026-09-30T10:00:00Z"),
                    author = expectedUser,
                    html = "<p>Looks good</p>",
                    inlinePath = "src/Main.kt",
                    inlineLine = 12,
                    inlineSide = InlineCommentSide.AFTER,
                    isResolved = true,
                    isDeleted = false,
                    isPending = false,
                    resolvedBy = ActivityUser(
                        uuid = "{resolver}",
                        displayName = "Reviewer",
                        avatarUrl = "https://avatar/32"
                    ),
                    resolvedOn = Instant.parse("2026-09-30T12:00:00Z")
                )
            )
        }

        @Test
        fun `parseCommentsPage - should parse Bitbucket multi-line to range`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    {
                      "id": 8,
                      "created_on": "2026-09-30T13:00:00Z",
                      "content": { "html": "<p>Range</p>" },
                      "inline": {
                        "path": "src/Main.kt",
                        "to": 160,
                        "from": null,
                        "start_to": 120,
                        "start_from": null
                      },
                      "user": $userJson
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val comment = BitbucketPullRequestResponseParser.parseCommentsPage(json).comments.single()

            // THEN
            assertSoftly(comment) {
                inlineLine shouldBe 120
                inlineLineEnd shouldBe 160
                inlineSide shouldBe InlineCommentSide.AFTER
            }
        }

        @Test
        fun `parseCommentsPage - should leave the resolver empty for unresolved and unresolved-detail comments`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    { "id": 6, "created_on": "2026-09-30T10:00:00Z", "content": { "html": "<p>Open</p>" }, "user": $userJson },
                    {
                      "id": 7,
                      "created_on": "2026-09-30T11:00:00Z",
                      "content": { "html": "<p>Closed</p>" },
                      "resolution": { "type": "comment_resolution" },
                      "user": $userJson
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val comments = BitbucketPullRequestResponseParser.parseCommentsPage(json).comments

            // THEN
            assertSoftly(comments) {
                first().also { comment ->
                    comment.isResolved shouldBe false
                    comment.resolvedBy.shouldBeNull()
                    comment.resolvedOn.shouldBeNull()
                }
                last().also { comment ->
                    comment.isResolved shouldBe true
                    comment.resolvedBy.shouldBeNull()
                    comment.resolvedOn.shouldBeNull()
                }
            }
        }

        @Test
        fun `parseCommentsPage - should parse top-level deleted and pending flags and use the old line of removed lines`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    { "id": 3, "created_on": "2026-09-30T10:00:00Z", "deleted": true, "content": { "html": "" }, "user": $userJson },
                    { "id": 4, "created_on": "2026-09-30T11:00:00Z", "pending": true, "inline": { "path": "a.kt", "from": 7 } },
                    { "id": 5 }
                  ]
                }
                """.trimIndent()

            // WHEN
            val comments = BitbucketPullRequestResponseParser.parseCommentsPage(json).comments

            // THEN
            comments shouldHaveSize 2
            assertSoftly(comments[0]) {
                parentId.shouldBeNull()
                isDeleted shouldBe true
                isResolved shouldBe false
                inlinePath.shouldBeNull()
            }
            assertSoftly(comments[1]) {
                isPending shouldBe true
                author.shouldBeNull()
                inlineLine shouldBe 7
            }
        }
    }

    @Nested
    inner class CommitTests {
        @Test
        fun `parseCommitsPage - should parse commits with and without links and linked accounts`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    {
                      "hash": "728c8bad1813",
                      "date": "2026-09-30T10:00:00+00:00",
                      "message": "Fix parser\n\nDetails",
                      "author": { "raw": "Name Lastname <name@example.com>", "user": $userJson },
                      "links": { "html": { "href": "https://bitbucket.org/ws/repo/commits/728c8bad1813" } }
                    },
                    {
                      "hash": "6a2c16e4a152",
                      "date": "2026-09-30T09:00:00+00:00",
                      "message": "Initial",
                      "author": { "raw": "Jane Doe <jane@example.com>" }
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val commits = BitbucketPullRequestResponseParser.parseCommitsPage(json).commits

            // THEN
            commits shouldBe listOf(
                PullRequestCommit(
                    hash = "728c8bad1813",
                    message = "Fix parser\n\nDetails",
                    date = Instant.parse("2026-09-30T10:00:00Z"),
                    author = expectedUser,
                    url = "https://bitbucket.org/ws/repo/commits/728c8bad1813"
                ),
                PullRequestCommit(
                    hash = "6a2c16e4a152",
                    message = "Initial",
                    date = Instant.parse("2026-09-30T09:00:00Z"),
                    author = ActivityUser(uuid = null, displayName = "Jane Doe", avatarUrl = null),
                    url = null
                )
            )
        }

        @Test
        fun `parseCommitsPage - should return no commits for an empty page`() {
            // WHEN
            val page = BitbucketPullRequestResponseParser.parseCommitsPage("""{ "values": [] }""")

            // THEN
            page.commits.shouldBeEmpty()
        }
    }

    @Nested
    inner class TaskTests {
        @Test
        fun `parseTasksPage - should parse creator and resolver`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    {
                      "id": 9,
                      "state": "RESOLVED",
                      "content": { "raw": "Update the changelog" },
                      "created_on": "2026-09-30T10:00:00Z",
                      "creator": $userJson,
                      "resolved_on": "2026-09-30T12:00:00Z",
                      "resolved_by": $userJson
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val tasks = BitbucketPullRequestResponseParser.parseTasksPage(json).tasks

            // THEN
            tasks shouldBe listOf(
                PullRequestTask(
                    id = 9,
                    content = "Update the changelog",
                    isResolved = true,
                    createdOn = Instant.parse("2026-09-30T10:00:00Z"),
                    creator = expectedUser,
                    resolvedOn = Instant.parse("2026-09-30T12:00:00Z"),
                    resolvedBy = expectedUser
                )
            )
        }

        @Test
        fun `parseTasksPage - should ignore a stale resolution on an unresolved task`() {
            // GIVEN
            val json =
                """
                { "values": [ { "id": 9, "state": "UNRESOLVED", "content": { "raw": "Task" }, "resolved_on": "2026-09-30T12:00:00Z" } ] }
                """.trimIndent()

            // WHEN
            val task = BitbucketPullRequestResponseParser.parseTasksPage(json).tasks.single()

            // THEN
            assertSoftly(task) {
                isResolved shouldBe false
                resolvedOn.shouldBeNull()
                createdOn.shouldBeNull()
            }
        }

        @Test
        fun `buildCommentCreateUrl - should target the comments collection without query`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildCommentCreateUrl(workspace = "ws", repoSlug = "repo", pullRequestId = 273)

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/ws/repo/pullrequests/273/comments"
        }

        @Test
        fun `buildCommentCreateBody - should send the Markdown as raw content and escape it`() {
            // GIVEN
            val markdown = "**Looks \"good\"**\n- item"

            // WHEN
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(markdown)

            // THEN
            body shouldBe """{"content":{"raw":"**Looks \"good\"**\n- item"}}"""
        }

        @Test
        fun `buildTasksUrl - should request creation and resolution fields`() {
            // WHEN
            val fields = BitbucketPullRequestResponseParser.buildTasksUrl(workspace = "ws", repoSlug = "repo", pullRequestId = 273)
                .toHttpUrl()
                .queryParameter("fields")
                .shouldNotBeNull()

            // THEN
            fields.split(',') shouldBe listOf(
                "next",
                "values.id",
                "values.state",
                "values.content.raw",
                "values.created_on",
                "values.resolved_on",
                "values.creator.uuid",
                "values.creator.display_name",
                "values.creator.links.avatar.href",
                "values.resolved_by.uuid",
                "values.resolved_by.display_name",
                "values.resolved_by.links.avatar.href"
            )
        }
    }
}