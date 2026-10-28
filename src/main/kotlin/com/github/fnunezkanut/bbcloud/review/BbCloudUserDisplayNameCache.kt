package com.github.fnunezkanut.bbcloud.review

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.ConcurrentHashMap

/** The stored outcome of a user lookup: [displayName] is null when Bitbucket returned no usable name for the account. */
data class CachedUserName(val displayName: String?)

/**
 * Remembers the display name of each account for the rest of the IDE session, so that a mention is looked up at most
 * once. Negative results are kept as well, so an account that Bitbucket will not reveal is not requested again.
 *
 * The cache is deliberately in memory only. A user who renames themselves keeps the old name until the IDE restarts,
 * which is an acceptable trade-off for a short mention label.
 *
 * Keys are the normalized account keys produced by `CommentMentionResolver.normalizeKey`.
 */
@Service(Service.Level.APP)
class BbCloudUserDisplayNameCache {
    private val entries = ConcurrentHashMap<String, CachedUserName>()

    /** Returns the stored outcome for [uuidKey], or null when the account has never been looked up. */
    fun cachedName(uuidKey: String): CachedUserName? {
        return entries[uuidKey]
    }

    /** Stores a resolved [displayName], or a negative result when it is null or blank. */
    fun put(uuidKey: String, displayName: String?) {
        val name = displayName?.takeIf { candidate -> candidate.isNotBlank() }
        entries[uuidKey] = CachedUserName(displayName = name)
    }

    /** Seeds names that are already known, such as comment authors and workspace members, so that later lookups can skip them. */
    fun putAll(names: Map<String, String>) {
        names.forEach { (uuidKey, displayName) -> put(uuidKey = uuidKey, displayName = displayName) }
    }

    companion object {
        fun getInstance(): BbCloudUserDisplayNameCache {
            return ApplicationManager.getApplication().service()
        }
    }
}