package com.github.fnunezkanut.bbcloud.ui.components

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestFileType
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
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

    private fun svgLogicalSize(resourcePath: String): Int {
        val markup = checkNotNull(BbCloudPluginIcons::class.java.getResourceAsStream(resourcePath)) {
            "Missing resource $resourcePath"
        }.reader(Charsets.UTF_8).readText()
        val match = WIDTH_ATTRIBUTE.find(markup) ?: error("No width attribute in $resourcePath")
        return match.groupValues[1].toInt()
    }

    companion object {
        private val WIDTH_ATTRIBUTE = Regex("""width="(\d+)"""")
    }
}