package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

internal object ArmeriaDataFetcherResolutionSupport {
    private val DATA_FETCHER_METHODS = setOf("dataFetcher", "dataFetchers", "DataFetcher")

    fun dataFetcherCallHasBlockingTarget(call: KtCallExpression): Boolean =
        call.valueArguments.any { argument ->
            val expression = argument.getArgumentExpression() ?: return@any false
            if (expression is KtLambdaExpression) {
                return@any false
            }
            val klass = resolveDataFetcherFromExpression(expression) ?: return@any false
            dataFetcherClassHasBlockingCall(klass)
        }

    fun resolveDataFetcherFromExpression(expression: KtExpression): PsiElement? =
        resolveDataFetcherFromExpression(expression, mutableSetOf())

    private fun resolveDataFetcherFromExpression(
        expression: KtExpression,
        visited: MutableSet<PsiElement>,
    ): PsiElement? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        if (!visited.add(unwrapped)) {
            return null
        }
        return when (unwrapped) {
            is KtCallExpression -> {
                val className = ArmeriaKotlinExpressionSupport.resolveCallName(unwrapped) ?: return null
                if (className in DATA_FETCHER_METHODS) {
                    return null
                }
                ArmeriaMissingBlockingKotlinSupport
                    .resolveClassByName(unwrapped, className)
                    ?.takeIf(ArmeriaMissingBlockingKotlinSupport::isDataFetcherType)
            }

            is KtNameReferenceExpression -> {
                resolveFromNameReference(unwrapped, visited)
            }

            is KtDotQualifiedExpression -> {
                val selector = unwrapped.selectorExpression ?: return null
                resolveDataFetcherFromExpression(selector, visited)
            }

            else -> {
                null
            }
        }
    }

    private fun resolveFromNameReference(
        reference: KtNameReferenceExpression,
        visited: MutableSet<PsiElement>,
    ): PsiElement? {
        when (val resolved = reference.references.firstNotNullOfOrNull { it.resolve() }) {
            is KtProperty -> {
                return propertyDataFetcher(resolved, visited)
            }

            is KtParameter -> {
                resolved.defaultValue?.let { resolveDataFetcherFromExpression(it, visited) }?.let { return it }
                val typeName =
                    resolved.typeReference
                        ?.text
                        ?.substringBefore('<')
                        ?.trim()
                return typeName?.let { findDataFetcherClassInFile(reference, it) }
            }

            is PsiVariable -> {
                val typeClass = (resolved.type as? PsiClassType)?.resolve()
                if (typeClass != null &&
                    ArmeriaMissingBlockingSupport.isDataFetcherClass(typeClass) &&
                    typeClass.qualifiedName != ArmeriaGraphqlBlockingSupport.DATA_FETCHER_CLASS
                ) {
                    return typeClass
                }
            }

            is KtClassOrObject -> {
                return resolved.takeIf(ArmeriaMissingBlockingKotlinSupport::isDataFetcherHierarchy)
            }

            is PsiClass -> {
                return resolved.takeIf { ArmeriaMissingBlockingSupport.isDataFetcherClass(it) }
            }
        }
        return findPropertyDataFetcher(reference, reference.getReferencedName(), visited)
    }

    private fun propertyDataFetcher(
        property: KtProperty,
        visited: MutableSet<PsiElement>,
    ): PsiElement? {
        property.initializer?.let { resolveDataFetcherFromExpression(it, visited) }?.let { return it }
        val typeName =
            property.typeReference
                ?.text
                ?.substringBefore('<')
                ?.trim() ?: return null
        return findDataFetcherClassInFile(property, typeName)
    }

    private fun findPropertyDataFetcher(
        anchor: PsiElement,
        name: String,
        visited: MutableSet<PsiElement>,
    ): PsiElement? {
        val function = PsiTreeUtil.getParentOfType(anchor, KtNamedFunction::class.java) ?: return null
        var found: PsiElement? = null
        function.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val property = element as? KtProperty ?: return@forEachDescendant
            if (property.name != name) {
                return@forEachDescendant
            }
            if (PsiTreeUtil.getParentOfType(property, KtNamedFunction::class.java) != function) {
                return@forEachDescendant
            }
            found = propertyDataFetcher(property, visited)
        }
        return found
    }

    private fun findDataFetcherClassInFile(
        anchor: PsiElement,
        className: String,
    ): PsiElement? {
        if (className.isEmpty() || className == "DataFetcher") {
            return null
        }
        val file = anchor.containingFile as? KtFile ?: return null
        file.declarations
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull { it.name == className && ArmeriaMissingBlockingKotlinSupport.isDataFetcherHierarchy(it) }
            ?.let { return it }
        val facade = JavaPsiFacade.getInstance(file.project)
        file.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == className || it.endsWith(".$className") }
            ?.let { imported ->
                facade.findClass(imported, file.resolveScope)?.let { cls ->
                    if (ArmeriaMissingBlockingSupport.isDataFetcherClass(cls)) {
                        return cls
                    }
                }
            }
        val pkg = file.packageFqName.asString()
        val fqn = if (pkg.isEmpty()) className else "$pkg.$className"
        return facade.findClass(fqn, file.resolveScope)?.takeIf {
            ArmeriaMissingBlockingSupport.isDataFetcherClass(it)
        }
    }

    fun dataFetcherClassHasBlockingCall(klass: PsiElement): Boolean =
        when (klass) {
            is KtClassOrObject -> {
                if (ArmeriaMissingBlockingKotlinSupport.hasNonBlocking(klass.annotationEntries)) {
                    false
                } else {
                    klass.declarations.filterIsInstance<KtNamedFunction>().any { function ->
                        function.name == "get" &&
                            !ArmeriaMissingBlockingKotlinSupport.hasNonBlocking(function.annotationEntries) &&
                            ArmeriaMissingBlockingKotlinSupport.findings(function).isNotEmpty()
                    }
                }
            }

            is PsiClass -> {
                !ArmeriaMissingBlockingSupport.hasNonBlocking(klass) &&
                    klass.methods.any { method ->
                        ArmeriaMissingBlockingSupport.isDataFetcherGet(method) &&
                            !ArmeriaMissingBlockingSupport.hasNonBlocking(method) &&
                            ArmeriaMissingBlockingSupport.findings(method).isNotEmpty()
                    }
            }

            else -> {
                false
            }
        }
}
