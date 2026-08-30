package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal data class ArmeriaParamBinding(
    val name: String,
)

internal data class ArmeriaParamPathVariableFinding(
    val highlight: PsiElement,
    val messageKey: String,
    val messageArg: String,
)

internal object ArmeriaParamPathVariableMismatch {
    private val FRAMEWORK_TYPE_PREFIXES =
        listOf(
            "java.",
            "javax.",
            "jakarta.",
            "kotlin.",
            "com.linecorp.armeria.",
            "io.netty.",
        )

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
        return listOf(
            ArmeriaParamPathVariableFinding(
                highlight = missingHighlight,
                messageKey = "inspection.param.path.variable.missing",
                messageArg = missing.joinToString(),
            ),
        )
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
        buildList {
            method.parameterList.parameters.forEach { parameter ->
                val annotation = parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION)
                if (annotation != null) {
                    val explicit =
                        ArmeriaRouteSupport
                            .extractStrings(annotation.findDeclaredAttributeValue("value"))
                            .firstOrNull { it.isNotBlank() }
                    val name = explicit ?: parameter.name
                    add(ArmeriaParamBinding(name))
                    return@forEach
                }
                addAll(beanParamBindings(parameter))
            }
        }

    fun beanParamBindings(type: PsiClass): List<ArmeriaParamBinding> {
        if (!isUserBeanType(type)) {
            return emptyList()
        }
        val names = linkedSetOf<String>()
        type.allFields.forEach { field ->
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return@forEach
            }
            paramName(field.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION), field.name)?.let { names += it }
        }
        type.allMethods.forEach { method ->
            if (method.hasModifierProperty(PsiModifier.STATIC) ||
                method.parameterList.parametersCount != 1 ||
                !method.name.startsWith("set")
            ) {
                return@forEach
            }
            val annotation =
                method.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION)
                    ?: method.parameterList.parameters[0].getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION)
            val property = propertyNameFromSetter(method.name) ?: return@forEach
            paramName(annotation, property)?.let { names += it }
        }
        type.constructors.forEach { constructor ->
            constructor.parameterList.parameters.forEach { parameter ->
                paramName(
                    parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION),
                    parameter.name,
                )?.let { names += it }
            }
        }
        return names.map(::ArmeriaParamBinding)
    }

    private fun beanParamBindings(parameter: PsiParameter): List<ArmeriaParamBinding> {
        val type = (parameter.type as? PsiClassType)?.resolve() ?: return emptyList()
        return beanParamBindings(type)
    }

    private fun paramName(
        annotation: PsiAnnotation?,
        fallback: String?,
    ): String? {
        if (annotation == null) {
            return null
        }
        val explicit =
            ArmeriaRouteSupport
                .extractStrings(annotation.findDeclaredAttributeValue("value"))
                .firstOrNull { it.isNotBlank() }
        return explicit ?: fallback
    }

    private fun propertyNameFromSetter(methodName: String): String? {
        if (methodName.length < 4 || !methodName.startsWith("set")) {
            return null
        }
        val rest = methodName.substring(3)
        if (rest.isEmpty()) {
            return null
        }
        return rest.replaceFirstChar { it.lowercaseChar() }
    }

    internal fun isUserBeanType(type: PsiClass): Boolean {
        if (type.isEnum || type.isAnnotationType || type.isInterface) {
            return false
        }
        val qualifiedName = type.qualifiedName ?: return false
        return isUserBeanQualifiedName(qualifiedName)
    }

    internal fun isUserBeanQualifiedName(qualifiedName: String): Boolean = FRAMEWORK_TYPE_PREFIXES.none { qualifiedName.startsWith(it) }
}
