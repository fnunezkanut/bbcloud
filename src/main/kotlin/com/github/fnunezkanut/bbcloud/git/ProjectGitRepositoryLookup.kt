package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Shared lookups of a project's Git repository and remote, used by the git services of this plugin. */
internal object ProjectGitRepositoryLookup {
    private val LOG = logger<ProjectGitRepositoryLookup>()

    // Give Git4Idea time to register a freshly opened work tree's repository before giving up (see awaitRepository).
    private val REPOSITORY_WAIT_TIMEOUT = 5_000.milliseconds
    private val REPOSITORY_WAIT_POLL_INTERVAL = 150.milliseconds

    fun findRepository(project: Project): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val basePath = project.basePath
        val baseDirectory = basePath?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
        val repositoryAtBase = baseDirectory?.let { directory -> repositoryManager.getRepositoryForRootQuick(directory) }
        return repositoryAtBase ?: repositoryManager.repositories.singleOrNull()
    }

    /** The `origin` remote when present, otherwise the first remote. */
    fun selectRemote(repository: GitRepository): GitRemote? {
        val remotes = repository.remotes
        return remotes.firstOrNull { remote -> remote.name == GitRemote.ORIGIN } ?: remotes.firstOrNull()
    }

    /**
     * Resolves the project's Git repository, tolerating the short window after a work tree project is first opened
     * during which [GitRepositoryManager] has not yet registered the repository.
     *
     * When a pull request is opened in a freshly created work tree, the editor binds (and requests line totals) before
     * Git4Idea finishes registering the repository, so [findRepository] returns `null` on the very first attempt. A
     * manual refresh works only because enough time has passed for registration to complete. Polling briefly here gives
     * the repository a chance to register so the indicator renders on initial open too. Callers run this on a
     * background dispatcher, so the short blocking wait never touches the EDT.
     */
    fun awaitRepository(project: Project): GitRepository? {
        val deadline = TimeSource.Monotonic.markNow() + REPOSITORY_WAIT_TIMEOUT
        var attempt = 0
        while (true) {
            val repository = findRepository(project)
            if (repository != null) {
                if (attempt > 0) {
                    LOG.debug("Resolved Git repository for project ${project.name} after $attempt retries")
                }
                return repository
            }
            if (project.isDisposed || deadline.hasPassedNow()) {
                return null
            }
            attempt += 1
            Thread.sleep(REPOSITORY_WAIT_POLL_INTERVAL.inWholeMilliseconds)
        }
    }
}