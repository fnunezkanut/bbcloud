package com.github.fnunezkanut.bbcloud.git

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommitMessageParserTest {
    @Nested
    inner class Title {
        @Test
        fun `parse - subject and body - title is the first line and description is the body`() {
            // GIVEN
            val raw = "Add feature\n\nFirst line\nSecond line\n"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            assertSoftly {
                result.title shouldBe "Add feature"
                result.description shouldBe "First line\nSecond line"
            }
        }

        @Test
        fun `parse - single line message - description is empty`() {
            // GIVEN
            val raw = "Fix typo\n"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            assertSoftly {
                result.title shouldBe "Fix typo"
                result.description shouldBe ""
            }
        }

        @Test
        fun `parse - body directly after the subject without a blank line - keeps every body line`() {
            // GIVEN
            val raw = "Subject\nBody line"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            assertSoftly {
                result.title shouldBe "Subject"
                result.description shouldBe "Body line"
            }
        }

        @Test
        fun `parse - CRLF line endings - title has no carriage return`() {
            // GIVEN
            val raw = "Subject line\r\n\r\nBody\r\n"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            assertSoftly {
                result.title shouldBe "Subject line"
                result.description shouldBe "Body"
            }
        }

        @Test
        fun `parse - trailing blank lines - description has no trailing blank lines`() {
            // GIVEN
            val raw = "Subj\n\nBody\n\n\n"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            result.description shouldBe "Body"
        }

        @Test
        fun `parse - empty input - title and description are empty`() {
            // GIVEN
            val raw = ""

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            assertSoftly {
                result.title shouldBe ""
                result.description shouldBe ""
            }
        }

        @Test
        fun `parse - internal blank lines in the body - are preserved`() {
            // GIVEN
            val raw = "Subj\n\nPara one\n\nPara two"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = raw)

            // THEN
            result.description shouldBe "Para one\n\nPara two"
        }

        @Test
        fun `parse - subject of exactly 255 characters - title is unchanged`() {
            // GIVEN
            val subject = "a".repeat(CommitMessageParser.MAX_TITLE_LENGTH)

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = "$subject\n\nBody")

            // THEN
            result.title shouldBe subject
        }

        @Test
        fun `parse - subject longer than 255 characters - title is truncated to the first 255 characters`() {
            // GIVEN
            val subject = "a".repeat(CommitMessageParser.MAX_TITLE_LENGTH) + "bbbb"

            // WHEN
            val result = CommitMessageParser.parse(rawMessage = "$subject\n\nBody")

            // THEN
            assertSoftly {
                result.title.length shouldBe CommitMessageParser.MAX_TITLE_LENGTH
                result.title shouldBe "a".repeat(CommitMessageParser.MAX_TITLE_LENGTH)
                result.description shouldBe "Body"
            }
        }
    }
}