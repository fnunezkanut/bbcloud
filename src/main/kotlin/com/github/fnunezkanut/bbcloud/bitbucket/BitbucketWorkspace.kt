package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * A Bitbucket workspace as returned by `GET /2.0/user/workspaces`. The endpoint exposes the workspace slug and uuid
 * inside each workspace access entry, so [slug] is what the settings chooser shows.
 */
data class BitbucketWorkspace(val slug: String, val uuid: String?)