package com.github.fnunezkanut.bbcloud.bitbucket

/** Compares Bitbucket account uuids case-insensitively and with or without surrounding braces. */
object BitbucketAccountUuid {
    /** Trims, strips one leading `{` and trailing `}`, and lowercases, so two forms of the same account compare equal. */
    fun normalize(uuid: String): String {
        val trimmed = uuid.trim()
        val withoutBraces = trimmed.removeSurrounding(prefix = "{", suffix = "}")
        return withoutBraces.lowercase()
    }

    /** Returns false when either side is null or blank; otherwise compares the two uuids after [normalize]. */
    fun matches(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) {
            return false
        }
        return normalize(uuid = first) == normalize(uuid = second)
    }
}