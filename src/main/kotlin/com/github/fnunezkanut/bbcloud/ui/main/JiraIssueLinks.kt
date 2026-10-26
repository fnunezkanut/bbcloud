package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.intellij.openapi.util.text.StringUtil

object JiraIssueLinks {
    val ISSUE_KEY_REGEX = Regex("""\b[A-Z][A-Z0-9]+-\d+\b""")

    private val DNS_LABEL_REGEX = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?""")

    fun jiraBaseUrl(organizationUrl: String): String? {
        val workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)?.lowercase() ?: return null
        if (!DNS_LABEL_REGEX.matches(workspace)) {
            return null
        }
        return "https://$workspace.atlassian.net"
    }

    /** Issue keys contain only uppercase letters, digits, and dashes, so matching after escaping never splits an entity. */
    fun formatTitleHtml(title: String, jiraBaseUrl: String?): String {
        val escaped = StringUtil.escapeXmlEntities(title)
        if (jiraBaseUrl == null) {
            return escaped
        }
        return ISSUE_KEY_REGEX.replace(escaped) { match ->
            """<a href="$jiraBaseUrl/browse/${match.value}">${match.value}</a>"""
        }
    }
}