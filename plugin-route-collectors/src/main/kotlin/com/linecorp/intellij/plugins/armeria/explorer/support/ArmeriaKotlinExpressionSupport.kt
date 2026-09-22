package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenExpression

object ArmeriaKotlinExpressionSupport {
    fun containingKotlinExpressionScope(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: break
            if (parent is KtBlockExpression || parent is KtLambdaExpression) {
                return parent
            }
            current = parent
        }
        return call
    }

    fun containingKotlinStatementExpression(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: break
            if (parent is KtBlockExpression || parent is KtLambdaExpression) {
                return current
            }
            current = parent
        }
        return call
    }

    fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    fun findArgumentExpression(
        arguments: List<KtValueArgument>,
        parameterName: String,
        positionalIndex: Int,
    ): KtExpression? {
        arguments
            .firstOrNull { argument ->
                argument.getArgumentName()?.asName?.identifier == parameterName
            }?.getArgumentExpression()
            ?.let { return it }
        return arguments.getOrNull(positionalIndex)?.getArgumentExpression()
    }

    /**
     * String literal or resolvable compile-time constant.
     * Unresolved names and non-string initializers return null — never PSI `.text`.
     */
    fun extractKotlinStringConstant(
        expression: KtExpression?,
        visitedProperties: MutableSet<KtProperty> = mutableSetOf(),
    ): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> kotlinStringTemplateWithoutInterpolation(unwrapped)
            is KtBinaryExpression -> {
                if (unwrapped.operationToken != KtTokens.PLUS) {
                    return null
                }
                val left = extractKotlinStringConstant(unwrapped.left, visitedProperties)
                val right = extractKotlinStringConstant(unwrapped.right, visitedProperties)
                if (left != null && right != null) {
                    left + right
                } else {
                    null
                }
            }
            is KtDotQualifiedExpression -> extractKotlinStringConstantFromReference(unwrapped, visitedProperties)
            is KtNameReferenceExpression -> extractKotlinStringConstantFromReference(unwrapped, visitedProperties)
            else -> null
        }
    }

    /**
     * True when [expression] denotes a `String` value — a string template, a
     * compile-time constant, or a reference to a `String`-typed declaration. Used
     * to decide whether an unresolved argument occupies a path parameter slot.
     */
    fun isStringValuedExpression(expression: KtExpression?): Boolean {
        val unwrapped = unwrapKotlinExpression(expression) ?: return false
        return when (unwrapped) {
            is KtStringTemplateExpression -> true
            is KtNameReferenceExpression, is KtDotQualifiedExpression -> isStringReferenceTarget(unwrapped)
            is KtBinaryExpression ->
                unwrapped.operationToken == KtTokens.PLUS &&
                    (isStringValuedExpression(unwrapped.left) || isStringValuedExpression(unwrapped.right))
            is KtBinaryExpressionWithTypeRHS -> unwrapped.right?.text == "String"
            is KtIfExpression ->
                isStringValuedExpression(unwrapped.then) && isStringValuedExpression(unwrapped.`else`)
            is KtWhenExpression ->
                unwrapped.entries.isNotEmpty() &&
                    unwrapped.entries.all { isStringValuedExpression(it.expression) }
            is KtCallExpression -> isStringReturningCall(unwrapped)
            else -> false
        }
    }

    private fun isStringReferenceTarget(expression: KtExpression): Boolean =
        when (val resolved = resolveStringReferenceTarget(expression)) {
            is KtProperty ->
                resolved.typeReference?.text == "String" ||
                    unwrapKotlinExpression(resolved.initializer) is KtStringTemplateExpression
            is KtParameter -> resolved.typeReference?.text == "String"
            is PsiVariable -> isJavaStringType(resolved.type)
            else -> false
        }

    private fun isStringReturningCall(call: KtCallExpression): Boolean =
        when (
            val resolved =
                call.calleeExpression
                    ?.references
                    ?.mapNotNull { it.resolve() }
                    ?.firstOrNull { it is KtNamedFunction || it is PsiMethod }
        ) {
            is KtNamedFunction ->
                resolved.typeReference?.text == "String" ||
                    (
                        resolved.typeReference == null &&
                            unwrapKotlinExpression(resolved.bodyExpression) is KtStringTemplateExpression
                    )
            is PsiMethod -> isJavaStringType(resolved.returnType)
            else -> false
        }

    private fun isJavaStringType(type: PsiType?): Boolean =
        type?.canonicalText == "java.lang.String" ||
            // Light-PSI fixtures render java.lang.String as "String".
            type?.canonicalText == "String"

    private fun kotlinStringTemplateWithoutInterpolation(template: KtStringTemplateExpression): String? {
        if (template.hasInterpolation()) {
            return null
        }
        return buildString {
            for (entry in template.entries) {
                when (entry) {
                    is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                    else -> append(entry.text)
                }
            }
        }
    }

    private fun resolveStringReferenceTarget(expression: KtExpression): PsiElement? {
        val reference =
            (expression as? KtDotQualifiedExpression)?.selectorExpression ?: expression
        return reference.references
            .mapNotNull { it.resolve() }
            .firstOrNull { it is KtProperty || it is KtParameter || it is PsiVariable }
    }

    private fun extractKotlinStringConstantFromReference(
        expression: KtExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): String? {
        // Resolve the selector directly — `Paths.API` resolves the property even
        // when resolving the whole qualified expression fails.
        val resolved = resolveStringReferenceTarget(expression)
        when (resolved) {
            is KtProperty -> {
                if (!visitedProperties.add(resolved)) {
                    return null
                }
                extractKotlinStringConstant(resolved.initializer, visitedProperties)?.let { return it }
            }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containing = receiver.references.firstNotNullOfOrNull { it.resolve() }
            val containingClass =
                containing as? com.intellij.psi.PsiClass
                    ?: (containing as? KtClassOrObject)?.toLightClass()
                    ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return null
    }

    fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current =
                when (current) {
                    is KtParenthesizedExpression -> current.expression ?: return null
                    is KtBinaryExpressionWithTypeRHS -> current.left
                    is KtUnaryExpression ->
                        if (current.operationToken == KtTokens.EXCLEXCL) {
                            current.baseExpression ?: return current
                        } else {
                            return current
                        }
                    else -> return current
                }
        }
    }
}
