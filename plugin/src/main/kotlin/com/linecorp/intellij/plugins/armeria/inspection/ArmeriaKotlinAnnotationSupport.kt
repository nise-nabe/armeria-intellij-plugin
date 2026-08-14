package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType

internal object ArmeriaKotlinAnnotationSupport {
    fun qualifiedName(entry: KtAnnotationEntry): String? {
        resolveAnnotationType(entry)?.let { return it }
        val shortName = entry.shortName?.asString() ?: return null
        entry.containingKtFile.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == shortName || it.endsWith(".$shortName") }
            ?.let { return it }
        return entry.containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .firstOrNull { it.name == shortName }
            ?.fqName
            ?.asString()
    }

    fun extractStrings(
        entry: KtAnnotationEntry,
        attributeName: String = "value",
    ): List<String> {
        val named =
            entry.valueArguments
                .filter { it.getArgumentName()?.asName?.asString() == attributeName }
                .flatMap { argument ->
                    ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
                }
        if (named.isNotEmpty()) {
            return named
        }
        if (attributeName != "value") {
            return emptyList()
        }
        return entry.valueArguments
            .filter { it.getArgumentName() == null }
            .flatMap { argument ->
                ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
            }
    }

    fun pathVariables(function: KtNamedFunction): Set<String> {
        val route = ArmeriaKotlinMethodRoute.from(function) ?: return emptySet()
        return buildSet {
            addAll(ArmeriaPathVariableSupport.extractPathVariables(route.classPrefix))
            route.rawPaths.forEach { addAll(ArmeriaPathVariableSupport.extractPathVariables(it)) }
        }
    }

    fun paramBindings(function: KtNamedFunction): List<ArmeriaParamBinding> =
        buildList {
            function.valueParameters.forEach { parameter ->
                val entry =
                    parameter.annotationEntries.firstOrNull {
                        qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
                    }
                if (entry != null) {
                    val explicit = extractStrings(entry).firstOrNull { it.isNotBlank() }
                    val name = explicit ?: parameter.name ?: return@forEach
                    add(ArmeriaParamBinding(name))
                    return@forEach
                }
                addAll(beanParamBindings(parameter))
            }
        }

    private fun beanParamBindings(parameter: KtParameter): List<ArmeriaParamBinding> {
        val resolved = resolveParameterType(parameter) ?: return emptyList()
        return when (resolved) {
            is KtClass -> kotlinBeanParamBindings(resolved)
            is PsiClass ->
                (resolved.navigationElement as? KtClass)?.let { kotlinBeanParamBindings(it) }
                    ?: ArmeriaParamPathVariableMismatch.beanParamBindings(resolved)
            else -> emptyList()
        }
    }

    private fun resolveParameterType(parameter: KtParameter): PsiElement? {
        val typeRef = parameter.typeReference ?: return null
        val userType = typeRef.typeElement as? KtUserType
        userType
            ?.referenceExpression
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() }
            ?.let { return it }
        typeRef.references
            .firstNotNullOfOrNull { it.resolve() }
            ?.let { return it }
        val shortName = userType?.referencedName ?: return null
        return typeRef.containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .firstOrNull { it.name == shortName }
    }

    private fun kotlinBeanParamBindings(klass: KtClass): List<ArmeriaParamBinding> {
        if (klass.isInterface() || klass.isEnum() || klass.isAnnotation()) {
            return emptyList()
        }
        klass.fqName?.asString()?.let { qualifiedName ->
            if (!ArmeriaParamPathVariableMismatch.isUserBeanQualifiedName(qualifiedName)) {
                return emptyList()
            }
        }
        val names = linkedSetOf<String>()
        klass.primaryConstructorParameters.forEach { parameter ->
            kotlinParamName(parameter)?.let { names += it }
        }
        klass.declarations.filterIsInstance<KtProperty>().forEach { property ->
            val entry =
                property.annotationEntries.firstOrNull {
                    qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
                } ?: return@forEach
            val explicit = extractStrings(entry).firstOrNull { it.isNotBlank() }
            val name = explicit ?: property.name ?: return@forEach
            names += name
        }
        return names.map(::ArmeriaParamBinding)
    }

    private fun kotlinParamName(parameter: KtParameter): String? {
        val entry =
            parameter.annotationEntries.firstOrNull {
                qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
            } ?: return null
        val explicit = extractStrings(entry).firstOrNull { it.isNotBlank() }
        return explicit ?: parameter.name
    }

    private fun resolveAnnotationType(entry: KtAnnotationEntry): String? {
        val candidates =
            listOfNotNull(
                entry.typeReference
                    ?.references
                    ?.firstOrNull()
                    ?.resolve(),
                entry.calleeExpression
                    ?.references
                    ?.firstOrNull()
                    ?.resolve(),
            )
        for (resolved in candidates) {
            when (resolved) {
                is PsiClass -> resolved.qualifiedName?.let { return it }
                is KtClass -> resolved.fqName?.asString()?.let { return it }
            }
        }
        return null
    }
}
