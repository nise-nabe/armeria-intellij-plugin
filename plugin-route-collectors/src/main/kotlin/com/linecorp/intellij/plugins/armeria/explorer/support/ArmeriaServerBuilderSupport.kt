package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope

internal object ArmeriaServerBuilderSupport {
    fun isSpringBootArmeriaAvailable(
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
    ): Boolean {
        if (psiFacade.findClass(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION, scope) == null) {
            return false
        }
        return psiFacade.findClass(ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS, scope) != null ||
            psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) != null ||
            psiFacade.findClass(ArmeriaRouteSupport.ARMERIA_SERVER_CLASS, scope) != null
    }

    fun isArmeriaServerBeanReturnType(
        method: PsiMethod,
        scope: GlobalSearchScope,
    ): Boolean {
        val returnType = method.returnType ?: return false
        val psiClass = (returnType as? PsiClassType)?.resolve()
        if (psiClass != null) {
            return isArmeriaServerBeanReturnType(psiClass, JavaPsiFacade.getInstance(method.project), scope)
        }
        return isArmeriaServerBeanReturnType(returnType.canonicalText)
    }

    fun isArmeriaServerBeanReturnType(returnType: String): Boolean {
        if (returnType == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS || isServerBuilderType(returnType)) {
            return true
        }
        return returnType == ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS
    }

    fun isServerBuilderType(typeText: String): Boolean {
        val normalized = normalizeServerBuilderTypeText(typeText)
        return normalized == ArmeriaRouteSupport.SERVER_BUILDER_SIMPLE_NAME ||
            normalized == ArmeriaRouteSupport.SERVER_BUILDER_CLASS ||
            normalized.endsWith(".${ArmeriaRouteSupport.SERVER_BUILDER_SIMPLE_NAME}")
    }

    fun evaluateJavaStringConstant(variable: PsiVariable): String? {
        (variable as? PsiField)?.initializer?.let { initializer ->
            if (initializer is PsiLiteralExpression) {
                return initializer.value as? String
            }
        }
        return variable.computeConstantValue() as? String
    }

    fun extractJavaStringConstant(expression: PsiExpression?): String? =
        when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            is PsiReferenceExpression -> {
                (expression.resolve() as? PsiVariable)?.let(::evaluateJavaStringConstant)
            }
            else -> {
                val constantValue =
                    JavaPsiFacade
                        .getInstance(expression.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(expression) as? String
                constantValue ?: expression.text.takeIf { StringUtil.isNotEmpty(it) }?.trim('"')
            }
        }

    private fun isArmeriaServerBeanReturnType(
        psiClass: PsiClass,
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
    ): Boolean {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS ||
            qualifiedName == ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS ||
            isServerBuilderType(qualifiedName.orEmpty())
        ) {
            return true
        }
        val configuratorClass = psiFacade.findClass(ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS, scope)
        if (configuratorClass != null && psiClass.isInheritor(configuratorClass, true)) {
            return true
        }
        val serverClass = psiFacade.findClass(ArmeriaRouteSupport.ARMERIA_SERVER_CLASS, scope)
        if (serverClass != null && psiClass.isInheritor(serverClass, true)) {
            return true
        }
        val serverBuilderClass = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope)
        return serverBuilderClass != null && psiClass.isInheritor(serverBuilderClass, true)
    }

    private fun normalizeServerBuilderTypeText(typeText: String): String {
        var normalized = typeText.trim().replace(Regex("""/\*.*?\*/"""), "").trim()
        normalized = stripLeadingKotlinTypeAnnotations(normalized)
        if (normalized.endsWith('?')) {
            normalized = normalized.dropLast(1).trim()
        }
        val genericStart = normalized.indexOf('<')
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart).trim()
        }
        return normalized
    }

    private fun stripLeadingKotlinTypeAnnotations(typeText: String): String {
        var remaining = typeText.trimStart()
        while (remaining.startsWith('@')) {
            var index = 1
            var depth = 0
            while (index < remaining.length) {
                when (val char = remaining[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            index++
                            break
                        }
                    }
                    ' ', '\t', '\n' ->
                        if (depth == 0) {
                            break
                        }
                }
                index++
            }
            remaining = remaining.substring(index).trimStart()
        }
        return remaining
    }
}
