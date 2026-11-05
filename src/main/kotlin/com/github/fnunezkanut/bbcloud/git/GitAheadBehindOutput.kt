package com.github.fnunezkanut.bbcloud.git

/** How many commits a local branch is ahead of and behind its remote counterpart. */
data class AheadBehind(val ahead: Int, val behind: Int)

/**
 * Parses the output of `git rev-list --left-right --count <local>...<remote>`, which is `"<ahead>\t<behind>"`.
 *
 * The runner's combined output can start with the printable command line (`$ git ...`), so those lines are ignored and
 * the last remaining non-blank line is parsed. Kept pure so it is unit-testable.
 */
object GitAheadBehindOutput {
    fun parse(output: String): AheadBehind? {
        val line = output.lineSequence()
            .map { candidate -> candidate.trim() }
            .lastOrNull { candidate -> candidate.isNotBlank() && !candidate.startsWith("$ ") }
            ?: return null
        val parts = line.split(WHITESPACE).filter { part -> part.isNotEmpty() }
        if (parts.size != 2) {
            return null
        }
        val ahead = parts[0].toIntOrNull() ?: return null
        val behind = parts[1].toIntOrNull() ?: return null
        if (ahead < 0 || behind < 0) {
            return null
        }
        return AheadBehind(ahead = ahead, behind = behind)
    }

    private val WHITESPACE = Regex("\\s+")
}