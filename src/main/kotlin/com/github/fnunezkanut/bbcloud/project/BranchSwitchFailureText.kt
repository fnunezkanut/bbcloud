package com.github.fnunezkanut.bbcloud.project

/**
 * Trims and length-limits Git command output so it fits in a balloon notification.
 *
 * The full Git output is never useful in a balloon and can contain file system paths with user information,
 * so it is shortened to at most [trimmedOutput] characters followed by an ellipsis when it exceeds the limit.
 */
object BranchSwitchFailureText {
    /** The maximum length of the trimmed output before the truncation ellipsis is appended. */
    const val DEFAULT_MAX_LENGTH = 500

    /**
     * Trims surrounding whitespace from [output], returning an empty string when the result is blank.
     * When the trimmed text is longer than [maxLength], returns its first [maxLength] characters followed by `...`;
     * otherwise returns it as is.
     */
    fun trimmedOutput(output: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.length <= maxLength) {
            return trimmed
        }
        return trimmed.substring(startIndex = 0, endIndex = maxLength) + "..."
    }
}