package com.github.fnunezkanut.bbcloud.git

object GitCommandOutput {
    fun format(commandLine: String, output: String, errorOutput: String): String {
        return listOf("$ $commandLine", output, errorOutput)
            .map { part -> part.trimEnd() }
            .filter { part -> part.isNotEmpty() }
            .joinToString(separator = "\n")
    }
}