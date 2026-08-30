package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

internal object ArmeriaKotlinInspectionCallChains {
    fun callName(call: KtCallExpression): String? = ArmeriaKotlinExpressionSupport.resolveCallName(call)

    fun asCall(expression: KtExpression): KtCallExpression? =
        when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression) {
            is KtCallExpression -> unwrapped
            is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtCallExpression
            else -> null
        }

    fun chainReceiver(expression: KtExpression): KtExpression? {
        val receiver =
            when (expression) {
                is KtDotQualifiedExpression -> expression.receiverExpression
                is KtCallExpression -> {
                    val parent = expression.parent
                    if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
                        parent.receiverExpression
                    } else {
                        when (val callee = expression.calleeExpression) {
                            is KtDotQualifiedExpression -> callee.receiverExpression
                            else -> null
                        }
                    }
                }
                else -> null
            } ?: return null
        return ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver
    }

    fun nextChainedCall(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == call) {
            return parent.selectorExpression as? KtCallExpression
        }
        if (parent is KtDotQualifiedExpression) {
            val grandParent = parent.parent as? KtDotQualifiedExpression
            if (grandParent != null && grandParent.receiverExpression == parent) {
                return grandParent.selectorExpression as? KtCallExpression
            }
        }
        return null
    }

    fun forwardChainCalls(call: KtCallExpression): Sequence<KtCallExpression> =
        sequence {
            var cursor = call
            while (true) {
                val next = nextChainedCall(cursor) ?: break
                yield(next)
                cursor = next
            }
        }

    fun assignedProperty(call: KtCallExpression): KtProperty? {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: return null
            if (parent is KtProperty) {
                return parent
            }
            if (parent is KtDotQualifiedExpression || parent is KtCallExpression) {
                current = parent
                continue
            }
            return null
        }
    }

    fun callsOnProperty(
        property: KtProperty,
        scope: PsiElement,
    ): Sequence<KtCallExpression> =
        PsiTreeUtil.findChildrenOfType(scope, KtCallExpression::class.java).asSequence().filter { call ->
            qualifierResolvesTo(call, property)
        }

    fun qualifierResolvesTo(
        call: KtCallExpression,
        property: KtProperty,
    ): Boolean {
        var current: KtExpression? = chainReceiver(call)
        while (current != null) {
            when (current) {
                is KtNameReferenceExpression -> return current.references.any { it.resolve() == property }
                is KtCallExpression -> current = chainReceiver(current)
                is KtDotQualifiedExpression ->
                    current = current.selectorExpression as? KtCallExpression ?: current.receiverExpression
                else -> return false
            }
        }
        return false
    }

    fun containingCallable(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(
            element,
            KtNamedFunction::class.java,
            KtClassInitializer::class.java,
            PsiMethod::class.java,
        )

    fun qualifierSimpleName(call: KtCallExpression): String? {
        val receiver = chainReceiver(call) ?: return null
        return when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver) {
            is KtNameReferenceExpression -> unwrapped.getReferencedName()
            is KtCallExpression -> callName(unwrapped)
            is KtDotQualifiedExpression ->
                (unwrapped.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
                    ?: unwrapped.selectorExpression?.text?.substringBefore('(')
            else -> unwrapped.text.substringAfterLast('.').substringBefore('(')
        }
    }

    fun resolvedContainingClass(call: KtCallExpression): String? {
        val references =
            call.calleeExpression
                ?.references
                ?.toList()
                .orEmpty()
        for (reference in references) {
            val resolved = reference.resolve()
            val containingClass = (resolved as? PsiMethod)?.containingClass?.qualifiedName
            if (containingClass != null) {
                return containingClass
            }
        }
        return null
    }

    fun isCallOnClass(
        call: KtCallExpression,
        methodName: String,
        fqcn: String,
        simpleName: String,
    ): Boolean {
        if (callName(call) != methodName) {
            return false
        }
        val resolved = resolvedContainingClass(call)
        if (resolved == fqcn) {
            return true
        }
        return qualifierSimpleName(call) == simpleName
    }

    fun decoratorClassSimpleName(expression: KtExpression): String {
        var current: KtExpression =
            ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        while (true) {
            when (current) {
                is KtCallExpression -> {
                    val receiver = chainReceiver(current)
                    if (receiver != null) {
                        current = receiver
                    } else {
                        return callName(current).orEmpty()
                    }
                }
                is KtDotQualifiedExpression -> {
                    when (val selector = current.selectorExpression) {
                        is KtCallExpression -> current = selector
                        is KtNameReferenceExpression -> return selector.getReferencedName()
                        else -> current = current.receiverExpression
                    }
                }
                is KtNameReferenceExpression -> return current.getReferencedName()
                else -> return current.text.substringAfterLast('.').substringBefore('(')
            }
        }
    }

    fun lambdaBodyCalls(call: KtCallExpression): List<KtCallExpression> {
        val lambda =
            call.lambdaArguments.firstOrNull()?.getLambdaExpression()
                ?: call.valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
                ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(lambda, KtCallExpression::class.java).toList()
    }

    fun highlightCallName(call: KtCallExpression): PsiElement {
        val callee = call.calleeExpression
        val selector = (callee as? KtDotQualifiedExpression)?.selectorExpression
        return selector ?: callee ?: call
    }

    fun superTypeSimpleNames(declaration: KtClassOrObject): List<String> =
        declaration.superTypeListEntries.mapNotNull { entry ->
            entry.typeAsUserType?.referencedName ?: entry.typeReference?.text?.substringAfterLast('.')
        }

    fun toPsiClass(declaration: KtClassOrObject): PsiClass? = declaration.toLightClass()
}
