package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.parseGitDirPointer
import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val LOG = logger<WorktreeSourceCheckoutResolver>()

/**
 * Given the `gitdir` pointer target `<source>/.git/worktrees/<name>`, returns the source checkout root `<source>`.
 * Returns null for anything else (bare repositories, submodules, custom `GIT_DIR`).
 */
fun sourceRootFromGitDirPointer(gitDir: Path): Path? {
    val worktreesFolder = gitDir.parent ?: return null
    val dotGitFolder = worktreesFolder.parent ?: return null
    if (worktreesFolder.fileName?.toString() != "worktrees" || dotGitFolder.fileName?.toString() != ".git") {
        return null
    }
    return dotGitFolder.parent
}

/** Finds the source checkout of a PR work tree, so the work tree can be removed with `git worktree remove` from there. */
object WorktreeSourceCheckoutResolver {
    /**
     * Resolves the source checkout root of [worktreePath]. Tries, in order: the `gitdir` pointer in the work tree's `.git`
     * file, the watched mapping of [repoSlug] ([mappingsBySlug]: slug to local folder), and `$projectsDirectory/$repoSlug`.
     * Returns null when none of them is an existing directory. It never throws.
     */
    fun resolve(
        worktreePath: Path,
        repoSlug: String?,
        mappingsBySlug: Map<String, String>,
        projectsDirectory: String?,
        isDirectory: (Path) -> Boolean = Files::isDirectory
    ): Path? {
        val fromPointer = sourceFromPointer(worktreePath = worktreePath)
        if (fromPointer != null && isDirectory(fromPointer)) {
            return fromPointer
        }
        if (repoSlug == null) {
            return null
        }
        val fromMapping = pathOrNull(value = mappingsBySlug[repoSlug])
        if (fromMapping != null && isDirectory(fromMapping)) {
            return fromMapping
        }
        val fromConvention = pathOrNull(value = projectsDirectory)?.let { directory -> pathOrNull(value = directory.resolve(repoSlug).toString()) }
        if (fromConvention != null && isDirectory(fromConvention)) {
            return fromConvention
        }
        return null
    }

    private fun sourceFromPointer(worktreePath: Path): Path? {
        return try {
            val dotGit = worktreePath.resolve(".git")
            if (!Files.isRegularFile(dotGit)) {
                return null
            }
            val pointer = parseGitDirPointer(content = Files.readString(dotGit)) ?: return null
            sourceRootFromGitDirPointer(gitDir = worktreePath.resolve(pointer).normalize())
        } catch (exception: IOException) {
            LOG.debug("Could not read the git pointer of a work tree", exception)
            null
        } catch (exception: InvalidPathException) {
            LOG.debug("The git pointer of a work tree is not a valid path", exception)
            null
        }
    }

    private fun pathOrNull(value: String?): Path? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            Path.of(value).toAbsolutePath().normalize()
        } catch (exception: InvalidPathException) {
            LOG.debug("Not a valid folder path", exception)
            null
        }
    }
}