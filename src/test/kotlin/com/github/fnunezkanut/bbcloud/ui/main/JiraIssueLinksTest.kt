package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class JiraIssueLinksTest {
    @Nested
    inner class JiraBaseUrl {
        @Test
        fun `jiraBaseUrl - should derive atlassian url from workspace url with trailing slash`() {
            // WHEN
            val url = JiraIssueLinks.jiraBaseUrl("https://bitbucket.org/example-org/")

            // THEN
            url shouldBe "https://example-org.atlassian.net"
        }

        @Test
        fun `jiraBaseUrl - should derive atlassian url from workspace url without trailing slash`() {
            // WHEN
            val url = JiraIssueLinks.jiraBaseUrl("https://bitbucket.org/example-org")

            // THEN
            url shouldBe "https://example-org.atlassian.net"
        }

        @Test
        fun `jiraBaseUrl - should lowercase the workspace`() {
            // WHEN
            val url = JiraIssueLinks.jiraBaseUrl("https://bitbucket.org/Example-Org/")

            // THEN
            url shouldBe "https://example-org.atlassian.net"
        }

        @Test
        fun `jiraBaseUrl - should return null when workspace is not a valid host label`() {
            // WHEN
            val url = JiraIssueLinks.jiraBaseUrl("https://bitbucket.org/my_workspace/")

            // THEN
            url.shouldBeNull()
        }

        @Test
        fun `jiraBaseUrl - should return null for blank or non bitbucket url`() {
            // WHEN
            val blank = JiraIssueLinks.jiraBaseUrl("  ")
            val other = JiraIssueLinks.jiraBaseUrl("https://github.com/example-org/")

            // THEN
            blank.shouldBeNull()
            other.shouldBeNull()
        }
    }

    @Nested
    inner class FormatTitleHtml {
        private val baseUrl = "https://example-org.atlassian.net"

        @Test
        fun `formatTitleHtml - should link key at the start of the title`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "ABC-663 test", jiraBaseUrl = baseUrl)

            // THEN
            html shouldBe """<a href="https://example-org.atlassian.net/browse/ABC-663">ABC-663</a> test"""
        }

        @Test
        fun `formatTitleHtml - should link key in the middle of the title`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "Fix for ABC-12 by John Smith", jiraBaseUrl = baseUrl)

            // THEN
            html shouldBe """Fix for <a href="https://example-org.atlassian.net/browse/ABC-12">ABC-12</a> by John Smith"""
        }

        @Test
        fun `formatTitleHtml - should link every key`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "ABC-1, ABC-2", jiraBaseUrl = baseUrl)

            // THEN
            html shouldBe """<a href="https://example-org.atlassian.net/browse/ABC-1">ABC-1</a>, """ +
                """<a href="https://example-org.atlassian.net/browse/ABC-2">ABC-2</a>"""
        }

        @Test
        fun `formatTitleHtml - should not link lowercase or dashless keys`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "abc-663 ABC663", jiraBaseUrl = baseUrl)

            // THEN
            html shouldBe "abc-663 ABC663"
        }

        @Test
        fun `formatTitleHtml - should escape html special characters`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "<b>A & B</b> ABC-7", jiraBaseUrl = baseUrl)

            // THEN
            html shouldBe """&lt;b&gt;A &amp; B&lt;/b&gt; <a href="https://example-org.atlassian.net/browse/ABC-7">ABC-7</a>"""
        }

        @Test
        fun `formatTitleHtml - should not add links when base url is null`() {
            // WHEN
            val html = JiraIssueLinks.formatTitleHtml(title = "ABC-663 <test>", jiraBaseUrl = null)

            // THEN
            html shouldBe "ABC-663 &lt;test&gt;"
        }
    }
}