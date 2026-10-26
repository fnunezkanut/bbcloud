package com.github.fnunezkanut.bbcloud.ui.setup

object BbCloudSetupInstructionsHtml {
    const val API_TOKENS_URL = "https://id.atlassian.com/manage-profile/security/api-tokens"

    fun buildBody(intro: String, apiTokensLinkLabel: String): String {
        return """
            <p>$intro</p>
            <ol>
              <li>Go to <a href="$API_TOKENS_URL">$apiTokensLinkLabel</a></li>
              <li>Click &quot;Create API token with scopes&quot;</li>
              <li>Create a Bitbucket App API token with the following scopes:
                <table border="1" cellpadding="4" cellspacing="0">
                  <tr><th>Scope</th><th>Access</th></tr>
${BbCloudRequiredApiTokenScopes.htmlTableRows()}
                </table>
              </li>
            </ol>
            """.trimIndent()
    }
}