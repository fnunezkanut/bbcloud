package com.github.fnunezkanut.bbcloud.project

/**
 * A verified mapping between a local checkout folder and its Bitbucket Cloud repository.
 *
 * @property localFolderPath Absolute normalized path to the checkout root on disk.
 * @property remoteRepoSlug The Bitbucket repository slug parsed from the origin remote URL.
 * @property workspace The Bitbucket workspace slug the repository belongs to. This is an in-memory value produced by
 *   discovery (parsed from the origin remote) and used by the repository-existence check; it is no longer part of
 *   persistence. The watched map is persisted by repository slug alone (see [com.github.fnunezkanut.bbcloud.settings.BbCloudSettings.State.watchedRepos]).
 * @property watched True when the project is watched, so its pull requests appear on the Workspace - Assigned PRs
 *   tab. New scanned or verified mappings are unchecked by default; the user ticks a project on the Workspace -
 *   Watched Projects tab to watch it.
 */
data class WatchedProjectMapping(val localFolderPath: String, val remoteRepoSlug: String, val workspace: String, val watched: Boolean = false)