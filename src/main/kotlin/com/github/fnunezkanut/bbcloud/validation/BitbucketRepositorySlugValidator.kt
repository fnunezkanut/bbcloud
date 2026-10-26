package com.github.fnunezkanut.bbcloud.validation

/**
 * Validates that a string is a valid Bitbucket repository slug, using the same character class
 * as [BitbucketWorkspaceValidator]: `[A-Za-z0-9._-]+`. Slugs are matched in full, with no
 * slashes, spaces, or URL fragments allowed, matching what Bitbucket accepts for repository
 * names.
 */
object BitbucketRepositorySlugValidator {
    private val SLUG_PATTERN = Regex("[A-Za-z0-9._-]+")

    fun validate(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(SlugValidationException.Empty)
        }
        if (!SLUG_PATTERN.matches(trimmed)) {
            return Result.failure(SlugValidationException.InvalidFormat)
        }
        return Result.success(trimmed)
    }

    sealed class SlugValidationException : Exception() {
        data object Empty : SlugValidationException()

        data object InvalidFormat : SlugValidationException()
    }
}