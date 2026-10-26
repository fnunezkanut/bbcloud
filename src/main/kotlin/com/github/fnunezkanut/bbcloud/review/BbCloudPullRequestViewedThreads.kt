package com.github.fnunezkanut.bbcloud.review

import androidx.compose.runtime.mutableStateMapOf
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger

/** Persists viewed state for comment threads across projects for the current IDE user. */
@Service(Service.Level.APP)
@State(
    name = "BbCloudViewedThreads",
    storages = [Storage(value = "bbcloud-viewed-threads.xml", roamingType = RoamingType.DISABLED)]
)
class BbCloudPullRequestViewedThreads : SimplePersistentStateComponent<BbCloudPullRequestViewedThreads.State>(State()) {
    /** The persisted state of viewed thread keys and their current content fingerprints. */
    class State : BaseState() {
        var viewedThreadEntries by stringSet()
    }

    private val viewedFingerprints = mutableStateMapOf<String, String>()
    private var mirroredState: State? = null

    init {
        synchronizeStateMirror()
    }

    /** Returns whether the thread was viewed at the supplied content fingerprint. */
    @Synchronized
    fun isViewed(workspaceSlug: String, pullRequestId: Long, relativePath: String, rootCommentId: Long, fingerprint: String): Boolean {
        synchronizeStateMirror()
        val key = PullRequestViewedThreadKey.of(
            workspaceSlug = workspaceSlug,
            pullRequestId = pullRequestId,
            relativePath = relativePath,
            rootCommentId = rootCommentId
        )
        return viewedFingerprints[key] == fingerprint
    }

    /** Persists or removes viewed state for a thread and schedules application settings to be saved. */
    @Synchronized
    fun setViewed(workspaceSlug: String, pullRequestId: Long, relativePath: String, rootCommentId: Long, fingerprint: String, viewed: Boolean) {
        synchronizeStateMirror()
        val key = PullRequestViewedThreadKey.of(
            workspaceSlug = workspaceSlug,
            pullRequestId = pullRequestId,
            relativePath = relativePath,
            rootCommentId = rootCommentId
        )
        val entries = state.viewedThreadEntries.toMutableSet()
        entries.removeAll { entry -> PullRequestViewedThreadKey.decodeEntry(entry)?.key == key }
        viewedFingerprints.remove(key)
        if (viewed) {
            entries.add(PullRequestViewedThreadKey.entryOf(key = key, fingerprint = fingerprint))
            viewedFingerprints[key] = fingerprint
        }
        state.viewedThreadEntries = entries
        LOG.debug("Updated viewed-thread state: ${entries.size} persisted entr${if (entries.size == 1) "y" else "ies"}")
        SaveAndSyncHandler.getInstance().scheduleSave(task = SaveAndSyncHandler.SaveTask())
    }

    /** Removes stale thread entries for one workspace and pull request after a successful comment reload. */
    @Synchronized
    fun prune(workspaceSlug: String, pullRequestId: Long, keptRootCommentIds: Set<Long>) {
        synchronizeStateMirror()
        val staleKeys = PullRequestViewedThreadKey.keysToPrune(
            keys = viewedFingerprints.keys,
            workspaceSlug = workspaceSlug,
            pullRequestId = pullRequestId,
            keptRootCommentIds = keptRootCommentIds
        )
        if (staleKeys.isEmpty()) {
            return
        }
        staleKeys.forEach { key -> viewedFingerprints.remove(key) }
        val entries = state.viewedThreadEntries.toMutableSet()
        entries.removeAll { entry -> PullRequestViewedThreadKey.decodeEntry(entry)?.key in staleKeys }
        state.viewedThreadEntries = entries
        LOG.debug("Pruned ${staleKeys.size} viewed-thread entr${if (staleKeys.size == 1) "y" else "ies"}")
        SaveAndSyncHandler.getInstance().scheduleSave(task = SaveAndSyncHandler.SaveTask())
    }

    private fun synchronizeStateMirror() {
        val currentState = state
        if (mirroredState === currentState) {
            return
        }
        viewedFingerprints.clear()
        var undecodableCount = 0
        currentState.viewedThreadEntries.forEach { entry ->
            val decoded = PullRequestViewedThreadKey.decodeEntry(entry)
            if (decoded == null) {
                undecodableCount++
            } else {
                viewedFingerprints[decoded.key] = decoded.fingerprint
            }
        }
        mirroredState = currentState
        LOG.debug("Loaded ${currentState.viewedThreadEntries.size} viewed-thread entr${if (currentState.viewedThreadEntries.size == 1) "y" else "ies"}")
        if (undecodableCount > 0) {
            LOG.warn("Found $undecodableCount undecodable viewed-thread entr${if (undecodableCount == 1) "y" else "ies"}")
        }
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestViewedThreads>()

        /** Returns the application-scoped viewed-thread store. */
        fun getInstance(): BbCloudPullRequestViewedThreads {
            return ApplicationManager.getApplication().service()
        }
    }
}