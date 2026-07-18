package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement

internal data class SpringArmeriaPortBinding(
    val port: String,
    val protocols: List<String>,
    val element: PsiElement? = null,
)

/**
 * Parsed Spring Boot `armeria.*` settings.
 *
 * Path fields are nullable: absent keys stay `null` and are resolved to
 * [SpringArmeriaConfigSemantics] defaults only at emit time via [resolvedDocsPath] /
 * [resolvedHealthPath] / [resolvedMetricsPath]. PSI navigation anchors are populated by the
 * YAML reader only; the properties parser leaves them null.
 */
internal data class SpringArmeriaConfig(
    val ports: List<SpringArmeriaPortBinding> = emptyList(),
    val includes: Set<String> = emptySet(),
    val docsPath: String? = null,
    val healthPath: String? = null,
    val metricsPath: String? = null,
    val internalServicesPort: String? = null,
    val includeElement: PsiElement? = null,
    val docsPathElement: PsiElement? = null,
    val healthPathElement: PsiElement? = null,
    val metricsPathElement: PsiElement? = null,
) {
    fun resolvedDocsPath(): String =
        docsPath ?: SpringArmeriaConfigSemantics.DEFAULT_DOCS_PATH

    fun resolvedHealthPath(): String =
        healthPath ?: SpringArmeriaConfigSemantics.DEFAULT_HEALTH_PATH

    fun resolvedMetricsPath(): String =
        metricsPath ?: SpringArmeriaConfigSemantics.DEFAULT_METRICS_PATH
}
