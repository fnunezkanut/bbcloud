package com.github.fnunezkanut.bbcloud.git

object GitStatusOutput {
    fun hasUncommittedChanges(porcelainOutput: String): Boolean {
        val lines = porcelainOutput.lines()
        for (line in lines) {
            if (line.trim().isNotEmpty()) {
                return true
            }
        }
        return false
    }
}