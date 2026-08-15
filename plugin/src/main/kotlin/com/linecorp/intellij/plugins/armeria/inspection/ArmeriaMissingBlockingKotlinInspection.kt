package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaMissingBlockingKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.blocking.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.blocking.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (!shouldInspect(function)) {
                    return
                }
                val body = function.bodyExpression ?: return
                body.forEachDescendant { element ->
                    val call = element as? KtCallExpression ?: return@forEachDescendant
                    if (!isOnInspectedFunctionPath(function, call)) {
                        return@forEachDescendant
                    }
                    val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return@forEachDescendant
                    val resolved = resolvePsiMethod(call)
                    val qualifierText =
                        (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
                            ?: (call.calleeExpression as? KtDotQualifiedExpression)?.receiverExpression?.text
                    if (!ArmeriaBlockingCallPatterns.isBlockingCall(
                            methodName = methodName,
                            ownerFqn = resolved?.containingClass?.qualifiedName,
                            unresolved = resolved == null,
                            qualifierText = qualifierText,
                            argumentCount = call.valueArguments.size,
                        )
                    ) {
                        return@forEachDescendant
                    }
                    holder.registerProblem(
                        highlight(call),
                        message("inspection.missing.blocking.problem", methodName),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }

    private fun shouldInspect(function: KtNamedFunction): Boolean {
        if (hasBlockingOrNonBlocking(function.annotationEntries) ||
            containingClass(function)?.annotationEntries?.let(::hasBlockingOrNonBlocking) == true
        ) {
            return false
        }
        if (ArmeriaKotlinMethodRoute.from(function) != null) {
            return true
        }
        return isGrpcServiceOverride(function)
    }

    private fun hasBlockingOrNonBlocking(entries: List<KtAnnotationEntry>): Boolean =
        entries.any { entry ->
            val name = ArmeriaKotlinAnnotationSupport.qualifiedName(entry)
            name == ArmeriaRouteSupport.BLOCKING_ANNOTATION ||
                name == ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION
        }

    private fun containingClass(function: KtNamedFunction): KtClassOrObject? =
        PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)

    private fun isGrpcServiceOverride(function: KtNamedFunction): Boolean {
        if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return false
        }
        val klass = containingClass(function) ?: return false
        return isGrpcHierarchy(klass)
    }

    private fun isGrpcHierarchy(root: PsiElement): Boolean {
        val visited = mutableSetOf<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            when (current) {
                is KtClassOrObject -> {
                    if (current.name?.endsWith("ImplBase") == true) {
                        return true
                    }
                    current.superTypeListEntries.forEach { entry ->
                        val referencedName = entry.typeAsUserType?.referencedName
                        if (referencedName?.endsWith("ImplBase") == true || referencedName == "BindableService") {
                            return true
                        }
                        resolveSuperType(entry, current)?.let { queue.add(it) }
                    }
                }
                is PsiClass -> {
                    if (ArmeriaMissingBlockingSupport.isGrpcServiceType(current)) {
                        return true
                    }
                    current.supers.forEach { queue.add(it) }
                }
                is PsiMethod -> current.containingClass?.let { queue.add(it) }
                is KtConstructor<*> -> queue.add(current.getContainingClassOrObject())
            }
        }
        return false
    }

    private fun resolveSuperType(
        entry: KtSuperTypeListEntry,
        klass: KtClassOrObject,
    ): PsiElement? {
        entry.typeReference
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() }
            ?.let { resolved -> asHierarchyType(resolved)?.let { return it } }
        val shortName = entry.typeAsUserType?.referencedName ?: return null
        val file = klass.containingKtFile
        val facade = JavaPsiFacade.getInstance(file.project)
        file.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == shortName || it.endsWith(".$shortName") }
            ?.let { imported ->
                facade.findClass(imported, file.resolveScope)?.let { return it }
            }
        val pkg = file.packageFqName.asString()
        val fqn = if (pkg.isEmpty()) shortName else "$pkg.$shortName"
        return facade.findClass(fqn, file.resolveScope)
    }

    private fun asHierarchyType(element: PsiElement): PsiElement? =
        when (element) {
            is PsiClass, is KtClassOrObject -> element
            is PsiMethod -> element.containingClass
            is KtConstructor<*> -> element.getContainingClassOrObject()
            else -> null
        }

    private fun isOnInspectedFunctionPath(
        function: KtNamedFunction,
        element: PsiElement,
    ): Boolean {
        var current: PsiElement? = element.parent
        while (current != null && current != function) {
            when (current) {
                is KtLambdaExpression, is KtClassOrObject, is KtNamedFunction -> return false
            }
            current = current.parent
        }
        return current == function
    }

    private fun resolvePsiMethod(call: KtCallExpression): PsiMethod? {
        val references =
            call.calleeExpression
                ?.references
                ?.toList()
                .orEmpty()
        for (reference in references) {
            val resolved = reference.resolve()
            if (resolved is PsiMethod) {
                return resolved
            }
        }
        return null
    }

    private fun highlight(call: KtCallExpression): PsiElement {
        val callee = call.calleeExpression
        if (callee is KtDotQualifiedExpression) {
            return callee.selectorExpression ?: callee
        }
        return callee ?: call
    }
}
