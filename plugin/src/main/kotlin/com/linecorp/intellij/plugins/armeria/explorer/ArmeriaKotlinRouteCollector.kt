package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal object ArmeriaKotlinRouteCollector {
    private const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    private val REGISTRATION_METHOD_NAMES = setOf("service", "serviceUnder", "annotatedService")

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val seenMethods = mutableSetOf<PsiMethod>()
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val ktFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!referencesArmeria(ktFile)) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectAnnotatedRoutes(ktFile, routes, seenMethods)
            collectServiceRegistrations(ktFile, routes, seenServiceRegistrations)
        }
    }

    private fun referencesArmeria(file: KtFile): Boolean {
        val hasArmeriaImports = file.importList?.imports?.any { import ->
            import.importedFqName?.asString()?.startsWith(ARMERIA_PACKAGE_PREFIX) == true
        } ?: false
        if (hasArmeriaImports) {
            return true
        }
        return file.text.take(4096).contains(ARMERIA_PACKAGE_PREFIX)
    }

    private fun collectAnnotatedRoutes(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenMethods: MutableSet<PsiMethod>,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
            for (lightMethod in function.toLightMethods()) {
                if (!seenMethods.add(lightMethod)) {
                    continue
                }
                ArmeriaRouteCollector.addAnnotatedRouteFromMethod(lightMethod, routes)
            }
        }
    }

    private fun collectServiceRegistrations(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (call in file.collectDescendantsOfType<KtCallExpression>()) {
            val methodName = resolveCallName(call) ?: continue
            if (methodName !in REGISTRATION_METHOD_NAMES) {
                continue
            }
            if (!looksLikeArmeriaBuilderCall(call)) {
                continue
            }
            val registrationKey = "${file.virtualFile.path}:${call.textRange.startOffset}"
            if (!seenServiceRegistrations.add(registrationKey)) {
                continue
            }
            addKotlinServiceRegistration(call, methodName, routes)
        }
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun looksLikeArmeriaBuilderCall(call: KtCallExpression): Boolean {
        val receiverText = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression.text
            else -> null
        }
        if (receiverText != null && (receiverText.contains("Server.builder") || receiverText.contains("serverBuilder") || receiverText.contains("ServerBuilder"))) {
            return true
        }
        val javaCall = PsiTreeUtil.getParentOfType(call, com.intellij.psi.PsiMethodCallExpression::class.java)
        if (javaCall != null) {
            return true
        }
        return call.containingKtFile.text.contains("Server.builder") || call.containingKtFile.text.contains("serverBuilder")
    }

    private fun addKotlinServiceRegistration(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
    ) {
        val arguments = call.valueArguments.map { it.getArgumentExpression() }
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = when (methodName) {
            "annotatedService" -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
            else -> arguments.getOrNull(1)
        } ?: return
        val expressionText = implementationExpression.text
        val protocol = ArmeriaRouteTargetExtractor.detectProtocol(expressionText)
        val target = expressionText
        val routeMatch = when {
            protocol != RouteProtocol.HTTP -> RouteMatch.NON_HTTP
            methodName == "service" -> RouteMatch.SERVICE
            methodName == "serviceUnder" -> RouteMatch.SERVICE_UNDER
            else -> RouteMatch.ANNOTATED_SERVICE
        }
        val annotatedServiceHasPathPrefix = methodName == "annotatedService" && arguments.size > 1
        routes += ArmeriaRoute.create(
            element = call,
            protocol = protocol.presentableName(),
            httpMethod = "",
            path = ArmeriaRouteSupport.normalizePath(path),
            target = target,
            routeMatch = routeMatch,
            isDocService = protocol == RouteProtocol.DOC_SERVICE,
            annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
        )
    }

    private fun extractRegistrationPath(methodName: String, arguments: List<org.jetbrains.kotlin.psi.KtExpression?>): String? {
        return when (methodName) {
            "service", "serviceUnder" -> extractKotlinString(arguments.getOrNull(0))
            "annotatedService" -> if (arguments.size > 1) extractKotlinString(arguments.getOrNull(0)) else "/"
            else -> null
        }
    }

    private fun extractKotlinString(expression: org.jetbrains.kotlin.psi.KtExpression?): String? {
        return when (expression) {
            is KtStringTemplateExpression -> {
                if (expression.entries.size == 1) {
                    expression.entries[0].text
                } else {
                    expression.text.trim('"')
                }
            }
            else -> expression?.text?.trim('"')
        }
    }
}
