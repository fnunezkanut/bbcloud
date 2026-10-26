package com.github.fnunezkanut.bbcloud.bitbucket

enum class MergeCheckStatus {
    Passed,
    Failed,
    Pending,
    Skipped,
    Unknown
}

/**
 * A standard (branch restriction) or custom merge check reported by the mergeability checks endpoint.
 *
 * [kind] is the Bitbucket `check.kind` for standard checks and [CUSTOM_KIND] for custom checks, which carry a [name] instead.
 * [requiredValue] and [observedValue] are the first integer values of Bitbucket's `requirement` and `observed` objects.
 */
data class PullRequestMergeCheck(
    val kind: String,
    val name: String?,
    val status: MergeCheckStatus,
    val isBlocking: Boolean,
    val requiredValue: Int?,
    val observedValue: Int?
) {
    companion object {
        const val CUSTOM_KIND = "custom"
    }
}