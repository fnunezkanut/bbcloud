package com.github.fnunezkanut.bbcloud.plugin

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards every registered application and project service against the `PluginException: Please, do not use
 * constructor injection` the platform throws when a service constructor takes a parameter it cannot resolve
 * from the container. `BbCloudToolWindowPanel` once declared `BbCloudConfigurationService` and
 * `BbCloudProjectRepositoryLinkService` constructor parameters, neither of which is a registered service, so the
 * tool window failed to build. This test reads the service registrations from `plugin.xml` and fails if any
 * registered service has a constructor parameter outside the set the platform allows.
 *
 * The allowed parameter types are confirmed against the IntelliJ Platform documentation:
 * https://plugins.jetbrains.com/docs/intellij/plugin-services.html
 * The platform allows a `Project` (and `Module`) argument for project/module-level services, and a
 * `CoroutineScope` can be injected for application and project services. Everything else is rejected.
 */
@Tag("unit")
class BbCloudServiceConstructorTest {
    @Test
    fun `plugin xml - every registered service constructor should only take allowed parameter types`() {
        // GIVEN
        val descriptor = pluginDescriptor()
        val implementations = serviceImplementations(root = descriptor)

        // WHEN
        val offenders = implementations.flatMap { implementation -> offendingParameters(implementation = implementation) }

        // THEN
        assertSoftly {
            offenders.shouldBeEmpty()
        }
    }

    /**
     * Loads the implementation class without initializing it and returns a human-readable description for each
     * constructor parameter whose type is not in [ALLOWED_PARAMETER_TYPES].
     */
    private fun offendingParameters(implementation: String): List<String> {
        val type = Class.forName(implementation, false, javaClass.classLoader)
        return type.declaredConstructors.flatMap { constructor ->
            constructor.parameterTypes.filterNot { parameterType ->
                parameterType.name in ALLOWED_PARAMETER_TYPES
            }.map { parameterType ->
                "$implementation has a constructor parameter of type ${parameterType.name}, " +
                    "which the platform cannot resolve for a registered service"
            }
        }
    }

    /**
     * Bundled plugin dependencies ship their own `META-INF/plugin.xml`, and the plugin's own descriptor is
     * reachable both from the resources output directory and from the packaged jar, so every descriptor on the
     * classpath is scanned and the bbcloud one is selected by its `<id>` rather than by resource order. This
     * mirrors `BbCloudPluginDescriptorTest.pluginDescriptor()`.
     */
    private fun pluginDescriptor(): Element {
        val descriptors = javaClass.classLoader.getResources(DESCRIPTOR_RESOURCE).toList().filter { url ->
            url.openStream().use { stream -> readRoot(stream).pluginId() == PLUGIN_ID }
        }
        check(descriptors.isNotEmpty()) { "$DESCRIPTOR_RESOURCE with id '$PLUGIN_ID' is not on the test classpath" }
        val url = descriptors.first()
        return url.openStream().use { stream -> readRoot(stream) }
    }

    private fun readRoot(stream: InputStream): Element {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream).documentElement
    }

    private fun Element.pluginId(): String = getElementsByTagName("id").item(0).textContent.trim()

    private fun serviceImplementations(root: Element): List<String> {
        val implementations = mutableListOf<String>()
        val extensions = root.getElementsByTagName("extensions").item(0) as Element
        val children = extensions.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node !is Element) {
                continue
            }
            if (node.tagName != "applicationService" && node.tagName != "projectService") {
                continue
            }
            implementations.add(node.getAttribute("serviceImplementation"))
        }
        return implementations
    }

    companion object {
        private const val DESCRIPTOR_RESOURCE = "META-INF/plugin.xml"
        private const val PLUGIN_ID = "com.github.fnunezkanut.bbcloud"

        private val ALLOWED_PARAMETER_TYPES = setOf(
            "com.intellij.openapi.project.Project",
            "com.intellij.openapi.module.Module",
            "kotlinx.coroutines.CoroutineScope"
        )
    }
}