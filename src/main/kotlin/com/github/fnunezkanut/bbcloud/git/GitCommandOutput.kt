package com.github.fnunezkanut.bbcloud.git

object GitCommandOutput {
    fun format(commandLine: String, output: String, errorOutput: String): String {
        return listOf("$ $commandLine", output, errorOutput)
            .map { part -> part.trimEnd() }
            .filter { part -> part.isNotEmpty() }
            .joinToString(separator = "\n")
    }

    /** Drops the leading `$ <command line>` echo that [format] adds, leaving only what git printed. */
    fun withoutCommandLine(combinedOutput: String): String {
        return combinedOutput.lineSequence()
            .filter { line -> !line.startsWith("$ ") }
            .joinToString(separator = "\n")
    }

    /** The last non-blank line git printed, ignoring the `$ <command line>` echo, or null when there is none. */
    fun lastOutputLine(combinedOutput: String): String? {
        return withoutCommandLine(combinedOutput)
            .lineSequence()
            .map { line -> line.trim() }
            .lastOrNull { line -> line.isNotEmpty() }
    }
}