package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinMethodRoute
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ArmeriaKotlinParamReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            ArmeriaKotlinParamReferenceProvider(),
        )
    }
}

private class ArmeriaKotlinParamReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val template = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        val value = template.decodedConstantString() ?: return PsiReference.EMPTY_ARRAY
        val entry = PsiTreeUtil.getParentOfType(template, KtAnnotationEntry::class.java) ?: return PsiReference.EMPTY_ARRAY
        val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return PsiReference.EMPTY_ARRAY
        val function = PsiTreeUtil.getParentOfType(entry, KtNamedFunction::class.java)
        val owner = PsiTreeUtil.getParentOfType(entry, KtClassOrObject::class.java)
        val valueRange = ElementManipulators.getValueTextRange(template)
        if (qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION) {
            if (function == null) {
                return PsiReference.EMPTY_ARRAY
            }
            return arrayOf(ArmeriaKotlinParamNameReference(template, valueRange, function, value))
        }
        if (!isKotlinPathAnnotation(qualifiedName)) {
            return PsiReference.EMPTY_ARRAY
        }
        val occurrences = ArmeriaPathVariableSupport.pathVariableOccurrences(value)
        if (function != null) {
            return occurrences
                .map { occurrence ->
                    ArmeriaKotlinPathVariableReference(
                        template = template,
                        rangeInElement = pathVariableRangeInHost(template, occurrence),
                        function = function,
                        variableName = occurrence.name,
                    )
                }.toTypedArray()
        }
        if (qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION && owner != null) {
            return occurrences
                .map { occurrence ->
                    ArmeriaKotlinPathPrefixVariableReference(
                        template = template,
                        rangeInElement = pathVariableRangeInHost(template, occurrence),
                        owner = owner,
                        variableName = occurrence.name,
                    )
                }.toTypedArray()
        }
        return PsiReference.EMPTY_ARRAY
    }
}

private fun isKotlinPathAnnotation(qualifiedName: String): Boolean =
    qualifiedName in ArmeriaRouteSupport.routeAnnotations ||
        qualifiedName == ArmeriaRouteSupport.PATH_ANNOTATION ||
        qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION

private class ArmeriaKotlinParamNameReference(
    template: KtStringTemplateExpression,
    rangeInElement: TextRange,
    private val function: KtNamedFunction,
    private val variableName: String,
) : PsiReferenceBase<KtStringTemplateExpression>(template, rangeInElement) {
    override fun resolve(): PsiElement = element

    override fun getVariants(): Array<Any> =
        ArmeriaKotlinAnnotationSupport
            .pathVariables(function)
            .map { name ->
                LookupElementBuilder
                    .create(name)
                    .withTypeText(message("completion.param.path.variable.type"))
            }.toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        renameKotlinPathVariable(function, variableName, newElementName)
        return element
    }
}

private class ArmeriaKotlinPathVariableReference(
    template: KtStringTemplateExpression,
    rangeInElement: TextRange,
    private val function: KtNamedFunction,
    private val variableName: String,
) : PsiReferenceBase<KtStringTemplateExpression>(template, rangeInElement, true) {
    override fun resolve(): PsiElement = element

    override fun getCanonicalText(): String = variableName

    override fun handleElementRename(newElementName: String): PsiElement {
        renameKotlinPathVariable(function, variableName, newElementName)
        return element
    }
}

private class ArmeriaKotlinPathPrefixVariableReference(
    template: KtStringTemplateExpression,
    rangeInElement: TextRange,
    private val owner: KtClassOrObject,
    private val variableName: String,
) : PsiReferenceBase<KtStringTemplateExpression>(template, rangeInElement, true) {
    override fun resolve(): PsiElement = element

    override fun getCanonicalText(): String = variableName

    override fun handleElementRename(newElementName: String): PsiElement {
        renameKotlinClassPathVariable(owner, variableName, newElementName)
        return element
    }
}

internal fun renameKotlinPathVariable(
    function: KtNamedFunction,
    oldName: String,
    newName: String,
) {
    if (oldName.isEmpty() || oldName == newName) {
        return
    }
    if (!ArmeriaPathVariableSupport.isRenameableVariable(oldName, kotlinRouteRawPaths(function))) {
        return
    }
    val owner = PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)
    if (owner != null && kotlinClassPrefixHasVariable(owner, oldName)) {
        renameKotlinClassPathVariable(owner, oldName, newName)
        return
    }
    rewriteKotlinPathVariableEntries(kotlinEntriesOnFunction(function), oldName, newName)
    renameKotlinImplicitParams(function, oldName, newName)
}

internal fun renameKotlinClassPathVariable(
    owner: KtClassOrObject,
    oldName: String,
    newName: String,
) {
    if (oldName.isEmpty() || oldName == newName) {
        return
    }
    if (!ArmeriaPathVariableSupport.isRenameableVariable(oldName, kotlinClassRawPaths(owner))) {
        return
    }
    val entries = mutableListOf<KtAnnotationEntry>()
    entries += owner.annotationEntries
    owner.declarations.filterIsInstance<KtNamedFunction>().forEach { function ->
        entries += kotlinEntriesOnFunction(function)
        renameKotlinImplicitParams(function, oldName, newName)
    }
    rewriteKotlinPathVariableEntries(entries, oldName, newName)
}

