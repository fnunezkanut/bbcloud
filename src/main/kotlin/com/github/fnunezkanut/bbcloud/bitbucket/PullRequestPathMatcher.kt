package com.github.fnunezkanut.bbcloud.bitbucket

/** Normalizes repository-relative paths so Bitbucket inline paths match local [Change] files. */
object PullRequestPathMatcher {
    fun normalize(path: String): String {
        return path.trim().replace('\\', '/').removePrefix("./").trim('/')
    }

    fun relativeProjectPath(projectBasePath: String?, absoluteOrRelativePath: String): String {
        val normalized = normalize(absoluteOrRelativePath)
        val base = projectBasePath?.let { normalize(it) } ?: return normalized
        val prefix = "$base/"
        if (normalized.startsWith(prefix)) {
            return normalized.removePrefix(prefix)
        }
        return normalized
    }

    fun matches(projectBasePath: String?, changeRelativePath: String, bitbucketPath: String): Boolean {
        val changePath = normalize(changeRelativePath)
        val inlinePath = normalize(bitbucketPath)
        if (changePath == inlinePath) {
            return true
        }
        return changePath.endsWith("/$inlinePath") || inlinePath.endsWith("/$changePath")
    }
}