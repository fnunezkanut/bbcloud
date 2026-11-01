package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.auth.BbCloudAuthenticationMethod

/**
 * Builds the Settings authentication status label text. It lives outside the screen so it can be unit tested without
 * touching Compose or the hosting tool window, mirroring [SetupFooterText].
 */
internal object AuthenticationStatusText {
    /**
     * The status line for [method]. When a workspace slug is stored it is appended as "Workspace: <slug>"; a null or
     * blank slug drops that suffix so a partially completed older setup still reads cleanly.
     */
    fun build(method: BbCloudAuthenticationMethod, workspaceSlug: String?): String {
        val methodName = when (method) {
            BbCloudAuthenticationMethod.None -> return BbCloudBundle.message("setup.authentication.status.none")
            BbCloudAuthenticationMethod.ApiToken -> BbCloudBundle.message("setup.authentication.method.apiToken")
            BbCloudAuthenticationMethod.OAuth -> BbCloudBundle.message("setup.authentication.method.oauth")
        }
        return if (workspaceSlug.isNullOrBlank()) {
            BbCloudBundle.message("setup.authentication.status.authenticated", methodName)
        } else {
            BbCloudBundle.message("setup.authentication.status.authenticatedWithWorkspace", methodName, workspaceSlug)
        }
    }
}