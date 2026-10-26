package com.github.fnunezkanut.bbcloud.validation

object BitbucketWorkspaceValidator {
    private val SLUG_PATTERN = Regex("[A-Za-z0-9._-]+")

    fun validate(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(ValidationException.Empty)
        }
        if (!SLUG_PATTERN.matches(trimmed)) {
            return Result.failure(ValidationException.InvalidFormat)
        }
        return Result.success(trimmed)
    }

    sealed class ValidationException : Exception() {
        data object Empty : ValidationException()

        data object InvalidFormat : ValidationException()
    }
}