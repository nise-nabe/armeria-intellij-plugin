package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal data class ArmeriaParamBinding(
    val name: String,
    val highlight: PsiElement,
)

internal data class ArmeriaParamPathVariableFinding(
    val highlight: PsiElement,
    val messageKey: String,
    val messageArg: String,
)

internal object ArmeriaParamPathVariableMismatch {
    fun findings(
        pathVariables: Set<String>,
        bindings: List<ArmeriaParamBinding>,
        missingHighlight: PsiElement,
    ): List<ArmeriaParamPathVariableFinding> {
        if (pathVariables.isEmpty()) {
            return emptyList()
        }
        val paramNames = bindings.mapTo(linkedSetOf()) { it.name }
        val missing = pathVariables.filterNot { it in paramNames }
        if (missing.isEmpty()) {
            return emptyList()
        }
        val unused = bindings.filter { it.name !in pathVariables }
        return buildList {
            add(
                ArmeriaParamPathVariableFinding(
                    highlight = missingHighlight,
                    messageKey = "inspection.param.path.variable.missing",
                    messageArg = missing.joinToString(),
                ),
            )
            for (binding in unused) {
                add(
                    ArmeriaParamPathVariableFinding(
                        highlight = binding.highlight,
                        messageKey = "inspection.param.path.variable.unused",
                        messageArg = binding.name,
                    ),
                )
            }
        }
    }

    fun pathVariables(method: PsiMethod): Set<String> {
        val route = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return emptySet()
        val classPrefix =
            ArmeriaRouteSupport.extractPrimaryPath(
                method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
            )
        val routePaths = ArmeriaRouteSupport.extractPaths(route.first) + ArmeriaRouteSupport.extractPathAnnotations(method)
        return buildSet {
            addAll(ArmeriaPathVariableSupport.extractPathVariables(classPrefix))
            routePaths.ifEmpty { listOf("/") }.forEach { addAll(ArmeriaPathVariableSupport.extractPathVariables(it)) }
        }
    }

    fun paramBindings(method: PsiMethod): List<ArmeriaParamBinding> =
        method.parameterList.parameters.mapNotNull { parameter ->
            val annotation = parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION) ?: return@mapNotNull null
            val explicit =
                ArmeriaRouteSupport
                    .extractStrings(annotation.findDeclaredAttributeValue("value"))
                    .firstOrNull { it.isNotBlank() }
            val name = explicit ?: parameter.name ?: return@mapNotNull null
            ArmeriaParamBinding(name, annotation.nameReferenceElement ?: annotation)
        }
}
