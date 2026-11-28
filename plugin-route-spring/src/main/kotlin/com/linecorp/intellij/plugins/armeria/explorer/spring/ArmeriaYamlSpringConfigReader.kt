package com.linecorp.intellij.plugins.armeria.explorer.spring
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLLanguage
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
    fun read(psiFile: PsiFile): SpringArmeriaConfig = readProfiledDocuments(psiFile).firstOrNull()?.config ?: SpringArmeriaConfig()

    fun readProfiledDocuments(psiFile: PsiFile): List<SpringArmeriaConfigDocument> {
        val yamlFile = resolveYamlFile(psiFile)
        return readYamlDocuments(yamlFile)
    }

    /**
     * Prefer the real YAML PSI for the file (key-level navigation). If the file is not a
     * [YAMLFile] — e.g. marked as Plain Text — parse a throwaway tree from text. Emit-time
     * navigation keeps only elements whose containing file matches the original config file.
     */
    private fun resolveYamlFile(psiFile: PsiFile): YAMLFile {
        (psiFile as? YAMLFile)?.let { return it }
        (psiFile.viewProvider.getPsi(YAMLLanguage.INSTANCE) as? YAMLFile)?.let { return it }
        return YAMLElementGenerator
            .getInstance(psiFile.project)
            .createDummyYamlWithText(psiFile.text)
    }

    private fun readYamlDocuments(yamlFile: YAMLFile): List<SpringArmeriaConfigDocument> {
        val parsed =
            yamlFile.documents.mapNotNull { document ->
                val root = document.topLevelValue as? YAMLMapping ?: return@mapNotNull null
                val armeria = root.getKeyValueByKey("armeria") ?: return@mapNotNull null
                val mapping = armeria.value as? YAMLMapping ?: return@mapNotNull null
                SpringArmeriaConfigDocument(
                    profile = readDocumentProfile(root),
                    config = readArmeriaMapping(mapping),
                )
            }
        if (parsed.isEmpty()) {
            return emptyList()
        }
        val baseDocuments = parsed.filter { it.profile == null }
        val baseConfig =
            baseDocuments
                .map { it.config }
                .fold(SpringArmeriaConfig(), ::mergeConfigs)
        val effectiveDocuments = mutableListOf<SpringArmeriaConfigDocument>()
        if (baseDocuments.isNotEmpty()) {
            effectiveDocuments += SpringArmeriaConfigDocument(profile = null, config = baseConfig)
        }
        for (profile in parsed.mapNotNull { it.profile }.distinct()) {
            val profileConfig =
                parsed
                    .filter { it.profile == profile }
                    .map { it.config }
                    .fold(baseConfig, ::mergeConfigs)
            effectiveDocuments += SpringArmeriaConfigDocument(profile = profile, config = profileConfig)
        }
        return effectiveDocuments
    }

    private fun readArmeriaMapping(armeria: YAMLMapping): SpringArmeriaConfig {
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
            docsPath = scalarText(docsPathKv?.value),
            healthPath = scalarText(healthPathKv?.value),
            metricsPath = scalarText(metricsPathKv?.value),
            internalServicesPort = internalServicesPort,
            includeElement = includeKv,
            docsPathElement = docsPathKv,
            healthPathElement = healthPathKv,
            metricsPathElement = metricsPathKv,
        )
    }

    private fun mergeConfigs(
        base: SpringArmeriaConfig,
        override: SpringArmeriaConfig,
    ): SpringArmeriaConfig =
        SpringArmeriaConfig(
            ports = override.ports.ifEmpty { base.ports },
            includes = override.includes.ifEmpty { base.includes },
            docsPath = override.docsPath ?: base.docsPath,
            healthPath = override.healthPath ?: base.healthPath,
            metricsPath = override.metricsPath ?: base.metricsPath,
            internalServicesPort = override.internalServicesPort ?: base.internalServicesPort,
            includeElement = override.includeElement ?: base.includeElement,
            docsPathElement = override.docsPathElement ?: base.docsPathElement,
            healthPathElement = override.healthPathElement ?: base.healthPathElement,
            metricsPathElement = override.metricsPathElement ?: base.metricsPathElement,
        )

    private fun readDocumentProfile(root: YAMLMapping): String? {
        val spring = childKeyValue(root, "spring")?.value as? YAMLMapping ?: return null
        val config = childKeyValue(spring, "config")?.value as? YAMLMapping
        val activate = childKeyValue(config, "activate")?.value as? YAMLMapping
        scalarText(childKeyValue(activate, "on-profile", "onProfile")?.value)?.let { return it }
        return scalarText(childKeyValue(spring, "profiles")?.value)
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
            bindings +=
                SpringArmeriaPortBinding(
                    port = port,
                    protocols = protocols,
                    // portKv is non-null here: scalarText(portKv?.value) already continued otherwise.
                    element = portKv,
                )
        }
        return bindings
    }

    private fun readProtocols(protocolsKv: YAMLKeyValue?): List<String> {
        val value = protocolsKv?.value
        val tokens =
            when (value) {
                is YAMLSequence -> value.items.mapNotNull { scalarText(it.value) }
                is YAMLScalar -> SpringArmeriaConfigSemantics.splitScalarList(value.textValue)
                else -> emptyList()
            }
        return SpringArmeriaConfigSemantics.normalizeProtocols(tokens)
    }

    private fun readIncludes(includeKv: YAMLKeyValue?): Set<String> {
        if (includeKv == null) {
            return emptySet()
        }
        val value = includeKv.value
        // Sequence items are already discrete tokens; scalars need comma/space tokenization.
        val tokens =
            when (value) {
                is YAMLSequence -> value.items.mapNotNull { scalarText(it.value) }.toSet()
                is YAMLScalar -> SpringArmeriaConfigSemantics.parseIncludeTokens(value.textValue)
                else -> emptySet()
            }
        return SpringArmeriaConfigSemantics.expandIncludes(tokens)
    }

    private fun childMapping(
        parent: YAMLMapping,
        vararg keys: String,
    ): YAMLMapping? {
        val kv = childKeyValue(parent, *keys) ?: return null
        return kv.value as? YAMLMapping
    }

    private fun childKeyValue(
        parent: YAMLMapping?,
        vararg keys: String,
    ): YAMLKeyValue? {
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
