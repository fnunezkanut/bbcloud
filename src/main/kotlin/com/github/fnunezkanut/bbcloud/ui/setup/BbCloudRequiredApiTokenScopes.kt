package com.github.fnunezkanut.bbcloud.ui.setup

/**
 * Bitbucket Cloud API token scopes documented on the Settings page and in setup instructions.
 *
 * See [Atlassian scope documentation](https://developer.atlassian.com/cloud/bitbucket/rest/intro/).
 */
object BbCloudRequiredApiTokenScopes {
    data class Entry(val scope: String, val access: String)

    val entries: List<Entry> = listOf(
        Entry(scope = "read:workspace:bitbucket", access = "Read"),
        Entry(scope = "read:user:bitbucket", access = "Read"),
        Entry(scope = "read:repository:bitbucket", access = "Read"),
        Entry(scope = "read:pullrequest:bitbucket", access = "Read"),
        Entry(scope = "write:pullrequest:bitbucket", access = "Write")
    )

    fun htmlTableRows(): String {
        return entries.joinToString(separator = "\n") { entry ->
            "                  <tr><td>${entry.scope}</td><td>${entry.access}</td></tr>"
        }
    }
}