package com.github.fnunezkanut.bbcloud.bitbucket

import java.net.URI

/**
 * Parses a Git remote URL into a Bitbucket Cloud workspace and repository slug.
 * Returns null when the URL does not point at the Bitbucket Cloud domain or is malformed.
 *
 * The parse is structural rather than an allowlist of exact hostnames, so it survives Bitbucket introducing new
 * hostnames. For both URL shapes the structure is fixed:
 * - the host is the authority after the last `@` (SSH) or the authority of the URI (HTTPS), with any `:port` dropped;
 * - the workspace is the first path segment after the host, and the repository slug is the second;
 * - any `.git` suffix on the repository slug is stripped.
 *
 * The only domain assumption left is that the host is the Bitbucket Cloud domain [BITBUCKET_DOMAIN] or one of its
 * subdomains. That accepts every current and future host prefix (`bitbucket.org`, `ssh.bitbucket.org`,
 * `altssh.bitbucket.org`, and any other `*.bitbucket.org` Bitbucket may add) without code changes. If Bitbucket ever
 * moved off the `bitbucket.org` domain entirely, [BITBUCKET_DOMAIN] is the single line to update, and the API base
 * URL would have to change alongside it. The domain check is what keeps non-Bitbucket remotes (GitHub, GitLab) that
 * share the `workspace/repo` shape from being treated as Bitbucket repositories.
 *
 * Supported forms:
 * - SSH: `git@bitbucket.org:workspace/repo.git`
 * - SSH via alternate host: `git@ssh.bitbucket.org:workspace/repo.git`
 * - HTTPS: `https://bitbucket.org/workspace/repo.git`
 * - HTTPS with user: `https://user@bitbucket.org/workspace/repo.git`
 * - SCP-style without `.git`: `git@bitbucket.org:workspace/repo`
 */
object BitbucketRepositoryRemoteUrl {
    /**
     * The Bitbucket Cloud domain. Any host equal to this or ending in `.` + this is treated as Bitbucket, so every
     * host prefix is covered without an allowlist. This is the single source of truth for the domain; change it only
     * if Bitbucket moves off `bitbucket.org` entirely.
     */
    private const val BITBUCKET_DOMAIN = "bitbucket.org"

    data class BitbucketRemoteRepository(val workspace: String, val repoSlug: String)

    /** True when [host] is the Bitbucket Cloud domain itself or one of its subdomains (such as `ssh.bitbucket.org`). */
    private fun isBitbucketHost(host: String): Boolean {
        val normalized = host.lowercase().trim().trimEnd('.')
        return normalized == BITBUCKET_DOMAIN || normalized.endsWith(".$BITBUCKET_DOMAIN")
    }

    fun parse(rawUrl: String): BitbucketRemoteRepository? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return parseSsh(trimmed) ?: parseHttps(trimmed)
    }

    /**
     * Parses an SSH remote in either of its two shapes:
     * - the SCP short form `user@host:workspace/repo[.git]`, where the first `:` separates the host from the path;
     * - the `ssh://user@host[:port]/workspace/repo[.git]` URL form, where a `:` is a port and `/` starts the path.
     *
     * In both, the host is the authority after the last `@`, the workspace is the first path segment, and the
     * repository slug is the second.
     */
    private fun parseSsh(url: String): BitbucketRemoteRepository? {
        val hasSshScheme = url.startsWith("ssh://", ignoreCase = true)
        val atIndex = url.lastIndexOf('@')
        if (atIndex < 0) {
            return null
        }
        val authorityAndPath = url.substring(atIndex + 1)

        // The ssh:// URL form starts its path at the first '/', with any ':' before it being a port. The SCP short
        // form has no scheme and starts its path at the first ':'.
        val separatorIndex = if (hasSshScheme) {
            authorityAndPath.indexOf('/')
        } else {
            authorityAndPath.indexOf(':')
        }
        if (separatorIndex < 0) {
            return null
        }

        // Strip any ':port' from the authority so only the host remains; the SCP form never has a port here.
        val host = authorityAndPath.substring(0, separatorIndex).substringBefore(':')
        if (!isBitbucketHost(host)) {
            return null
        }
        val path = authorityAndPath.substring(separatorIndex + 1)
        return parsePathSegments(path)
    }

    private fun parseHttps(url: String): BitbucketRemoteRepository? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "https") {
            return null
        }
        val host = uri.host ?: return null
        if (!isBitbucketHost(host)) {
            return null
        }
        return parsePathSegments(uri.path)
    }

    private fun parsePathSegments(path: String): BitbucketRemoteRepository? {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size != 2) {
            return null
        }
        val workspace = segments[0]
        val repoSlug = segments[1].removeSuffix(".git")
        if (workspace.isEmpty() || repoSlug.isEmpty()) {
            return null
        }
        return BitbucketRemoteRepository(workspace = workspace, repoSlug = repoSlug)
    }
}