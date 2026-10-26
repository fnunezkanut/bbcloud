package com.github.fnunezkanut.bbcloud.bitbucket

import java.net.URI

object BitbucketRepositoryUrls {
    private const val BITBUCKET_HOST = "bitbucket.org"

    fun workspaceFromOrganizationUrl(organizationUrl: String): String? {
        val trimmed = organizationUrl.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "https" || uri.host?.lowercase() != BITBUCKET_HOST) {
            return null
        }
        return uri.path
            .trim('/')
            .split('/')
            .firstOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun repositoryWebUrl(organizationUrl: String, repoSlug: String): String? {
        val workspace = workspaceFromOrganizationUrl(organizationUrl) ?: return null
        // Delegate to the workspace-based overload so the URL shape has a single source of truth.
        return repositoryWebUrlForWorkspace(workspace = workspace, repoSlug = repoSlug)
    }

    /**
     * The repository web URL built from an explicit [workspace] and [repoSlug], `https://bitbucket.org/<workspace>/<slug>`.
     *
     * Unlike [repositoryWebUrl] the workspace is supplied directly rather than derived from the configured organization
     * URL, so the URL follows the repository actually being viewed. The Project tabs use this with the workspace resolved
     * from the checkout's git remote, which can differ from the configured workspace (for example a checkout whose origin
     * remote names a different Bitbucket workspace). Returns null when [workspace] or [repoSlug] is blank.
     */
    fun repositoryWebUrlForWorkspace(workspace: String, repoSlug: String): String? {
        if (workspace.isBlank() || repoSlug.isBlank()) {
            return null
        }
        return "https://$BITBUCKET_HOST/${workspace.trim()}/${repoSlug.trim()}"
    }

    fun pullRequestDiffUrl(organizationUrl: String, repoSlug: String, pullRequestId: Long): String? {
        val repositoryUrl = repositoryWebUrl(organizationUrl = organizationUrl, repoSlug = repoSlug) ?: return null
        return "$repositoryUrl/pull-requests/$pullRequestId/diff"
    }

    /**
     * The pull request web page URL, `https://bitbucket.org/<workspace>/<slug>/pull-requests/<id>`.
     *
     * Unlike [pullRequestDiffUrl] this points at the pull request overview page rather than its diff, which is what the
     * worktree banner's `pr#<id>` link opens. Returns null when the organization URL is not a Bitbucket Cloud
     * organization URL or the repository slug is blank.
     */
    fun pullRequestWebUrl(organizationUrl: String, repoSlug: String, pullRequestId: Long): String? {
        val repositoryUrl = repositoryWebUrl(organizationUrl = organizationUrl, repoSlug = repoSlug) ?: return null
        return "$repositoryUrl/pull-requests/$pullRequestId"
    }

    /**
     * The pull request diff URL anchored at a single file, `…/pull-requests/{id}/diff#chg-{path}`.
     *
     * Bitbucket anchors a file in its diff view with the raw repository path. The path is therefore not URL-encoded.
     *
     * When [fileRelativePath] is null or blank, the plain diff URL is returned.
     */
    fun pullRequestDiffUrl(organizationUrl: String, repoSlug: String, pullRequestId: Long, fileRelativePath: String?): String? {
        val diffUrl = pullRequestDiffUrl(
            organizationUrl = organizationUrl,
            repoSlug = repoSlug,
            pullRequestId = pullRequestId
        ) ?: return null
        val path = fileRelativePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return diffUrl
        return "$diffUrl#chg-$path"
    }

    /**
     * The workspace-wide overview page, `…/<workspace>/workspace/overview`, which is what the Other Projects tab
     * points at in its header. Unlike [repositoryWebUrl] this does not need a repository slug, since every repository
     * of the workspace is a step in that page. The workspace is only used to validate that [organizationUrl] is a
     * Bitbucket Cloud organization URL; the overview path is appended to the URL itself.
     */
    fun workspaceOverviewUrl(organizationUrl: String): String? {
        workspaceFromOrganizationUrl(organizationUrl) ?: return null
        val orgBase = organizationUrl.trim().trimEnd('/')
        return "$orgBase/workspace/overview"
    }

    /**
     * The repository slug a pull request link points at, or null when [href] is not one. Both link shapes
     * Bitbucket hands out are recognised: the API one, `/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}`,
     * and the browser one, `https://bitbucket.org/{workspace}/{repo_slug}/pull-requests/{id}`.
     *
     * The workspace-wide pull request listing puts no repository on the pull request itself, so this is how the
     * repository behind a row is recovered. Each shape has to carry both of its markers before the segment in
     * between is read as a slug, so an unrelated link that happens to mention a repository is not mistaken for one.
     */
    fun repoSlugFromPullRequestLink(href: String): String? {
        val segments = pathSegments(href)
        val repositoriesIndex = segments.indexOf(REPOSITORIES_SEGMENT)
        if (repositoriesIndex >= 0 && segments.contains(API_PULL_REQUESTS_SEGMENT)) {
            // The workspace sits between `repositories` and the slug, so the slug is two segments further along.
            return segments.getOrNull(repositoriesIndex + WORKSPACE_THEN_SLUG_OFFSET)?.takeIf { it.isNotBlank() }
        }
        val webPullRequestsIndex = segments.indexOf(WEB_PULL_REQUESTS_SEGMENT)
        if (webPullRequestsIndex < 1) {
            return null
        }
        return segments[webPullRequestsIndex - 1].takeIf { it.isNotBlank() }
    }

    private fun pathSegments(href: String): List<String> {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val path = runCatching { URI(trimmed).path }.getOrNull() ?: return emptyList()
        return path.trim('/').split('/').filter { segment -> segment.isNotEmpty() }
    }

    private const val REPOSITORIES_SEGMENT = "repositories"

    private const val API_PULL_REQUESTS_SEGMENT = "pullrequests"

    private const val WEB_PULL_REQUESTS_SEGMENT = "pull-requests"

    /** How many segments separate `repositories` from the slug in the API link: the workspace sits in between. */
    private const val WORKSPACE_THEN_SLUG_OFFSET = 2
}