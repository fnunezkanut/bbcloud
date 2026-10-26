package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.BbCloudBundle

/**
 * Builds the single-line footer under the Compose Settings form. It lives outside the screen so it can be unit tested
 * without touching Compose or the hosting tool window.
 */
object SetupFooterText {
    /**
     * The footer for the resolved plugin [version], or without the version suffix when it is null or blank, so the
     * unresolved case never prints a placeholder word such as "Unknown".
     */
    fun build(version: String?): String {
        val disclaimer = BbCloudBundle.message("setup.disclaimer")
        val normalizedVersion = version?.trim().orEmpty()
        return if (normalizedVersion.isEmpty()) {
            BbCloudBundle.message("about.footerNoVersion", disclaimer)
        } else {
            BbCloudBundle.message("about.footer", normalizedVersion, disclaimer)
        }
    }
}