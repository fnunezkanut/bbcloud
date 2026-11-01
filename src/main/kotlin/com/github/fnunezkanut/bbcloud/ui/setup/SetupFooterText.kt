package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.BbCloudBundle

/**
 * Builds the single-line footer under the Compose Settings form. It lives outside the screen so it can be unit tested
 * without touching Compose or the hosting tool window.
 *
 * The footer is split into a [label] (the plugin name, rendered as a clickable link to the project on GitHub) and a
 * [buildSuffix] remainder (the version and disclaimer). [build] joins the two so the full-line behaviour stays
 * verifiable in one place, while the screen renders the two parts separately to make only the label a link.
 */
object SetupFooterText {
    /** The GitHub project the footer label links to. */
    const val PROJECT_URL = "https://github.com/fnunezkanut/bbcloud"

    /** The plugin-name label; the screen renders this as the clickable link. */
    fun label(): String {
        return BbCloudBundle.message("about.footer.label")
    }

    /**
     * The footer remainder shown after [label]: ": <version> \u2022 <disclaimer>" for a resolved [version], or
     * " \u2022 <disclaimer>" when it is null or blank, so the unresolved case never prints a placeholder word such as
     * "Unknown".
     */
    fun buildSuffix(version: String?): String {
        val disclaimer = BbCloudBundle.message("setup.disclaimer")
        val normalizedVersion = version?.trim().orEmpty()
        return if (normalizedVersion.isEmpty()) {
            BbCloudBundle.message("about.footer.suffixNoVersion", disclaimer)
        } else {
            BbCloudBundle.message("about.footer.suffix", normalizedVersion, disclaimer)
        }
    }

    /**
     * The full footer line for the resolved plugin [version], or without the version suffix when it is null or blank.
     * Kept as a single-string builder so the composed label-plus-suffix output stays verifiable.
     */
    fun build(version: String?): String {
        return label() + buildSuffix(version)
    }
}