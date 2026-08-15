package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
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
        return klass.superTypeListEntries.any { entry ->
            val referencedName = entry.typeAsUserType?.referencedName ?: entry.text
            if (referencedName.endsWith("ImplBase") || referencedName.contains("BindableService")) {
                return@any true
            }
            val superClass = entry.typeReference?.references?.firstNotNullOfOrNull { it.resolve() }
            superClass is KtClass &&
                (
                    superClass.name?.endsWith("ImplBase") == true ||
                        superClass.fqName?.asString() == "io.grpc.BindableService"
                )
        }
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
