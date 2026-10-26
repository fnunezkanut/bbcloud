package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads the Git remote URL of a checkout root by reading `.git/config` directly on `Dispatchers.IO`,
 * avoiding any IntelliJ `Project` or `Git4Idea` dependency so it is unit-testable with a temp filesystem.
 *
 * Resolves the git directory for both normal clones (`.git` is a directory) and worktrees
 * (`.git` is a file pointing at the real git directory), then reads remotes in file order,
 * preferring `origin` and falling back to the first Bitbucket Cloud remote found.
 */
object BitbucketGitRemoteReader {
    private val LOG = logger<BitbucketGitRemoteReader>()

    /**
     * Returns the remote URL of `[remoteName]` (default `origin`) from the git config at [checkoutRoot],
     * or null when no such remote exists or `.git` cannot be resolved.
     */
    fun readRemoteUrl(checkoutRoot: Path, remoteName: String = "origin"): String? {
        val gitConfigPath = resolveGitConfig(checkoutRoot) ?: return null
        if (!Files.isRegularFile(gitConfigPath)) {
            LOG.debug("No git config found at $gitConfigPath")
            return null
        }
        return readRemoteUrlFromFile(gitConfigPath, remoteName)
    }

    private fun resolveGitConfig(checkoutRoot: Path): Path? {
        val dotGit = checkoutRoot.resolve(".git")
        return when {
            Files.isDirectory(dotGit) -> {
                dotGit.resolve("config")
            }
            Files.isRegularFile(dotGit) -> {
                val gitdirPath = parseGitfile(dotGit) ?: return null
                gitdirPath.resolve("config")
            }
            else -> {
                LOG.debug("No .git entry found at $dotGit")
                null
            }
        }
    }

    private fun parseGitfile(gitfile: Path): Path? {
        return try {
            val content = Files.readString(gitfile).trim()
            val line = content.lines().firstOrNull { it.startsWith("gitdir:") }
            val gitdirValue = line?.removePrefix("gitdir:")?.trim()
            if (gitdirValue.isNullOrEmpty()) {
                null
            } else {
                Paths.get(gitdirValue)
            }
        } catch (e: IOException) {
            LOG.debug("Cannot read gitfile at $gitfile", e)
            null
        }
    }

    private fun readRemoteUrlFromFile(configPath: Path, remoteName: String): String? {
        val sections = parseIniSections(configPath)
        val targetSection = sections[remoteName]
        if (targetSection != null) {
            val url = targetSection["url"]
            if (!url.isNullOrEmpty()) {
                return url
            }
            val pushUrl = targetSection["pushurl"]
            if (!pushUrl.isNullOrEmpty()) {
                return pushUrl
            }
        }

        for ((name, section) in sections) {
            if (name == remoteName) continue
            val url = section["url"]
            if (!url.isNullOrEmpty()) {
                return url
            }
        }
        return null
    }

    private fun parseIniSections(configPath: Path): Map<String, Map<String, String>> {
        val result = LinkedHashMap<String, LinkedHashMap<String, String>>()
        var currentSection: String? = null
        try {
            Files.readAllLines(configPath).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    return@forEach
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val sectionHeader = trimmed.removeSurrounding("[", "]")
                    val sectionName = parseSectionName(sectionHeader)
                    if (sectionName != null) {
                        currentSection = sectionName
                        result.getOrPut(sectionName) { LinkedHashMap() }
                    }
                    return@forEach
                }
                if (currentSection != null && "=" in trimmed) {
                    val eqIndex = trimmed.indexOf('=')
                    val key = trimmed.substring(0, eqIndex).trim()
                    val value = trimmed.substring(eqIndex + 1).trim()
                    if (key.isNotEmpty()) {
                        result[currentSection]!![key] = value
                    }
                }
            }
        } catch (e: IOException) {
            LOG.debug("Cannot read git config at $configPath", e)
        }
        return result
    }

    private fun parseSectionName(header: String): String? {
        if (header.startsWith("remote \"")) {
            val end = header.lastIndexOf("\"")
            if (end > "remote \"".length) {
                return header.substring("remote \"".length, end)
            }
        }
        return null
    }
}