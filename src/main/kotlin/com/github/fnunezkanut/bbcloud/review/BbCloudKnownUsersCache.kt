package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.atomic.AtomicLong

/**
 * A light application service that remembers the people seen in the pull request tables so @mention suggestions are
 * instant. It keeps an in-memory index for reads and writes through to the persisted [BbCloudKnownUsersState] on
 * every [record], so the remembered users survive **IDE restarts** (decision 5b).
 *
 * It stores nothing more sensitive than what the pull request tables already show: display names, nicknames, and
 * avatar URLs. The store is capped at [MAX_ENTRIES], evicting the least recently seen first, and [clear] erases it.
 */
@Service(Service.Level.APP)
class BbCloudKnownUsersCache(
    private val store: () -> BbCloudKnownUsersState.State = { BbCloudKnownUsersState.getInstance().state },
    private val maxEntries: Int = MAX_ENTRIES
) {
    private data class KnownUser(val candidate: MentionCandidate, val workspaces: Set<String>, val lastSeen: Long)

    private val lock = Any()

    /** Keyed by [MentionCandidate.identityKey]; insertion order is not relied upon, reads sort by recency. */
    private val index = LinkedHashMap<String, KnownUser>()

    private val sequence = AtomicLong(0)

    private var loaded = false

    /**
     * Records every candidate as recently seen in [workspace], merging with any existing entry: a non-null account id,
     * avatar, or nickname is kept when the new value is null, the newer display name wins, the workspace is added, and
     * the last-seen stamp is bumped. Evicts the least recently seen entries past the cap, then writes through.
     */
    fun record(workspace: String, candidates: Collection<MentionCandidate>) {
        if (candidates.isEmpty()) {
            return
        }
        val normalizedWorkspace = workspace.trim().lowercase().takeIf { it.isNotEmpty() }
        synchronized(lock) {
            ensureLoaded()
            for (candidate in candidates) {
                val key = candidate.identityKey() ?: continue
                val existing = index[key]
                val workspaces = buildSet {
                    existing?.workspaces?.let { addAll(it) }
                    normalizedWorkspace?.let { add(it) }
                }
                val merged = MentionCandidate(
                    uuid = candidate.uuid ?: existing?.candidate?.uuid,
                    accountId = candidate.accountId ?: existing?.candidate?.accountId,
                    displayName = candidate.displayName.takeIf { it.isNotBlank() } ?: existing?.candidate?.displayName ?: candidate.displayName,
                    nickname = candidate.nickname ?: existing?.candidate?.nickname,
                    avatarUrl = candidate.avatarUrl ?: existing?.candidate?.avatarUrl,
                    tier = MentionCandidateTier.RecentlySeen
                )
                index[key] = KnownUser(candidate = merged, workspaces = workspaces, lastSeen = sequence.incrementAndGet())
            }
            evictPastCap()
            writeThrough()
        }
    }

    /**
     * The remembered users seen in [workspace], or all of them when [workspace] is null, sorted most recently seen
     * first. The workspace match is case-insensitive.
     */
    fun candidates(workspace: String?): List<MentionCandidate> {
        val normalizedWorkspace = workspace?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        synchronized(lock) {
            ensureLoaded()
            return index.values
                .filter { user -> normalizedWorkspace == null || normalizedWorkspace in user.workspaces }
                .sortedByDescending { user -> user.lastSeen }
                .map { user -> user.candidate }
        }
    }

    /** Empties the in-memory index and the persisted rows. Backs a future "clear remembered users" action. */
    fun clear() {
        synchronized(lock) {
            index.clear()
            loaded = true
            store().rows.clear()
        }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        var maxSeen = 0L
        for (row in store().rows) {
            val decoded = BbCloudKnownUserRow.decode(row = row) ?: continue
            val candidate = decoded.toCandidate()
            val key = candidate.identityKey() ?: continue
            index[key] = KnownUser(candidate = candidate, workspaces = decoded.workspaces, lastSeen = decoded.lastSeen)
            if (decoded.lastSeen > maxSeen) {
                maxSeen = decoded.lastSeen
            }
        }
        sequence.set(maxSeen)
    }

    private fun evictPastCap() {
        if (index.size <= maxEntries) {
            return
        }
        val keysOldestFirst = index.entries.sortedBy { entry -> entry.value.lastSeen }.map { entry -> entry.key }
        val toRemove = index.size - maxEntries
        for (key in keysOldestFirst.take(toRemove)) {
            index.remove(key)
        }
    }

    private fun writeThrough() {
        val encoded = index.values
            .sortedBy { user -> user.lastSeen }
            .map { user ->
                BbCloudKnownUserRow.fromCandidate(candidate = user.candidate, workspaces = user.workspaces, lastSeen = user.lastSeen).encode()
            }
        val rows = store().rows
        rows.clear()
        rows.addAll(encoded)
    }

    companion object {
        const val MAX_ENTRIES = 2000

        fun getInstance(): BbCloudKnownUsersCache {
            return ApplicationManager.getApplication().service()
        }
    }
}