package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceListResult

/**
 * What the workspace resolve step decided, extracted from the Settings panel so the decision is unit testable without
 * Swing. The panel maps each case to the matching bundle message.
 */
sealed class WorkspaceResolution {
    /** The token reaches exactly one workspace, so there is nothing to confirm and it can be saved as is. */
    data class Resolved(val slug: String) : WorkspaceResolution()

    /** The token can reach [slugs]; the user must confirm one before anything is written. */
    data class NeedsConfirmation(val slugs: List<String>, val preselectedSlug: String) : WorkspaceResolution()

    data object NoneAccessible : WorkspaceResolution()

    data object Unauthorized : WorkspaceResolution()

    data class Failed(val cause: Throwable? = null) : WorkspaceResolution()
}

object WorkspaceResolutionPolicy {
    /**
     * Maps [result] to a [WorkspaceResolution]. A success list holding a single workspace resolves to that
     * workspace without asking, because there is no alternative to choose. A longer list preselects
     * [preferredSlug] when the token can still reach it, and falls back to the first slug otherwise.
     */
    fun resolve(result: WorkspaceListResult, preferredSlug: String?): WorkspaceResolution {
        return when (result) {
            is WorkspaceListResult.Success -> resolveSuccess(result = result, preferredSlug = preferredSlug)
            WorkspaceListResult.Unauthorized -> WorkspaceResolution.Unauthorized
            is WorkspaceListResult.Failed -> WorkspaceResolution.Failed(cause = result.cause)
        }
    }

    private fun resolveSuccess(result: WorkspaceListResult.Success, preferredSlug: String?): WorkspaceResolution {
        val slugs = result.workspaces.map { workspace -> workspace.slug }
        if (slugs.isEmpty()) {
            return WorkspaceResolution.NoneAccessible
        }
        if (slugs.size == 1) {
            // preferredSlug is irrelevant here: the token reaches this workspace and no other.
            return WorkspaceResolution.Resolved(slugs.first())
        }
        return WorkspaceResolution.NeedsConfirmation(
            slugs = slugs,
            preselectedSlug = slugs.firstOrNull { slug -> slug == preferredSlug } ?: slugs.first()
        )
    }
}