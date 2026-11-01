package com.github.fnunezkanut.bbcloud.ui.components

import com.github.fnunezkanut.bbcloud.ui.pullrequest.changes.WorktreePullRequestChangesToolWindowFactory
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestFileType
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.Charsets

@Tag("unit")
class PrToolWindowIconResourcesTest {
    @Test
    fun `pull request file type - should use the shared 16x16 tool window icon`() {
        // WHEN
        val fileTypeIcon = BbCloudPullRequestFileType.getIcon()

        // THEN
        fileTypeIcon shouldBe BbCloudPluginIcons.prIcon
    }

    @Test
    fun `pr tool window svg assets - should declare 16x16 or 20x20 logical sizes only`() {
        // THEN
        assertSoftly {
            svgLogicalSize("/icons/prToolWindow.svg") shouldBe 16
            svgLogicalSize("/icons/expui/toolwindow/prToolWindow.svg") shouldBe 16
            svgLogicalSize("/icons/expui/toolwindow/prToolWindow@20x20.svg") shouldBe 20
            BbCloudPluginIcons::class.java.getResource("/icons/prToolWindow@2x.svg") shouldBe null
        }
    }

    @Test
    fun `plugin xml - should register icon mappings for new UI tool window icons`() {
        // GIVEN
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))

        // WHEN
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream).documentElement
        val extensions = root.getElementsByTagName("extensions").item(0) as Element
        val mappers = (0 until extensions.childNodes.length).mapNotNull { index ->
            extensions.childNodes.item(index).takeIf { node -> node is Element && node.tagName == "iconMapper" } as? Element
        }

        // THEN
        assertSoftly {
            mappers.size shouldBe 1
            mappers.single().getAttribute("mappingFile") shouldBe "BbCloudIconMappings.json"
        }
    }

    @Test
    fun `pr changes tool window svg assets - should declare 13x13 classic and 16x16 or 20x20 logical sizes`() {
        // THEN
        assertSoftly {
            svgLogicalSize("/icons/prChangesToolWindow.svg") shouldBe 13
            svgLogicalSize("/icons/prChangesToolWindow_dark.svg") shouldBe 13
            svgLogicalSize("/icons/expui/toolwindow/prChangesToolWindow.svg") shouldBe 16
            svgLogicalSize("/icons/expui/toolwindow/prChangesToolWindow_dark.svg") shouldBe 16
            svgLogicalSize("/icons/expui/toolwindow/prChangesToolWindow@20x20.svg") shouldBe 20
            svgLogicalSize("/icons/expui/toolwindow/prChangesToolWindow@20x20_dark.svg") shouldBe 20
        }
    }

    @Test
    fun `pr changes tool window svg assets - should keep the blue-bar arrow colors and the 20 grid`() {
        // GIVEN
        val lightMarkups = listOf(
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow.svg"),
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow@20x20.svg")
        )
        val darkMarkups = listOf(
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow_dark.svg"),
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow@20x20_dark.svg")
        )
        val twentyGridMarkups = listOf(
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow@20x20.svg"),
            svgMarkup("/icons/expui/toolwindow/prChangesToolWindow@20x20_dark.svg")
        )

        // THEN
        assertSoftly {
            lightMarkups.forEach { markup ->
                markup shouldContain "#3574F0"
                markup shouldContain "#6C707E"
            }
            darkMarkups.forEach { markup ->
                markup shouldContain "#548AF7"
                markup shouldContain "#CED0D6"
            }
            twentyGridMarkups.forEach { markup ->
                markup shouldContain "viewBox=\"0 0 20 20\""
                markup shouldContain "stroke-width=\"1.5\""
            }
        }
    }

    @Test
    fun `plugin xml - pr changes tool window should use the plugin changes icon`() {
        // GIVEN
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))

        // WHEN
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream).documentElement
        val toolWindows = root.getElementsByTagName("toolWindow")
        val prChangesToolWindows = (0 until toolWindows.length)
            .map { index -> toolWindows.item(index) as Element }
            .filter { element -> element.getAttribute("id") == WorktreePullRequestChangesToolWindowFactory.TOOL_WINDOW_ID }

        // THEN
        assertSoftly {
            prChangesToolWindows.size shouldBe 1
            prChangesToolWindows.single().getAttribute("icon") shouldBe "/icons/prChangesToolWindow.svg"
        }
    }

    @Test
    fun `icon mappings - should map the new UI pr changes icon to the classic icon`() {
        // GIVEN
        val mappings = checkNotNull(BbCloudPluginIcons::class.java.getResourceAsStream("/BbCloudIconMappings.json")) {
            "Missing BbCloudIconMappings.json"
        }.reader(Charsets.UTF_8).readText()

        // THEN
        mappings shouldContain "\"prChangesToolWindow.svg\": \"icons/prChangesToolWindow.svg\""
    }

    private fun svgMarkup(resourcePath: String): String {
        val stream = checkNotNull(BbCloudPluginIcons::class.java.getResourceAsStream(resourcePath)) {
            "Missing resource $resourcePath"
        }
        return stream.reader(Charsets.UTF_8).readText()
    }

    private fun svgLogicalSize(resourcePath: String): Int {
        val match = WIDTH_ATTRIBUTE.find(svgMarkup(resourcePath)) ?: error("No width attribute in $resourcePath")
        return match.groupValues[1].toInt()
    }

    companion object {
        private val WIDTH_ATTRIBUTE = Regex("""width="(\d+)"""")
    }
}