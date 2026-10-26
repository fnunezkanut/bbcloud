package com.github.fnunezkanut.bbcloud.git

/** Builds and parses the line-oriented output of git cat-file --batch-check. */
object PullRequestFileRevisionParser {
    /** Builds one cat-file request for each repository-relative path. */
    fun requestLines(sourceRef: String, repositoryRelativePaths: Collection<String>): List<String> {
        return repositoryRelativePaths.map { path -> "$sourceRef:$path" }
    }

    /** Returns blob ids keyed by repository-relative path for existing blob objects. */
    fun parse(requestLines: List<String>, output: List<String>): Map<String, String> {
        val revisions = mutableMapOf<String, String>()
        requestLines.zip(output).forEach { (request, result) ->
            val fields = result.split(' ', limit = 3)
            if (fields.size < 3 || fields[1] != "blob") {
                return@forEach
            }
            val pathSeparator = request.indexOf(':')
            if (pathSeparator < 0 || fields[0].isEmpty()) {
                return@forEach
            }
            revisions[request.substring(pathSeparator + 1)] = fields[0]
        }
        return revisions
    }
}