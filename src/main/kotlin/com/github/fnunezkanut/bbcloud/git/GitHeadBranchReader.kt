package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** What a git `HEAD` file says about the checked-out revision. */
internal sealed interface HeadFileContent {
    data class Branch(val name: String) : HeadFileContent

    data object Detached : HeadFileContent

    /** A reftable repository, whose `HEAD` file is a stub that cannot be read directly. */
    data object ReftableStub : HeadFileContent

    data object Unknown : HeadFileContent
}

private const val REF_PREFIX = "ref:"
private const val BRANCH_REF_PREFIX = "refs/heads/"
private const val REFTABLE_STUB_BRANCH = ".invalid"
private const val GITDIR_PREFIX = "gitdir:"
private val SHA_LENGTHS = setOf(40, 64)

/** Classifies the text of a git `HEAD` file. */
internal fun parseHeadFileContent(content: String): HeadFileContent {
    val text = content.trim()
    if (text.startsWith(REF_PREFIX)) {
        val ref = text.removePrefix(REF_PREFIX).trim()
        if (!ref.startsWith(BRANCH_REF_PREFIX)) {
            return HeadFileContent.Unknown
        }
        val name = ref.removePrefix(BRANCH_REF_PREFIX)
        return when {
            name.isEmpty() -> HeadFileContent.Unknown
            name == REFTABLE_STUB_BRANCH -> HeadFileContent.ReftableStub
            else -> HeadFileContent.Branch(name = name)
        }
    }
    val isSha = text.length in SHA_LENGTHS && text.all { character -> character.isHexDigit() }
    if (isSha) {
        return HeadFileContent.Detached
    }
    return HeadFileContent.Unknown
}

private fun Char.isHexDigit(): Boolean {
    return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/** Returns the path in a `.git` file of a worktree or submodule (`gitdir: <path>`), or null when there is none. */
internal fun parseGitDirPointer(content: String): String? {
    val text = content.trim()
    if (!text.startsWith(GITDIR_PREFIX)) {
        return null
    }
    return text.removePrefix(GITDIR_PREFIX).trim().ifEmpty { null }
}

/**
 * Reads the current branch of a project folder straight from its git `HEAD` file, so the Recent Projects tab can show
 * the branch of dozens of closed projects without spawning a git process for each.
 *
 * The platform's own provider (`RecentProjectsBranchesProvider`) is `@ApiStatus.Internal` and would be flagged by
 * `verifyPlugin`, so the plugin reads the file itself. Reftable repositories fall back to
 * [WorktreeCurrentBranchReader].
 */
object GitHeadBranchReader {
    private val LOG = logger<GitHeadBranchReader>()

    /**
     * Returns the branch checked out in [projectPath], or null when the folder is not a git repository, `HEAD` is
     * detached or unreadable. Never throws. Callers run it on [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun readBranch(projectPath: Path): String? {
        try {
            val headFile = resolveHeadFile(projectPath = projectPath) ?: return null
            return when (val head = parseHeadFileContent(content = headFile.readText())) {
                is HeadFileContent.Branch -> head.name
                HeadFileContent.ReftableStub -> WorktreeCurrentBranchReader.readCurrentBranch(worktreePath = projectPath)
                HeadFileContent.Detached -> null
                HeadFileContent.Unknown -> null
            }
        } catch (exception: IOException) {
            LOG.debug("Could not read the git HEAD of ${projectPath.fileName}", exception)
            return null
        } catch (exception: SecurityException) {
            LOG.debug("Not allowed to read the git HEAD of ${projectPath.fileName}", exception)
            return null
        }
    }

    private fun resolveHeadFile(projectPath: Path): Path? {
        val dotGit = projectPath.resolve(".git")
        if (dotGit.isDirectory()) {
            return dotGit.resolve("HEAD")
        }
        if (!dotGit.isRegularFile()) {
            return null
        }
        val pointer = parseGitDirPointer(content = dotGit.readText()) ?: return null
        val gitDir = projectPath.resolve(pointer).normalize()
        if (!Files.isDirectory(gitDir)) {
            return null
        }
        return gitDir.resolve("HEAD")
    }
}