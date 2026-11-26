package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.starters.local.StarterModuleBuilder
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal stand-in for [com.intellij.ide.starters.local.GeneratorContext] used by J2EE file templates.
 */
class ArmeriaWizardTemplateTestContext(
    val group: String = "com.example",
    val artifact: String = "demo",
    val version: String = "0.0.1-SNAPSHOT",
    val moduleName: String = "demo",
    val language: String = "kotlin",
    val testRunnerId: String = "junit5",
    private val libraries: Set<String> = emptySet(),
    private val versions: Map<Pair<String, String>, String> = defaultVersions(),
    private val bomProperties: Map<String, String> = defaultBomProperties(),
) {
    val rootPackage: String = StarterModuleBuilder.suggestPackageName(group, artifact)

    fun hasLibrary(libraryId: String): Boolean = libraryId in libraries

    fun hasAnyLibrary(vararg libraryIds: String): Boolean = libraryIds.any { it in libraries }

    fun hasLanguage(languageId: String): Boolean = language == languageId

    fun getVersion(
        groupId: String,
        artifactId: String,
    ): String = versions[groupId to artifactId] ?: "UNVERSIONED"

    fun getBomProperty(propertyId: String): String =
        bomProperties[propertyId] ?: throw IllegalArgumentException("Missing BOM property: $propertyId")

    companion object {
        fun defaultVersions(): Map<Pair<String, String>, String> {
            val document = parseStarterPom()
            val bomProperties = defaultBomProperties(document)
            val dependencyManagement = document.getElementsByTagName("dependencyManagement").item(0) as org.w3c.dom.Element
            val dependencies = dependencyManagement.getElementsByTagName("dependency")
            return buildMap {
                for (index in 0 until dependencies.length) {
                    val dependency = dependencies.item(index)
                    val groupId = dependency.childNodesText("groupId") ?: continue
                    val artifactId = dependency.childNodesText("artifactId") ?: continue
                    val version = dependency.childNodesText("version") ?: continue
                    put(groupId to artifactId, interpolateProperties(version, bomProperties))
                }
            }
        }

        fun defaultBomProperties(): Map<String, String> = defaultBomProperties(parseStarterPom())

        private fun defaultBomProperties(document: org.w3c.dom.Document): Map<String, String> {
            val propertiesNode = document.getElementsByTagName("properties").item(0) ?: return emptyMap()
            return buildMap {
                for (index in 0 until propertiesNode.childNodes.length) {
                    val property = propertiesNode.childNodes.item(index)
                    if (property.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                        put(property.nodeName, property.textContent.trim())
                    }
                }
            }
        }

        private fun interpolateProperties(
            value: String,
            properties: Map<String, String>,
        ): String =
            Regex("\\$\\{([^}]+)}").replace(value) { match ->
                properties[match.groupValues[1]] ?: match.value
            }

        private fun parseStarterPom(): org.w3c.dom.Document {
            val input =
                requireNotNull(
                    ArmeriaWizardTemplateTestContext::class.java.classLoader.getResourceAsStream("starters/armeria.pom"),
                ) { "Missing starters/armeria.pom test resource" }
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }
            return input.use { factory.newDocumentBuilder().parse(it) }
        }

        private fun org.w3c.dom.Node.childNodesText(tagName: String): String? {
            for (index in 0 until childNodes.length) {
                val child = childNodes.item(index)
                if (child.nodeName == tagName) {
                    return child.textContent.trim()
                }
            }
            return null
        }
    }
}
