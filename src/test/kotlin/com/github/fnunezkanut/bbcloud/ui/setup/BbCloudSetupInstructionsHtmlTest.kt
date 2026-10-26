package com.github.fnunezkanut.bbcloud.ui.setup

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudSetupInstructionsHtmlTest {
    private val intro = "This plugin requires a Bitbucket Cloud API token."
    private val scopes = BbCloudRequiredApiTokenScopes.entries.map { entry -> entry.scope }

    @Test
    fun `buildBody - should lead with the intro before the instructions`() {
        // GIVEN
        val apiTokensLinkLabel = BbCloudSetupInstructionsHtml.API_TOKENS_URL

        // WHEN
        val result = BbCloudSetupInstructionsHtml.buildBody(
            intro = intro,
            apiTokensLinkLabel = apiTokensLinkLabel
        )

        // THEN
        assertSoftly {
            result shouldContain "<p>$intro</p>"
            result.indexOf(intro) shouldBeLessThan result.indexOf("<ol>")
            result.indexOf(intro) shouldBeLessThan result.indexOf("<table")
        }
    }

    @Test
    fun `buildBody - should keep the api tokens url as the link target`() {
        // GIVEN
        val apiTokensLinkLabel = BbCloudSetupInstructionsHtml.API_TOKENS_URL

        // WHEN
        val result = BbCloudSetupInstructionsHtml.buildBody(
            intro = intro,
            apiTokensLinkLabel = apiTokensLinkLabel
        )

        // THEN
        assertSoftly {
            result shouldContain "<a href=\"${BbCloudSetupInstructionsHtml.API_TOKENS_URL}\">$apiTokensLinkLabel</a>"
            result shouldContain "Create API token with scopes"
        }
    }

    @Test
    fun `buildBody - should list every required scope`() {
        // GIVEN
        val apiTokensLinkLabel = BbCloudSetupInstructionsHtml.API_TOKENS_URL

        // WHEN
        val result = BbCloudSetupInstructionsHtml.buildBody(
            intro = intro,
            apiTokensLinkLabel = apiTokensLinkLabel
        )

        // THEN
        assertSoftly {
            scopes.forEach { scope ->
                result shouldContain scope
            }
            result shouldNotContain "read:account"
            result shouldNotContain "read:me"
            result shouldNotContain "read:project:bitbucket"
        }
    }

    @Test
    fun `buildBody - should number the token creation as a third step`() {
        // GIVEN
        val apiTokensLinkLabel = BbCloudSetupInstructionsHtml.API_TOKENS_URL

        // WHEN
        val result = BbCloudSetupInstructionsHtml.buildBody(
            intro = intro,
            apiTokensLinkLabel = apiTokensLinkLabel
        )

        // THEN
        assertSoftly {
            result shouldContain "Create a Bitbucket App API token with the following scopes:"
            result shouldNotContain "with these scopes:"
        }
    }

    @Test
    fun `buildBody - should render one table row per scope with its access level`() {
        // GIVEN
        val apiTokensLinkLabel = BbCloudSetupInstructionsHtml.API_TOKENS_URL

        // WHEN
        val result = BbCloudSetupInstructionsHtml.buildBody(
            intro = intro,
            apiTokensLinkLabel = apiTokensLinkLabel
        )

        // THEN
        assertSoftly {
            result shouldContain "<tr><th>Scope</th><th>Access</th></tr>"
            scopes.filter { scope -> scope.startsWith("read:") }.forEach { scope ->
                val row = rowContaining(html = result, scope = scope)
                row shouldContain "<td>$scope</td>"
                row shouldContain "<td>Read</td>"
            }
            val writeRow = rowContaining(html = result, scope = "write:pullrequest:bitbucket")
            writeRow shouldContain "<td>Write</td>"
        }
    }

    /** The markup from the row that holds [scope] up to the next row, so cell pairs can be asserted together. */
    private fun rowContaining(html: String, scope: String): String {
        return html.split("<tr>").first { row -> row.contains("<td>$scope</td>") }
    }
}