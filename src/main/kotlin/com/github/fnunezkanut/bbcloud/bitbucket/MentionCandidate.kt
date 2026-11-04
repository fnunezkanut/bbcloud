package com.github.fnunezkanut.bbcloud.bitbucket

/** Which lookup tier produced a mention candidate; tiers are listed in display priority order. */
enum class MentionCandidateTier { PullRequest, RecentlySeen, Workspace }

/** A person who can be @mentioned in a comment. [uuid] keeps Bitbucket's braces; [accountId] is the Atlassian account id. */
data class MentionCandidate(
    val uuid: String?,
    val accountId: String?,
    val displayName: String,
    val nickname: String?,
    val avatarUrl: String?,
    val tier: MentionCandidateTier
) {
    /**
     * The account key used in the inserted `@{...}` token: the Atlassian account id when present, otherwise the uuid
     * with its braces removed. Null when neither is known.
     */
    fun mentionKey(): String? {
        accountId?.takeIf { it.isNotBlank() }?.let { return it }
        val rawUuid = uuid?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return rawUuid.removePrefix("{").removeSuffix("}")
    }

    /** A stable key used to de-duplicate candidates; derived from the uuid when known, otherwise the account id. */
    fun identityKey(): String? {
        uuid?.let { value -> return CommentMentionResolver.normalizeKey(rawKey = value) }
        val account = accountId?.takeIf { it.isNotBlank() } ?: return null
        return "account:" + account.lowercase()
    }
}

/** Builds a mention candidate from an activity user; null when it carries no display name or no usable key. */
fun ActivityUser.toMentionCandidate(tier: MentionCandidateTier): MentionCandidate? {
    val candidate = MentionCandidate(
        uuid = uuid,
        accountId = accountId,
        displayName = displayName,
        nickname = null,
        avatarUrl = avatarUrl,
        tier = tier
    )
    return candidate.takeIf { it.displayName.isNotBlank() && it.mentionKey() != null }
}

/** Builds a mention candidate from a pull request reviewer; null when it carries no display name or no usable key. */
fun PullRequestReviewer.toMentionCandidate(tier: MentionCandidateTier): MentionCandidate? {
    val candidate = MentionCandidate(
        uuid = uuid,
        accountId = accountId,
        displayName = displayName,
        nickname = null,
        avatarUrl = avatarUrl,
        tier = tier
    )
    return candidate.takeIf { it.displayName.isNotBlank() && it.mentionKey() != null }
}

/** Builds a mention candidate from a reviewer candidate; null when it carries no display name or no usable key. */
fun ReviewerCandidate.toMentionCandidate(tier: MentionCandidateTier): MentionCandidate? {
    val candidate = MentionCandidate(
        uuid = uuid,
        accountId = accountId,
        displayName = displayName,
        nickname = nickname,
        avatarUrl = avatarUrl,
        tier = tier
    )
    return candidate.takeIf { it.displayName.isNotBlank() && it.mentionKey() != null }
}

/**
 * Builds a mention candidate for the author of a pull request summary; null when the author has no name, no usable
 * key, or only the `Unknown` placeholder display name.
 */
fun PullRequestSummary.authorMentionCandidate(tier: MentionCandidateTier): MentionCandidate? {
    val name = authorDisplayName.takeIf { it.isNotBlank() && it != "Unknown" } ?: return null
    val candidate = MentionCandidate(
        uuid = authorUuid,
        accountId = authorAccountId,
        displayName = name,
        nickname = null,
        avatarUrl = authorAvatarUrl,
        tier = tier
    )
    return candidate.takeIf { it.mentionKey() != null }
}