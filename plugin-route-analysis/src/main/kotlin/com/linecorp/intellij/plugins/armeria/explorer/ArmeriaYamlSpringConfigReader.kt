package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

/**
 * Reads `armeria.*` Spring Boot settings from application YAML via the bundled YAML plugin PSI.
 * Call only when `org.jetbrains.plugins.yaml` is loaded.
 */
internal object ArmeriaYamlSpringConfigReader {
    fun read(psiFile: PsiFile): SpringArmeriaConfig? {
        val yamlFile = psiFile as? YAMLFile ?: return null
        val armeria = findTopLevelArmeriaMapping(yamlFile) ?: return SpringArmeriaConfig()
        val ports = readPorts(armeria)
        val internalServices = childMapping(armeria, "internal-services", "internalServices")
        val includeKv = internalServices?.let { childKeyValue(it, "include") }
        val includes = readIncludes(includeKv)
        val internalServicesPort = scalarText(childKeyValue(internalServices, "port")?.value)
        val docsPathKv = childKeyValue(armeria, "docs-path", "docsPath")
        val healthPathKv = childKeyValue(armeria, "health-check-path", "healthCheckPath")
        val metricsPathKv = childKeyValue(armeria, "metrics-path", "metricsPath")
        return SpringArmeriaConfig(
            ports = ports,
            includes = includes,
            docsPath = scalarText(docsPathKv?.value)
                ?: ArmeriaSpringConfigRouteCollector.DEFAULT_DOCS_PATH,
            healthPath = scalarText(healthPathKv?.value)
                ?: ArmeriaSpringConfigRouteCollector.DEFAULT_HEALTH_PATH,
            metricsPath = scalarText(metricsPathKv?.value)
                ?: ArmeriaSpringConfigRouteCollector.DEFAULT_METRICS_PATH,
            internalServicesPort = internalServicesPort,
            includeElement = includeKv,
            docsPathElement = docsPathKv,
            healthPathElement = healthPathKv,
            metricsPathElement = metricsPathKv,
        )
    }

    private fun findTopLevelArmeriaMapping(yamlFile: YAMLFile): YAMLMapping? {
        for (document in yamlFile.documents) {
            val root = document.topLevelValue as? YAMLMapping ?: continue
            val armeria = root.getKeyValueByKey("armeria") ?: continue
            val mapping = armeria.value as? YAMLMapping ?: continue
            return mapping
        }
        return null
    }

    private fun readPorts(armeria: YAMLMapping): List<SpringArmeriaPortBinding> {
        val portsKv = childKeyValue(armeria, "ports") ?: return emptyList()
        val sequence = portsKv.value as? YAMLSequence ?: return emptyList()
        val bindings = mutableListOf<SpringArmeriaPortBinding>()
        val seenPorts = mutableSetOf<String>()
        for (item in sequence.items) {
            val itemMapping = item.value as? YAMLMapping ?: continue
            val portKv = childKeyValue(itemMapping, "port")
            val port = scalarText(portKv?.value) ?: continue
            if (!seenPorts.add(port)) {
                continue
            }
            val protocols = readProtocols(childKeyValue(itemMapping, "protocols"))
            bindings += SpringArmeriaPortBinding(
                port = port,
                protocols = protocols,
                element = portKv ?: item,
            )
        }
        return bindings
    }

    private fun readProtocols(protocolsKv: YAMLKeyValue?): List<String> {
        val value = protocolsKv?.value
        val tokens = when (value) {
            is YAMLSequence -> value.items.mapNotNull { scalarText(it.value) }
            is YAMLScalar -> ArmeriaSpringConfigRouteCollector.splitScalarList(value.textValue)
            else -> emptyList()
        }
        return tokens
            .map { it.uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf("HTTP") }
    }

    private fun readIncludes(includeKv: YAMLKeyValue?): Set<String> {
        if (includeKv == null) {
            return emptySet()
        }
        val value = includeKv.value
        val tokens = when (value) {
            is YAMLSequence -> value.items.mapNotNull { scalarText(it.value) }.map { it.lowercase() }.toSet()
            is YAMLScalar -> ArmeriaSpringConfigRouteCollector.parseIncludeTokens(value.textValue)
            null -> emptySet()
            else -> emptySet()
        }
        return ArmeriaSpringConfigRouteCollector.expandIncludes(tokens)
    }

    private fun childMapping(parent: YAMLMapping, vararg keys: String): YAMLMapping? {
        val kv = childKeyValue(parent, *keys) ?: return null
        return kv.value as? YAMLMapping
    }

    private fun childKeyValue(parent: YAMLMapping?, vararg keys: String): YAMLKeyValue? {
        if (parent == null) {
            return null
        }
        for (key in keys) {
            parent.getKeyValueByKey(key)?.let { return it }
        }
        return null
    }

    private fun scalarText(value: YAMLValue?): String? {
        val scalar = value as? YAMLScalar ?: return null
        val text = scalar.textValue.trim()
        return text.takeIf { it.isNotEmpty() }
    }
}
