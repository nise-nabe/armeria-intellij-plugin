package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement

internal data class SpringArmeriaPortBinding(
    val port: String,
    val protocols: List<String>,
    val element: PsiElement? = null,
)

internal data class SpringArmeriaConfig(
    val ports: List<SpringArmeriaPortBinding> = emptyList(),
    val includes: Set<String> = emptySet(),
    val docsPath: String = SpringArmeriaConfigSemantics.DEFAULT_DOCS_PATH,
    val healthPath: String = SpringArmeriaConfigSemantics.DEFAULT_HEALTH_PATH,
    val metricsPath: String = SpringArmeriaConfigSemantics.DEFAULT_METRICS_PATH,
    val internalServicesPort: String? = null,
    val includeElement: PsiElement? = null,
    val docsPathElement: PsiElement? = null,
    val healthPathElement: PsiElement? = null,
    val metricsPathElement: PsiElement? = null,
)
