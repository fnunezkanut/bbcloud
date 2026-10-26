package com.github.fnunezkanut.bbcloud.review

import androidx.compose.runtime.mutableStateMapOf
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/** Persists and exposes the viewed state of changed files for one pull-request worktree project. */
@Service(Service.Level.PROJECT)
@State(name = "BbCloudViewedFiles", storages = [Storage("bbcloud-viewed-files.xml")])
class BbCloudPullRequestViewedFiles(private val project: Project) : SimplePersistentStateComponent<BbCloudPullRequestViewedFiles.State>(State()) {
    /** The persisted state of viewed file keys and their source-branch revisions. */
    class State : BaseState() {
        var viewedFileEntries by stringSet()
    }

    private val viewedRevisions = mutableStateMapOf<String, String>()
    private var mirroredState: State? = null

    init {
        synchronizeStateMirror()
    }

    /** Returns whether the file was viewed at the supplied source-branch revision. */
    @Synchronized
    fun isViewed(pullRequestId: Long, relativePath: String, revision: String?): Boolean {
        synchronizeStateMirror()
        val key = PullRequestViewedFileKey.of(pullRequestId = pullRequestId, relativePath = relativePath)
        return viewedRevisions[key] == revision.orEmpty()
    }

    /** Persists or removes the viewed state of a file and schedules the project state for saving. */
    @Synchronized
    fun setViewed(pullRequestId: Long, relativePath: String, revision: String?, viewed: Boolean) {
        synchronizeStateMirror()
        val key = PullRequestViewedFileKey.of(pullRequestId = pullRequestId, relativePath = relativePath)
        val entries = state.viewedFileEntries.toMutableSet()
        entries.removeAll { entry -> PullRequestViewedFileKey.decodeEntry(entry)?.key == key }
        viewedRevisions.remove(key)
        if (viewed) {
            val storedRevision = revision.orEmpty()
            entries.add(PullRequestViewedFileKey.entryOf(key = key, revision = storedRevision))
            viewedRevisions[key] = storedRevision
        }
        state.viewedFileEntries = entries
        LOG.debug("Updated viewed-file state: ${entries.size} persisted entr${if (entries.size == 1) "y" else "ies"}")
        SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    }

    /** Removes stale entries for one pull request after a successful changed-file load. */
    @Synchronized
    fun prune(pullRequestId: Long, keptPaths: Set<String>) {
        synchronizeStateMirror()
        val staleKeys = PullRequestViewedFileKey.keysToPrune(
            keys = viewedRevisions.keys,
            pullRequestId = pullRequestId,
            keptPaths = keptPaths
        )
        if (staleKeys.isEmpty()) {
            return
        }
        staleKeys.forEach { key -> viewedRevisions.remove(key) }
        val entries = state.viewedFileEntries.toMutableSet()
        entries.removeAll { entry -> PullRequestViewedFileKey.decodeEntry(entry)?.key in staleKeys }
        state.viewedFileEntries = entries
        LOG.debug("Pruned ${staleKeys.size} viewed-file entr${if (staleKeys.size == 1) "y" else "ies"}")
        SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    }

    private fun synchronizeStateMirror() {
        val currentState = state
        if (mirroredState === currentState) {
            return
        }
        viewedRevisions.clear()
        var undecodableCount = 0
        currentState.viewedFileEntries.forEach { entry ->
            val decoded = PullRequestViewedFileKey.decodeEntry(entry)
            if (decoded == null) {
                undecodableCount++
            } else {
                viewedRevisions[decoded.key] = decoded.revision
            }
        }
        mirroredState = currentState
        LOG.debug("Loaded ${currentState.viewedFileEntries.size} viewed-file entr${if (currentState.viewedFileEntries.size == 1) "y" else "ies"}")
        if (undecodableCount > 0) {
            LOG.warn("Found $undecodableCount undecodable viewed-file entr${if (undecodableCount == 1) "y" else "ies"}")
        }
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestViewedFiles>()

        /** Returns the viewed-file store for a project. */
        fun getInstance(project: Project): BbCloudPullRequestViewedFiles {
            return project.service()
        }
    }
}