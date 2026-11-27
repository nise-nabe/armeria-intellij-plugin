package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.toArmeriaLightClass
import com.linecorp.intellij.plugins.armeria.test.ArmeriaJUnitServerExtensionSupport.REGISTER_EXTENSION_ANNOTATION
import com.linecorp.intellij.plugins.armeria.test.ArmeriaJUnitServerExtensionSupport.REGISTER_EXTENSION_ANNOTATION_SHORT
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinJUnitServerExtensionSupport {
    fun serverExtensionFromKotlinFunction(function: KtNamedFunction): ArmeriaJUnitServerExtension? {
        if (function.valueParameters.isNotEmpty() || function.receiverTypeReference != null) {
            return null
        }
        val containingDeclaration = kotlinClassBodyOwner(function) ?: return null
        if (containingDeclaration is KtObjectDeclaration &&
            containingDeclaration.isCompanion() &&
            !function.hasJvmStaticAnnotation()
        ) {
            return null
        }
        if (!function.annotationEntries.any { it.isRegisterExtensionAnnotation() }) {
            return null
        }
        if (!isKotlinServerExtensionFunction(function, function.project)) {
            return null
        }
        val variableName = function.name ?: return null
        val containingClassName = kotlinContainingClassName(containingDeclaration) ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = function,
            variableName = variableName,
            containingClassName = containingClassName,
            moduleName = ArmeriaTestMetadata.moduleName(function),
            isFactoryMethod = true,
        )
    }

    fun serverExtensionFromKotlinProperty(property: KtProperty): ArmeriaJUnitServerExtension? {
        if (!property.annotationEntries.any { it.isRegisterExtensionAnnotation() }) {
            return null
        }
        if (!isKotlinServerExtensionProperty(property, property.project)) {
            return null
        }
        val variableName = property.name ?: return null
        val containingDeclaration = kotlinClassBodyOwner(property) ?: return null
        val containingClassName = kotlinContainingClassName(containingDeclaration) ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = property,
            variableName = variableName,
            containingClassName = containingClassName,
            moduleName = ArmeriaTestMetadata.moduleName(property),
        )
    }

    private fun KtAnnotationEntry.isRegisterExtensionAnnotation(): Boolean =
        when (qualifiedName()) {
            REGISTER_EXTENSION_ANNOTATION -> true
            null -> hasValidatedRegisterExtensionImport()
            else -> false
        }

    private fun KtAnnotationEntry.hasValidatedRegisterExtensionImport(): Boolean {
        if (shortName?.asString() != REGISTER_EXTENSION_ANNOTATION_SHORT) {
            return false
        }
        containingKtFile.importDirectives.forEach { directive ->
            val importPath = directive.importPath ?: return@forEach
            if (importPath.isAllUnder) {
                val packageName =
                    importPath.pathStr
                        .removeSuffix(".*")
                        .removeSuffix("*")
                        .trimEnd('.')
                if ("$packageName.$REGISTER_EXTENSION_ANNOTATION_SHORT" == REGISTER_EXTENSION_ANNOTATION) {
                    return true
                }
            } else {
                val path = importPath.pathStr
                if (path == REGISTER_EXTENSION_ANNOTATION || path.endsWith(".$REGISTER_EXTENSION_ANNOTATION_SHORT")) {
                    return true
                }
            }
        }
        return containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .any { it.name == REGISTER_EXTENSION_ANNOTATION_SHORT }
    }

    private fun KtAnnotationEntry.qualifiedName(): String? {
        resolveAnnotationType()?.let { return it }
        val shortName = shortName?.asString() ?: return null
        containingKtFile.importDirectives.forEach { directive ->
            val importPath = directive.importPath ?: return@forEach
            if (importPath.isAllUnder) {
                val packageName =
                    importPath.pathStr
                        .removeSuffix(".*")
                        .removeSuffix("*")
                        .trimEnd('.')
                if (!packageName.isNullOrEmpty()) {
                    return "$packageName.$shortName"
                }
            } else {
                val path = importPath.pathStr
                if (path == shortName || path.endsWith(".$shortName")) {
                    return path
                }
            }
        }
        return containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .firstOrNull { it.name == shortName }
            ?.fqName
            ?.asString()
    }

    private fun KtAnnotationEntry.resolveAnnotationType(): String? {
        val candidates =
            listOfNotNull(
                typeReference?.references?.firstOrNull()?.resolve(),
                calleeExpression?.references?.firstOrNull()?.resolve(),
            )
        for (resolved in candidates) {
            when (resolved) {
                is PsiClass -> resolved.qualifiedName?.let { return it }
                is KtClass -> resolved.fqName?.asString()?.let { return it }
            }
        }
        return null
    }

    fun enclosingTestClassName(element: PsiElement): String? {
        if (isKotlinPluginAvailable()) {
            element.getParentOfType<KtObjectDeclaration>(true)?.let { objectDeclaration ->
                return kotlinContainingClassName(objectDeclaration)
            }
            element
                .getParentOfType<KtClass>(true)
                ?.fqName
                ?.asString()
                ?.let { return it }
        }
        return PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName
    }

    fun enclosingServerExtension(
        element: PsiElement,
        scope: GlobalSearchScope,
        cachedExtensions: MutableMap<String, List<ArmeriaJUnitServerExtension>>? = null,
    ): ArmeriaJUnitServerExtension? {
        val className = enclosingTestClassName(element) ?: return null
        val project = element.project
        val extensions =
            cachedExtensions?.getOrPut(className) {
                serverExtensionsAccessibleFrom(className, project, scope)
            } ?: serverExtensionsAccessibleFrom(className, project, scope)
        if (extensions.isEmpty()) {
            return null
        }
        return ArmeriaJUnitServerExtensionSupport.resolveScopedExtension(extensions)
    }

    fun serverExtensionsAccessibleFrom(
        testClassName: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<ArmeriaJUnitServerExtension> =
        ArmeriaJUnitServerExtensionCollector
            .collect(project)
            .filter { extension ->
                ArmeriaJUnitServerExtensionSupport.canAccessServerExtension(
                    testClassName,
                    extension.containingClassName,
                    project,
                    scope,
                )
            }

    fun kotlinClassQualifiedName(psiClass: PsiClass): String? = toKtClassOrObject(psiClass)?.fqName?.asString()

    fun kotlinClassPackageName(psiClass: PsiClass): String? = toKtClassOrObject(psiClass)?.containingKtFile?.packageFqName?.asString()

    fun serverExtensionsInKotlinClass(psiClass: PsiClass): List<ArmeriaJUnitServerExtension> {
        val classOrObject = toKtClassOrObject(psiClass) ?: return emptyList()
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        classOrObject.declarations.forEach { declaration ->
            when (declaration) {
                is KtProperty -> serverExtensionFromKotlinProperty(declaration)?.let(extensions::add)
                is KtNamedFunction -> serverExtensionFromKotlinFunction(declaration)?.let(extensions::add)
            }
        }
        if (classOrObject is KtClass) {
            for (companion in classOrObject.companionObjects) {
                companion.declarations.forEach { declaration ->
                    when (declaration) {
                        is KtProperty -> serverExtensionFromKotlinProperty(declaration)?.let(extensions::add)
                        is KtNamedFunction -> serverExtensionFromKotlinFunction(declaration)?.let(extensions::add)
                    }
                }
            }
        }
        return extensions.distinctBy { "${it.containingClassName}#${it.variableName}" }
    }

    fun kotlinSuperTypeNames(
        className: String,
        project: Project,
        scope: GlobalSearchScope,
    ): Set<String> = findKtClass(className, project, scope)?.let(::collectKotlinSuperTypeNames) ?: emptySet()

    private fun findKtClass(
        className: String,
        project: Project,
        scope: GlobalSearchScope,
    ): KtClass? {
        val psiClass = JavaPsiFacade.getInstance(project).findClass(className, scope) ?: return null
        return toKtClass(psiClass)
    }

    private fun addKotlinSuperTypeNames(
        ktClass: KtClass,
        names: MutableSet<String>,
    ) {
        val queue = ArrayDeque<KtClass>()
        queue.add(ktClass)
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val name = current.fqName?.asString() ?: continue
            if (!visited.add(name)) {
                continue
            }
            names.add(name)
            current.superTypeListEntries.forEach { entry ->
                val resolved =
                    entry.typeReference
                        ?.references
                        ?.firstOrNull()
                        ?.resolve()
                when (resolved) {
                    is KtClass -> queue.add(resolved)
                    is PsiClass -> toKtClass(resolved)?.let(queue::add)
                }
            }
        }
    }

    private fun collectKotlinSuperTypeNames(ktClass: KtClass): Set<String> {
        val names = linkedSetOf<String>()
        addKotlinSuperTypeNames(ktClass, names)
        return names
    }

    fun toKtClass(psiClass: PsiClass): KtClass? = toKtClassOrObject(psiClass) as? KtClass

    fun toKtClassOrObject(psiClass: PsiClass): KtClassOrObject? {
        (psiClass as? KtClassOrObject)?.let { return it }
        (psiClass.navigationElement as? KtClassOrObject)?.let { return it }
        (psiClass.originalElement as? KtClassOrObject)?.let { return it }
        val qualifiedName = psiClass.qualifiedName ?: return null
        val ktFile = psiClass.containingFile as? KtFile ?: return null
        return ktFile.declarations.filterIsInstance<KtClassOrObject>().firstOrNull { it.fqName?.asString() == qualifiedName }
    }

    private fun isKotlinServerExtensionProperty(
        property: KtProperty,
        project: Project,
    ): Boolean {
        property.typeReference?.let { typeReference ->
            when (val resolved = typeReference.references.firstOrNull()?.resolve()) {
                is PsiClass -> {
                    return ArmeriaJUnitServerExtensionSupport.isServerExtensionClass(resolved, project)
                }

                is KtClass -> {
                    return isKotlinServerExtensionClass(resolved, project)
                }
            }
            if (typeReference.text.contains("ServerExtension")) {
                return true
            }
        }
        val initializer = property.initializer ?: return false
        return isKotlinServerExtensionInitializer(initializer, project)
    }

    private fun isKotlinServerExtensionFunction(
        function: KtNamedFunction,
        project: Project,
    ): Boolean {
        function.typeReference?.let { typeReference ->
            when (val resolved = typeReference.references.firstOrNull()?.resolve()) {
                is PsiClass -> {
                    return ArmeriaJUnitServerExtensionSupport.isServerExtensionClass(resolved, project)
                }

                is KtClass -> {
                    return isKotlinServerExtensionClass(resolved, project)
                }
            }
            if (typeReference.text.contains("ServerExtension")) {
                return true
            }
        }
        val bodyExpression = function.bodyExpression ?: return false
        return isKotlinServerExtensionInitializer(bodyExpression, project)
    }

    private fun isSameOrEnclosingOrSuperClass(
        resolved: PsiClass,
        context: PsiElement,
    ): Boolean {
        var current = PsiTreeUtil.getParentOfType(context, PsiClass::class.java)
        while (current != null) {
            if (current.isEquivalentTo(resolved) || current.isInheritor(resolved, true)) {
                return true
            }
            current = current.containingClass
        }
        if (!isKotlinPluginAvailable()) {
            return false
        }
        var kotlinClass = PsiTreeUtil.getParentOfType(context, KtClassOrObject::class.java)
        while (kotlinClass != null) {
            if (kotlinTypeIsSameOrInherits(kotlinClass, resolved)) {
                return true
            }
            kotlinClass =
                if (kotlinClass is KtObjectDeclaration && kotlinClass.isCompanion()) {
                    kotlinClassBodyOwner(kotlinClass)
                } else {
                    PsiTreeUtil.getParentOfType(kotlinClass, KtClassOrObject::class.java)
                }
        }
        return false
    }

    private fun kotlinTypeIsSameOrInherits(
        kotlinClass: KtClassOrObject,
        resolved: PsiClass,
    ): Boolean {
        val light = kotlinClass.toArmeriaLightClass()
        if (light != null && (light.isEquivalentTo(resolved) || light.isInheritor(resolved, true))) {
            return true
        }
        val resolvedName = resolved.qualifiedName
        if (resolvedName != null && resolvedName == kotlinClass.fqName?.asString()) {
            return true
        }
        if (kotlinClass.name == resolved.name) {
            return true
        }
        val ktClass = kotlinClass as? KtClass ?: return false
        if (resolvedName != null && resolvedName in collectKotlinSuperTypeNames(ktClass)) {
            return true
        }
        return resolved.name != null &&
            ktClass.superTypeListEntries.any { entry ->
                entry.typeAsUserType?.referencedName == resolved.name
            }
    }

    private fun isKotlinServerExtensionClass(
        ktClass: KtClass,
        project: Project,
    ): Boolean =
        ktClass.toArmeriaLightClass()?.let {
            ArmeriaJUnitServerExtensionSupport.isServerExtensionClass(it, project)
        } == true

    private fun isKotlinServerExtensionInitializer(
        initializer: KtExpression,
        project: Project,
    ): Boolean {
        val objectDeclaration =
            when (initializer) {
                is KtObjectLiteralExpression -> initializer.objectDeclaration
                else -> null
            } ?: return false
        return objectDeclaration.superTypeListEntries.any { entry ->
            when (
                val resolved =
                    entry.typeReference
                        ?.references
                        ?.firstOrNull()
                        ?.resolve()
            ) {
                is PsiClass -> {
                    ArmeriaJUnitServerExtensionSupport.isServerExtensionClass(resolved, project)
                }

                is KtClass -> {
                    isKotlinServerExtensionClass(resolved, project)
                }

                else -> {
                    entry.typeReference?.text?.contains("ServerExtension") == true
                }
            }
        }
    }

    fun referencesServerHttpUri(
        expression: PsiElement,
        serverVariableName: String,
    ): Boolean {
        if (!isKotlinPluginAvailable()) {
            return false
        }
        (expression as? KtCallExpression)?.let { call ->
            if (call.calleeExpression?.text != "httpUri") {
                return false
            }
            val parent = call.parent as? KtDotQualifiedExpression ?: return false
            if (parent.selectorExpression != call) {
                return false
            }
            return matchesKotlinServerReceiver(parent.receiverExpression, serverVariableName)
        }
        (expression as? KtDotQualifiedExpression)?.let { qualified ->
            val call = qualified.selectorExpression as? KtCallExpression ?: return false
            if (call.calleeExpression?.text != "httpUri") {
                return false
            }
            return matchesKotlinServerReceiver(qualified.receiverExpression, serverVariableName)
        }
        return false
    }

    fun matchesKotlinServerReceiver(
        expression: KtExpression,
        serverVariableName: String,
    ): Boolean =
        when (expression) {
            is KtNameReferenceExpression -> {
                expression.getReferencedName() == serverVariableName
            }

            is KtCallExpression -> {
                matchesKotlinFactoryCall(expression, serverVariableName)
            }

            is KtDotQualifiedExpression -> {
                val factoryCall = expression.selectorExpression as? KtCallExpression ?: return false
                if (!matchesKotlinFactoryCall(factoryCall, serverVariableName)) {
                    return false
                }
                isValidKotlinFactoryReceiver(expression.receiverExpression)
            }

            else -> {
                false
            }
        }

    private fun matchesKotlinFactoryCall(
        expression: KtCallExpression,
        serverVariableName: String,
    ): Boolean {
        if (expression.valueArguments.isNotEmpty()) {
            return false
        }
        val callee = expression.calleeExpression ?: return false
        if (callee.text == serverVariableName) {
            return true
        }
        if (callee is KtDotQualifiedExpression) {
            val nameRef = callee.selectorExpression as? KtNameReferenceExpression ?: return false
            if (nameRef.getReferencedName() != serverVariableName) {
                return false
            }
            return isValidKotlinFactoryReceiver(callee.receiverExpression)
        }
        return false
    }

    private fun isValidKotlinFactoryReceiver(receiver: KtExpression): Boolean =
        when (receiver) {
            is KtThisExpression -> true
            is KtNameReferenceExpression -> isKotlinClassReference(receiver)
            else -> false
        }

    private fun isKotlinPluginAvailable(): Boolean = ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()

    private fun isKotlinClassReference(reference: KtNameReferenceExpression): Boolean {
        when (val resolved = reference.reference?.resolve()) {
            is PsiClass -> {
                if (isSameOrEnclosingOrSuperClass(resolved, reference)) {
                    return true
                }
            }

            is KtClassOrObject -> {
                val light = resolved.toArmeriaLightClass()
                if (light != null && isSameOrEnclosingOrSuperClass(light, reference)) {
                    return true
                }
            }
        }
        val name = reference.getReferencedName()
        var current = reference.getParentOfType<KtClassOrObject>(true)
        while (current != null) {
            if (current.name == name) {
                return true
            }
            if (current is KtClass &&
                current.superTypeListEntries.any { it.typeAsUserType?.referencedName == name }
            ) {
                return true
            }
            current =
                if (current is KtObjectDeclaration && current.isCompanion()) {
                    kotlinClassBodyOwner(current)
                } else {
                    current.getParentOfType<KtClassOrObject>(true)
                }
        }
        return false
    }

    private fun kotlinClassBodyOwner(declaration: KtDeclaration): KtClassOrObject? =
        (declaration.parent as? KtClassBody)?.parent as? KtClassOrObject

    private fun kotlinContainingClassName(declaration: KtClassOrObject): String? {
        val attributed =
            if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                kotlinClassBodyOwner(declaration) as? KtClass ?: return null
            } else {
                declaration
            }
        return attributed.fqName?.asString()
    }

    private fun KtNamedFunction.hasJvmStaticAnnotation(): Boolean = annotationEntries.any { it.shortName?.asString() == "JvmStatic" }

    internal fun referencesServerVariable(
        element: PsiElement,
        serverVariableName: String,
    ): Boolean {
        if (!isKotlinPluginAvailable()) {
            return false
        }
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return false
        return serverVariableName in referencedServerVariableNamesFromKotlinCall(call)
    }

    private fun referencedServerVariableNamesFromKotlinCall(call: KtCallExpression): Set<String> {
        val names = linkedSetOf<String>()
        var current: KtExpression = call
        while (true) {
            val parent = current.parent as? KtDotQualifiedExpression ?: break
            if (parent.selectorExpression != current) {
                break
            }
            collectKotlinReceiverChainNames(parent.receiverExpression, names)
            current = parent.receiverExpression
        }
        return names
    }

    private fun collectKotlinReceiverChainNames(
        expression: KtExpression,
        names: MutableSet<String>,
    ) {
        when (expression) {
            is KtNameReferenceExpression -> {
                expression.getReferencedName().let(names::add)
            }

            is KtCallExpression -> {
                (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()?.let(names::add)
                (expression.parent as? KtDotQualifiedExpression)
                    ?.takeIf { it.selectorExpression == expression }
                    ?.receiverExpression
                    ?.let { collectKotlinReceiverChainNames(it, names) }
            }

            is KtDotQualifiedExpression -> {
                expression.selectorExpression?.let { collectKotlinReceiverChainNames(it, names) }
                collectKotlinReceiverChainNames(expression.receiverExpression, names)
            }
        }
    }
}
