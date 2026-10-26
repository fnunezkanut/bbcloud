package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher

object GitNumstatParser {
    fun sum(outputLines: List<String>): BranchDiffLineTotals {
        var added = 0
        var removed = 0
        var hasBinary = false
        for (rawLine in outputLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                continue
            }
            val columns = line.split('\t')
            if (columns.size < 3) {
                continue
            }
            val addedColumn = columns[0]
            val removedColumn = columns[1]
            if (addedColumn == "-" || removedColumn == "-") {
                hasBinary = true
                continue
            }
            val addedValue = addedColumn.toIntOrNull()
            val removedValue = removedColumn.toIntOrNull()
            if (addedValue == null || removedValue == null) {
                continue
            }
            added += addedValue
            removed += removedValue
        }
        return BranchDiffLineTotals(
            linesAdded = added,
            linesRemoved = removed,
            hasBinaryFiles = hasBinary
        )
    }

    fun perFile(outputLines: List<String>): Map<String, FileLineCount> {
        val countsByPath = mutableMapOf<String, FileLineCount>()
        for (rawLine in outputLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                continue
            }
            val columns = line.split('\t')
            if (columns.size < 3) {
                continue
            }
            val path = PullRequestPathMatcher.normalize(columns.drop(2).joinToString("\t"))
            if (path.isEmpty()) {
                continue
            }
            val addedColumn = columns[0]
            val removedColumn = columns[1]
            if (addedColumn == "-" || removedColumn == "-") {
                countsByPath[path] = FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = true)
                continue
            }
            val addedValue = addedColumn.toIntOrNull()
            val removedValue = removedColumn.toIntOrNull()
            if (addedValue == null || removedValue == null) {
                continue
            }
            countsByPath[path] = FileLineCount(
                linesAdded = addedValue,
                linesRemoved = removedValue,
                isBinary = false
            )
        }
        return countsByPath
    }
}