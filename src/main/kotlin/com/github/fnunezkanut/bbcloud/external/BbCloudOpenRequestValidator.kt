package com.github.fnunezkanut.bbcloud.external

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** The three values an open request carries, after validation. */
data class BbCloudOpenRequest(val workspace: String, val repoSlug: String, val prNumber: Long)

/** The result of validating an open request body. */
sealed interface BbCloudOpenRequestValidation {
    data class Valid(val request: BbCloudOpenRequest) : BbCloudOpenRequestValidation

    /** [message] is a short English reason that the extension shows to the user. */
    data class Rejected(val message: String) : BbCloudOpenRequestValidation
}

/**
 * Validates the JSON body of a `POST /api/bbcloud/open` request. The check is pure: it reads no settings and does no I/O,
 * so the caller supplies the configured workspace slug. Only `workspace`, `repo` and `pr` are read, and any other field
 * is ignored.
 */
object BbCloudOpenRequestValidator {
    private val REPO_SLUG_PATTERN = Regex("^[A-Za-z0-9._-]+$")

    const val INVALID_REQUEST = "The request body is not a valid JSON object."
    const val WRONG_WORKSPACE = "The workspace does not match the one configured in bbcloud."
    const val INVALID_REPO = "The repository slug is not valid."
    const val INVALID_PR = "The pull request number is not valid."

    fun validate(body: String, configuredWorkspace: String?): BbCloudOpenRequestValidation {
        val root = parseObject(body = body) ?: return BbCloudOpenRequestValidation.Rejected(message = INVALID_REQUEST)

        val expectedWorkspace = configuredWorkspace?.takeIf { it.isNotBlank() }
        val workspace = stringField(root = root, name = "workspace")
        if (expectedWorkspace == null || workspace != expectedWorkspace) {
            return BbCloudOpenRequestValidation.Rejected(message = WRONG_WORKSPACE)
        }

        val repoSlug = stringField(root = root, name = "repo")
        if (repoSlug == null || !isValidRepoSlug(repoSlug = repoSlug)) {
            return BbCloudOpenRequestValidation.Rejected(message = INVALID_REPO)
        }

        val prNumber = positiveIntegerField(root = root, name = "pr")
            ?: return BbCloudOpenRequestValidation.Rejected(message = INVALID_PR)

        return BbCloudOpenRequestValidation.Valid(
            request = BbCloudOpenRequest(workspace = workspace, repoSlug = repoSlug, prNumber = prNumber)
        )
    }

    /** A repository slug is a single path segment of letters, digits, dots, underscores and hyphens, and not `.` or `..`. */
    private fun isValidRepoSlug(repoSlug: String): Boolean {
        return REPO_SLUG_PATTERN.matches(repoSlug) && repoSlug != "." && repoSlug != ".."
    }

    private fun parseObject(body: String): JsonObject? {
        return try {
            JsonParser.parseString(body) as? JsonObject
        } catch (exception: JsonParseException) {
            null
        }
    }

    private fun stringField(root: JsonObject, name: String): String? {
        val element = root.get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString
    }

    /** A positive whole number. Strings, fractions, zero, negatives and values outside the Long range are rejected. */
    private fun positiveIntegerField(root: JsonObject, name: String): Long? {
        val element = root.get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return try {
            element.asBigDecimal.longValueExact().takeIf { value -> value > 0 }
        } catch (exception: ArithmeticException) {
            null
        }
    }
}