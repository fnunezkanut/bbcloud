package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketPullRequestDetailsParserTest {
    private val currentUserUuid = "{11111111-1111-1111-1111-111111111111}"
    private val otherUserUuid = "{22222222-2222-2222-2222-222222222222}"

    @Nested
    inner class CurrentUserTests {
        @Test
        fun `parseCurrentUser - should return uuid and avatar`() {
            // GIVEN
            val json = """{ "uuid": "$currentUserUuid", "links": { "avatar": { "href": "https://avatars/me.png" } } }"""

            // WHEN
            val user = BitbucketPullRequestResponseParser.parseCurrentUser(json)

            // THEN
            user shouldBe CurrentUser(uuid = currentUserUuid, avatarUrl = "https://avatars/me.png")
        }

        @Test
        fun `parseCurrentUser - should have no avatar when links missing`() {
            // GIVEN
            val json = """{ "uuid": "$currentUserUuid" }"""

            // WHEN
            val user = BitbucketPullRequestResponseParser.parseCurrentUser(json)

            // THEN
            user shouldBe CurrentUser(uuid = currentUserUuid, avatarUrl = null)
        }

        @Test
        fun `parseCurrentUser - should return null when uuid blank`() {
            // GIVEN
            val json = """{ "uuid": "" }"""

            // WHEN
            val user = BitbucketPullRequestResponseParser.parseCurrentUser(json)

            // THEN
            user.shouldBeNull()
        }

        @Test
        fun `buildCurrentUserUrl - should request uuid and avatar fields`() {
            // WHEN
            val fields = BitbucketPullRequestResponseParser.buildCurrentUserUrl().toHttpUrl().queryParameter("fields")

            // THEN
            fields shouldBe "uuid,links.avatar.href"
        }
    }

    @Nested
    inner class DetailsTests {
        @Test
        fun `parsePullRequestDetails - should report approved when current user approved`() {
            // GIVEN
            val json = pullRequestJson(participants = participant(uuid = currentUserUuid, approved = true))

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = currentUserUuid)

            // THEN
            details.shouldNotBeNull()
            assertSoftly(details) {
                isApprovedByCurrentUser shouldBe true
                summary.id shouldBe 273L
                summary.state shouldBe PullRequestState.Open
                summary.isDraft shouldBe true
            }
        }

        @Test
        fun `parsePullRequestDetails - should report not approved when current user is not a participant`() {
            // GIVEN
            val json = pullRequestJson(participants = participant(uuid = otherUserUuid, approved = true))

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = currentUserUuid)

            // THEN
            details?.isApprovedByCurrentUser shouldBe false
        }

        @Test
        fun `parsePullRequestDetails - should report not approved when current user participant has not approved`() {
            // GIVEN
            val json = pullRequestJson(participants = participant(uuid = currentUserUuid, approved = false))

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = currentUserUuid)

            // THEN
            details?.isApprovedByCurrentUser shouldBe false
        }

        @Test
        fun `parsePullRequestDetails - should report not approved when current user uuid unknown`() {
            // GIVEN
            val json = pullRequestJson(participants = participant(uuid = currentUserUuid, approved = true))

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = null)

            // THEN
            details?.isApprovedByCurrentUser shouldBe false
        }

        @Test
        fun `parsePullRequestDetails - should prefer raw rendered description`() {
            // GIVEN
            val json = pullRequestJson(
                participants = "",
                descriptionFields =
                    """
                    "rendered": { "description": { "raw": "## Markdown", "html": "<h2>HTML</h2>" } },
                    """.trimIndent()
            )

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = null)

            // THEN
            details?.description shouldBe "## Markdown"
        }

        @Test
        fun `parsePullRequestDetails - should fall back to rendered html description`() {
            // GIVEN
            val json = pullRequestJson(
                participants = "",
                descriptionFields =
                    """
                    "rendered": { "description": { "html": "<p>HTML description</p>" } },
                    """.trimIndent()
            )

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = null)

            // THEN
            details?.description shouldBe "<p>HTML description</p>"
        }

        @Test
        fun `parsePullRequestDetails - should fall back to top-level description`() {
            // GIVEN
            val json = pullRequestJson(
                participants = "",
                descriptionFields =
                    """
                    "description": "Legacy description",
                    """.trimIndent()
            )

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = null)

            // THEN
            details?.description shouldBe "Legacy description"
        }

        @Test
        fun `parsePullRequestDetails - should return null when description is absent`() {
            // GIVEN
            val json = pullRequestJson(participants = "")

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = null)

            // THEN
            details?.description.shouldBeNull()
        }
    }

    @Nested
    inner class ReviewerTests {
        @Test
        fun `parsePullRequestDetails - should map reviewers with their participant state`() {
            // GIVEN
            val json = pullRequestJson(
                participants = listOf(
                    """{ "user": { "uuid": "$currentUserUuid" }, "approved": true, "state": "approved", "role": "REVIEWER" }""",
                    """{ "user": { "uuid": "$otherUserUuid" }, "approved": false, "state": "changes_requested", "role": "REVIEWER" }"""
                ).joinToString(separator = ","),
                reviewers = listOf(
                    reviewer(uuid = currentUserUuid, displayName = "John Smith"),
                    reviewer(uuid = otherUserUuid, displayName = "Jane Doe"),
                    reviewer(uuid = "{33333333-3333-3333-3333-333333333333}", displayName = "PR Ranger")
                ).joinToString(separator = ",")
            )

            // WHEN
            val details = BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = json, currentUserUuid = currentUserUuid)

            // THEN
            details.shouldNotBeNull()
            assertSoftly(details) {
                authorUuid shouldBe AUTHOR_UUID
                reviewers.map { reviewer -> reviewer.displayName } shouldBe listOf("John Smith", "Jane Doe", "PR Ranger")
                reviewers.map { reviewer -> reviewer.status } shouldBe listOf(
                    ReviewerStatus.Approved,
                    ReviewerStatus.ChangesRequested,
                    ReviewerStatus.None
                )
                reviewers.first().avatarUrl shouldBe "https://avatar/John Smith"
            }
        }

        @Test
        fun `buildReviewersUpdateBody - should include title and reviewer uuids`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildReviewersUpdateBody(title = "ABC-663 test", reviewerUuids = listOf("{a}", "{b}"))

            // THEN
            body shouldBe """{"title":"ABC-663 test","reviewers":[{"uuid":"{a}"},{"uuid":"{b}"}]}"""
        }

        @Test
        fun `parseWorkspaceMembersPage - should map members and skip entries without uuid`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    { "user": { "uuid": "{a}", "display_name": "Anna Lee", "nickname": "alee", "links": { "avatar": { "href": "https://avatar/a" } } } },
                    { "user": { "display_name": "No Uuid" } },
                    { "user": { "uuid": "{b}", "nickname": "bnick" } }
                  ],
                  "next": "https://api.bitbucket.org/2.0/workspaces/my-workspace/members?page=2"
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseWorkspaceMembersPage(json)

            // THEN
            assertSoftly(page) {
                members shouldBe listOf(
                    ReviewerCandidate(uuid = "{a}", displayName = "Anna Lee", nickname = "alee", avatarUrl = "https://avatar/a"),
                    ReviewerCandidate(uuid = "{b}", displayName = "bnick", nickname = "bnick", avatarUrl = null)
                )
                nextUrl shouldBe "https://api.bitbucket.org/2.0/workspaces/my-workspace/members?page=2"
            }
        }

        @Test
        fun `parseDefaultReviewerUuids - should return reviewer uuids`() {
            // GIVEN
            val json = """{ "values": [ { "user": { "uuid": "{a}" }, "reviewer_type": "project" }, { "user": { "uuid": "{b}" } } ] }"""

            // WHEN
            val uuids = BitbucketPullRequestResponseParser.parseDefaultReviewerUuids(json)

            // THEN
            uuids shouldBe setOf("{a}", "{b}")
        }

        private fun reviewer(uuid: String, displayName: String): String {
            return """{ "uuid": "$uuid", "display_name": "$displayName", "links": { "avatar": { "href": "https://avatar/$displayName" } } }"""
        }
    }

    @Nested
    inner class TaskTests {
        @Test
        fun `parseTasksPage - should parse resolved and unresolved tasks and the next link`() {
            // GIVEN
            val json =
                """
                {
                  "next": "https://api.bitbucket.org/2.0/next",
                  "values": [
                    { "id": 11, "state": "UNRESOLVED", "content": { "raw": "test task" } },
                    { "id": 12, "state": "RESOLVED", "content": { "raw": "test task 2" } },
                    { "state": "RESOLVED", "content": { "raw": "without id" } }
                  ]
                }
                """.trimIndent()

            // WHEN
            val page = BitbucketPullRequestResponseParser.parseTasksPage(json)

            // THEN
            assertSoftly(page) {
                tasks shouldBe listOf(
                    PullRequestTask(id = 11, content = "test task", isResolved = false),
                    PullRequestTask(id = 12, content = "test task 2", isResolved = true)
                )
                nextUrl shouldBe "https://api.bitbucket.org/2.0/next"
            }
        }

        @Test
        fun `buildTaskCreateBody - should send only the raw content`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildTaskCreateBody("Add tests")

            // THEN
            body shouldBe """{"content":{"raw":"Add tests"}}"""
        }

        @Test
        fun `buildTaskUpdateBody - should leave out the content when only the state changes`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildTaskUpdateBody(content = null, resolved = false)

            // THEN
            body shouldBe """{"state":"UNRESOLVED"}"""
        }

        @Test
        fun `buildTaskUpdateBody - should leave out the state when only the content changes`() {
            // WHEN
            val body = BitbucketPullRequestResponseParser.buildTaskUpdateBody(content = "Renamed", resolved = null)

            // THEN
            body shouldBe """{"content":{"raw":"Renamed"}}"""
        }

        @Test
        fun `buildTaskUrl - should address the task under the pull request`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildTaskUrl(workspace = "ws", repoSlug = "repo", pullRequestId = 273, taskId = 11)

            // THEN
            url shouldBe "https://api.bitbucket.org/2.0/repositories/ws/repo/pullrequests/273/tasks/11"
        }
    }

    @Nested
    inner class MergeabilityCheckTests {
        @Test
        fun `parseMergeabilityChecks - should keep standard and custom checks only`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    { "type": "pullrequest_state_check", "status": "PASSED", "required": true, "blocking": false, "state": "OPEN" },
                    { "type": "current_user_permission_check", "status": "PASSED", "required": true, "blocking": false },
                    {
                      "type": "standard_merge_check", "status": "FAILED", "required": false, "blocking": false,
                      "check": { "type": "standard_merge_check_definition", "kind": "minimum_approvals" },
                      "requirement": { "minimum_approvals": 1 }, "observed": { "approval_count": 0 }
                    },
                    {
                      "type": "standard_merge_check", "status": "PASSED", "required": true, "blocking": false,
                      "check": { "type": "standard_merge_check_definition", "kind": "resolved_tasks" },
                      "requirement": {}, "observed": { "unresolved_task_count": 0 }
                    },
                    {
                      "type": "custom_pre_merge_check", "status": "PENDING", "required": true, "blocking": true,
                      "check": { "type": "custom_merge_check_definition", "id": "ari:1", "name": "Security scan" }
                    }
                  ]
                }
                """.trimIndent()

            // WHEN
            val checks = BitbucketPullRequestResponseParser.parseMergeabilityChecks(json)

            // THEN
            checks shouldBe listOf(
                PullRequestMergeCheck(
                    kind = "minimum_approvals",
                    name = null,
                    status = MergeCheckStatus.Failed,
                    isBlocking = false,
                    requiredValue = 1,
                    observedValue = 0
                ),
                PullRequestMergeCheck(
                    kind = "resolved_tasks",
                    name = null,
                    status = MergeCheckStatus.Passed,
                    isBlocking = false,
                    requiredValue = null,
                    observedValue = 0
                ),
                PullRequestMergeCheck(
                    kind = PullRequestMergeCheck.CUSTOM_KIND,
                    name = "Security scan",
                    status = MergeCheckStatus.Pending,
                    isBlocking = true,
                    requiredValue = null,
                    observedValue = null
                )
            )
        }

        @Test
        fun `parseMergeabilityChecks - should keep unknown kinds and treat unknown status as Unknown`() {
            // GIVEN
            val json =
                """
                {
                  "values": [
                    {
                      "type": "standard_merge_check", "status": "SOMETHING_NEW", "required": false, "blocking": false,
                      "check": { "kind": "no_failed_builds" }, "requirement": {}, "observed": { "failed_build_count": 2 }
                    },
                    { "type": "standard_merge_check", "status": "PASSED", "required": false, "blocking": false, "check": {} }
                  ]
                }
                """.trimIndent()

            // WHEN
            val checks = BitbucketPullRequestResponseParser.parseMergeabilityChecks(json)

            // THEN
            checks shouldBe listOf(
                PullRequestMergeCheck(
                    kind = "no_failed_builds",
                    name = null,
                    status = MergeCheckStatus.Unknown,
                    isBlocking = false,
                    requiredValue = null,
                    observedValue = 2
                )
            )
        }

        @Test
        fun `parseMergeabilityChecks - should return empty list when values missing`() {
            // WHEN
            val checks = BitbucketPullRequestResponseParser.parseMergeabilityChecks("""{ "size": 0 }""")

            // THEN
            checks shouldBe emptyList()
        }

        @Test
        fun `buildMergeabilityChecksUrl - should exclude the git mergeability check`() {
            // WHEN
            val url = BitbucketPullRequestResponseParser.buildMergeabilityChecksUrl(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273)

            // THEN
            url.toHttpUrl().queryParameter("q") shouldBe "type!=\"git_mergeability_check\""
            url.toHttpUrl().encodedPath shouldBe "/2.0/repositories/my-workspace/examplerepo/pullrequests/273/mergeability/checks"
        }
    }

    @Test
    fun `buildDraftUpdateBody - should include title and draft flag`() {
        // WHEN
        val body = BitbucketPullRequestResponseParser.buildDraftUpdateBody(title = "ABC-663 test", isDraft = true)

        // THEN
        body shouldBe """{"title":"ABC-663 test","draft":true}"""
    }

    @Test
    fun `buildApproveUrl - should target the approve endpoint of the pull request`() {
        // WHEN
        val url = BitbucketPullRequestResponseParser.buildApproveUrl(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273)

        // THEN
        url shouldBe "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/273/approve"
    }

    private fun participant(uuid: String, approved: Boolean): String {
        return """{ "user": { "uuid": "$uuid" }, "approved": $approved, "role": "REVIEWER" }"""
    }

    private fun pullRequestJson(participants: String, reviewers: String = "", descriptionFields: String = ""): String {
        return """
            {
              "id": 273,
              "title": "ABC-663 test",
              "state": "OPEN",
              "draft": true,
              "updated_on": "2026-09-29T19:28:03.123456+00:00",
              "author": { "uuid": "$AUTHOR_UUID", "display_name": "John Smith" },
              "source": { "branch": { "name": "ABC-663_test" } },
              "destination": { "branch": { "name": "master" } },
              "links": { "html": { "href": "https://bitbucket.org/my-workspace/examplerepo/pull-requests/273" } },
              $descriptionFields
              "participants": [ $participants ],
              "reviewers": [ $reviewers ]
            }
            """.trimIndent()
    }

    companion object {
        private const val AUTHOR_UUID = "{00000000-0000-0000-0000-000000000000}"
    }
}