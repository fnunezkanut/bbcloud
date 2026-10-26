package com.github.fnunezkanut.bbcloud.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Opens or focuses a local repository checkout in the IDE. */
object LocalRepositoryProjectOpener {
    data class OpenedProject(val project: Project, val wasAlreadyOpen: Boolean)

    private val LOG = logger<LocalRepositoryProjectOpener>()

    /**
     * Focuses the IDE window of the project already open at [checkoutPath], or opens [checkoutPath] in a new window when
     * no such project is open. The caller must have verified that [checkoutPath] is a directory.
     *
     * Returns the focused or opened project result, or null when the project could not be opened. The method is suspend
     * so it must be called from a coroutine; [ProjectUtil.openOrImportAsync] runs the open off the EDT.
     *
     * The options use `forceOpenInNewFrame = true`, which always opens a new IDE frame rather than asking the platform
     * to attach to an already-open window. The `createOptionsToOpenDotIdeaOrCreateNewIfNotExists` helper from the
     * platform is `@Internal` and suspend, so it is not usable from plugin code; the non-deprecated
     * `OpenProjectTask.build()` factory is used instead.
     */
    suspend fun openOrFocus(repoSlug: String, checkoutPath: Path): OpenedProject? {
        LOG.info("Opening local repository $repoSlug at $checkoutPath")
        val existingProject = ProjectUtil.findAndFocusExistingProjectForPath(checkoutPath)
        if (existingProject != null) {
            LOG.info("Focused the open project for local repository $repoSlug")
            return OpenedProject(
                project = existingProject,
                wasAlreadyOpen = true
            )
        }
        val options = OpenProjectTask.build().withForceOpenInNewFrame(true)
        val openedProject = ProjectUtil.openOrImportAsync(
            file = checkoutPath,
            options = options
        ) ?: run {
            LOG.warn("Could not open local repository $repoSlug at $checkoutPath")
            return null
        }
        return OpenedProject(
            project = openedProject,
            wasAlreadyOpen = false
        )
    }
}