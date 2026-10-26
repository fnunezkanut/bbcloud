package com.github.fnunezkanut.bbcloud.ui.main

import org.jetbrains.jewel.ui.icon.IntelliJIconKey

/**
 * The icons of the comment composer toolbar, one key per Markdown style the toolbar offers.
 *
 * The glyphs are vendored expui SVGs from the JetBrains Markdown plugin (Apache 2.0), so the composer toolbar reads the
 * way the platform Markdown editor toolbar reads, without a dependency on the Markdown plugin. The blockquote glyph has
 * no counterpart in the Markdown plugin, so it is a custom expui-matched SVG kept beside the vendored ones.
 *
 * Every key points at the same expui SVG for the old and the new UI; the light and the `_dark` pair follows the IDE
 * theme on its own. The resource is loaded through [iconClass], so it resolves against this plugin's class loader.
 */
internal object BbCloudCommentToolbarIcons {
    private const val BASE_PATH = "icons/expui/commentToolbar"

    val bold: IntelliJIconKey = toolbarIcon(name = "bold")
    val italic: IntelliJIconKey = toolbarIcon(name = "italic")
    val strikethrough: IntelliJIconKey = toolbarIcon(name = "strikeThrough")
    val unorderedList: IntelliJIconKey = toolbarIcon(name = "bulletList")
    val orderedList: IntelliJIconKey = toolbarIcon(name = "numberedList")
    val blockquote: IntelliJIconKey = toolbarIcon(name = "blockquote")
    val inlineCode: IntelliJIconKey = toolbarIcon(name = "codeSpan")
    val link: IntelliJIconKey = toolbarIcon(name = "link")

    private fun toolbarIcon(name: String): IntelliJIconKey {
        val path = "$BASE_PATH/$name.svg"
        return IntelliJIconKey(
            oldUiPath = path,
            newUiPath = path,
            iconClass = BbCloudCommentToolbarIcons::class.java
        )
    }
}