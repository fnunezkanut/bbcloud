package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceMembers
import com.github.fnunezkanut.bbcloud.bitbucket.toMentionCandidate
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Supplies @mention suggestions to a comment composer. Implementations must be cheap to call on the EDT except [workspaceCandidates]. */
interface CommentMentionSource {
    /** The uuid of the signed-in user, excluded from suggestions; null when unknown. */
    val currentUserUuid: String?

    /** Tiers 1 and 2: people on the pull request, then recently seen users. No network. */
    fun localCandidates(): List<MentionCandidate>

    /** Tier 3: workspace members; loads once and then answers from memory. Null when they could not be loaded. */
    suspend fun workspaceCandidates(): WorkspaceMentionCandidates?
}

/** Workspace members as candidates; [isTruncated] is true when the member listing stopped at its page limit. */
data class WorkspaceMentionCandidates(val candidates: List<MentionCandidate>, val isTruncated: Boolean)

/**
 * The production [CommentMentionSource]. Its constructor takes only lambdas, so it is testable without the platform.
 * The workspace load is memoized: the first success is cached, concurrent callers share one load, and a null result
 * is not cached so a later `@` retries.
 */
class DefaultCommentMentionSource(
    private val pullRequestPeople: () -> List<MentionCandidate>,
    private val recentlySeen: () -> List<MentionCandidate>,
    private val loadWorkspaceMembers: suspend () -> WorkspaceMembers?,
    private val currentUser: () -> String?
) : CommentMentionSource {
    override val currentUserUuid: String?
        get() = currentUser()

    private val workspaceMutex = Mutex()

    @Volatile
    private var cachedWorkspace: WorkspaceMentionCandidates? = null

    override fun localCandidates(): List<MentionCandidate> {
        return pullRequestPeople() + recentlySeen()
    }

    override suspend fun workspaceCandidates(): WorkspaceMentionCandidates? {
        cachedWorkspace?.let { cached -> return cached }
        return workspaceMutex.withLock {
            cachedWorkspace?.let { cached -> return cached }
            val members = loadWorkspaceMembers() ?: return null
            val candidates = members.members.mapNotNull { member -> member.toMentionCandidate(tier = MentionCandidateTier.Workspace) }
            LOG.debug("Loaded ${candidates.size} workspace mention candidates")
            val result = WorkspaceMentionCandidates(candidates = candidates, isTruncated = members.isTruncated)
            cachedWorkspace = result
            result
        }
    }

    companion object {
        private val LOG = logger<DefaultCommentMentionSource>()
    }
}