private fun kotlinClassPrefixHasVariable(
    owner: KtClassOrObject,
    name: String,
): Boolean {
    val prefixEntry =
        owner.annotationEntries.firstOrNull {
            ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
        } ?: return false
    return name in
        ArmeriaKotlinAnnotationSupport.extractStrings(prefixEntry).flatMap {
            ArmeriaPathVariableSupport.extractPathVariables(it)
        }
}

private fun kotlinRouteRawPaths(function: KtNamedFunction): List<String> {
    val route = ArmeriaKotlinMethodRoute.from(function) ?: return emptyList()
    return buildList {
        if (route.classPrefix.isNotEmpty()) {
            add(route.classPrefix)
        }
        addAll(route.rawPaths)
    }
}

private fun kotlinClassRawPaths(owner: KtClassOrObject): List<String> =
    buildList {
        owner.annotationEntries
            .firstOrNull {
                ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
            }?.let { entry ->
                addAll(ArmeriaKotlinAnnotationSupport.extractStrings(entry))
            }
        owner.declarations.filterIsInstance<KtNamedFunction>().forEach { function ->
            addAll(kotlinRouteRawPaths(function))
        }
    }

private fun kotlinEntriesOnFunction(function: KtNamedFunction): List<KtAnnotationEntry> =
    buildList {
        addAll(function.annotationEntries)
        function.valueParameters.forEach { parameter -> addAll(parameter.annotationEntries) }
    }

private fun renameKotlinImplicitParams(
    function: KtNamedFunction,
    oldName: String,
    newName: String,
) {
    function.valueParameters.forEach { parameter ->
        val entry =
            parameter.annotationEntries.firstOrNull {
                ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
            } ?: return@forEach
        val explicit = ArmeriaKotlinAnnotationSupport.extractStrings(entry).firstOrNull { it.isNotBlank() }
        if (explicit != null || parameter.name != oldName) {
            return@forEach
        }
        parameter.setName(newName)
    }
}

private fun rewriteKotlinPathVariableEntries(
    entries: List<KtAnnotationEntry>,
    oldName: String,
    newName: String,
) {
    for (entry in entries) {
        val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: continue
        when {
            isKotlinPathAnnotation(qualifiedName) ->
                replaceKotlinAnnotationStrings(entry) {
                    ArmeriaPathVariableSupport.replacePathVariableName(it, oldName, newName)
                }
            qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION ->
                replaceKotlinAnnotationStrings(entry) { current -> if (current == oldName) newName else current }
        }
    }
}

private fun replaceKotlinAnnotationStrings(
    entry: KtAnnotationEntry,
    transform: (String) -> String,
) {
    for (argument in entry.valueArguments) {
        replaceKotlinStringExpression(argument.getArgumentExpression(), transform)
    }
}

private fun replaceKotlinStringExpression(
    expression: KtExpression?,
    transform: (String) -> String,
) {
    when (expression) {
        is KtStringTemplateExpression -> {
            val current = expression.decodedConstantString() ?: return
            val updated = transform(current)
            if (updated != current) {
                ElementManipulators.handleContentChange(expression, updated)
            }
        }
        is KtCollectionLiteralExpression ->
            expression.getInnerExpressions().forEach { replaceKotlinStringExpression(it, transform) }
    }
}

internal fun KtStringTemplateExpression.decodedConstantString(): String? {
    if (entries.any { it is KtStringTemplateEntryWithExpression }) {
        return null
    }
    if (entries.isEmpty()) {
        return ""
    }
    return buildString {
        for (entry in entries) {
            when (entry) {
                is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                else -> append(entry.text)
            }
        }
    }
}

internal fun cookieNamesInKotlinClass(owner: KtClassOrObject): Set<String> =
    namedParameterValuesInKotlinClass(owner, ArmeriaRouteSupport.COOKIE_ANNOTATION)

internal fun attributeNamesInKotlinClass(owner: KtClassOrObject): Set<String> =
    namedParameterValuesInKotlinClass(owner, ArmeriaRouteSupport.ATTRIBUTE_ANNOTATION)

private fun namedParameterValuesInKotlinClass(
    owner: KtClassOrObject,
    annotationFqn: String,
): Set<String> {
    val names = linkedSetOf<String>()
    for (declaration in owner.declarations.filterIsInstance<KtNamedFunction>()) {
        for (parameter in declaration.valueParameters) {
            namedParameterValue(parameter, annotationFqn)?.let { names += it }
        }
    }
    return names
}

private fun namedParameterValue(
    parameter: KtParameter,
    annotationFqn: String,
): String? {
    val entry =
        parameter.annotationEntries.firstOrNull {
            ArmeriaKotlinAnnotationSupport.qualifiedName(it) == annotationFqn
        } ?: return null
    return ArmeriaKotlinAnnotationSupport.extractStrings(entry).firstOrNull { it.isNotBlank() }
}
