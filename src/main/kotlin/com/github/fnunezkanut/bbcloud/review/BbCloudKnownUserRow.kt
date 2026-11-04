package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier

/**
 * One remembered mention candidate, encoded as a single delimited string so it round-trips through
 * [com.intellij.openapi.components.BaseState], which persists primitive collections but not nested object graphs.
 *
 * Fields are joined with the unit separator `\u001F`; the workspace set is joined with the record separator `\u001E`.
 * Both are control characters that never appear in uuids, account ids, names, URLs, or slugs, so a value can be
 * decoded back exactly even when a display name contains spaces or punctuation.
 */
internal data class BbCloudKnownUserRow(
    val uuid: String?,
    val accountId: String?,
    val displayName: String,
    val nickname: String?,
    val avatarUrl: String?,
    val workspaces: Set<String>,
    val lastSeen: Long
) {
    /** Encodes this row into a single string with a fixed field count. A null field encodes as the empty string. */
    fun encode(): String {
        return listOf(
            uuid.orEmpty(),
            accountId.orEmpty(),
            displayName,
            nickname.orEmpty(),
            avatarUrl.orEmpty(),
            workspaces.joinToString(separator = RECORD_SEPARATOR.toString()),
            lastSeen.toString()
        ).joinToString(separator = UNIT_SEPARATOR.toString())
    }

    /** Builds a recently-seen mention candidate from this row. */
    fun toCandidate(): MentionCandidate {
        return MentionCandidate(
            uuid = uuid,
            accountId = accountId,
            displayName = displayName,
            nickname = nickname,
            avatarUrl = avatarUrl,
            tier = MentionCandidateTier.RecentlySeen
        )
    }

    companion object {
        private const val UNIT_SEPARATOR = '\u001F'
        private const val RECORD_SEPARATOR = '\u001E'
        private const val FIELD_COUNT = 7

        /**
         * Decodes a row, returning null when the shape is wrong (not exactly [FIELD_COUNT] fields), the display name
         * is blank, or the last-seen stamp does not parse. A corrupt row is dropped rather than crashing the load.
         */
        fun decode(row: String): BbCloudKnownUserRow? {
            val fields = row.split(UNIT_SEPARATOR)
            if (fields.size != FIELD_COUNT) {
                return null
            }
            val displayName = fields[2].takeIf { it.isNotBlank() } ?: return null
            val lastSeen = fields[6].toLongOrNull() ?: return null
            val workspaces = fields[5].split(RECORD_SEPARATOR).filter { workspace -> workspace.isNotEmpty() }.toSet()
            return BbCloudKnownUserRow(
                uuid = fields[0].takeIf { it.isNotEmpty() },
                accountId = fields[1].takeIf { it.isNotEmpty() },
                displayName = displayName,
                nickname = fields[3].takeIf { it.isNotEmpty() },
                avatarUrl = fields[4].takeIf { it.isNotEmpty() },
                workspaces = workspaces,
                lastSeen = lastSeen
            )
        }

        /** Builds a row from a mention candidate seen in [workspaces] at logical time [lastSeen]. */
        fun fromCandidate(candidate: MentionCandidate, workspaces: Set<String>, lastSeen: Long): BbCloudKnownUserRow {
            return BbCloudKnownUserRow(
                uuid = candidate.uuid,
                accountId = candidate.accountId,
                displayName = candidate.displayName,
                nickname = candidate.nickname,
                avatarUrl = candidate.avatarUrl,
                workspaces = workspaces,
                lastSeen = lastSeen
            )
        }
    }
}