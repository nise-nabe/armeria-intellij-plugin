package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

internal object ArmeriaKotlinExtendedRegistrationCollector {

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(ktFile)) {
                continue
            }
            collectFromFile(ktFile, routes, seenRegistrations)
        }
    }

    private fun collectFromFile(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        file.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            val methodName = resolveCallName(call) ?: return@forEachDescendant
            if (methodName !in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
                return@forEachDescendant
            }
            collectFromKotlinCall(call, methodName, routes, seenRegistrations)
        }
    }

    private fun collectFromKotlinCall(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val virtualFile = call.containingFile?.virtualFile ?: return
        val key = "${virtualFile.path}:${call.textRange.startOffset}"
        if (!seenRegistrations.add(key)) {
            return
        }
        val pathArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.FILE_SERVICE -> {
                val rawPath = extractKotlinString(pathArg) ?: "/"
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = normalizedPath,
                    target = call.valueArguments.getOrNull(1)?.getArgumentExpression()?.text ?: "file",
                    routeMatch = RouteMatch.FILE_SERVICE,
                    pathType = pathType,
                )
            }
            ServiceRegistrationMethod.HEALTH_CHECK_SERVICE -> {
                val path = pathArg?.let(::extractKotlinString)?.let(ArmeriaRouteSupport::normalizePath)
                    ?: "/internal/healthcheck"
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "GET",
                    path = path,
                    target = "HealthCheckService",
                    routeMatch = RouteMatch.HEALTH_CHECK,
                )
            }
            ServiceRegistrationMethod.VIRTUAL_HOST -> {
                val hostname = extractKotlinString(pathArg) ?: pathArg?.text ?: "*"
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = "/",
                    target = hostname,
                    routeMatch = RouteMatch.VIRTUAL_HOST,
                    virtualHostName = hostname,
                )
            }
            ServiceRegistrationMethod.ROUTE_DECORATOR -> {
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = "/**",
                    target = "routeDecorator",
                    routeMatch = RouteMatch.ROUTE_DECORATOR,
                )
            }
            else -> Unit
        }
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun extractKotlinString(expression: org.jetbrains.kotlin.psi.KtExpression?): String? {
        return when (expression) {
            is KtStringTemplateExpression -> expression.entries.joinToString("") { it.text }.trim('"')
            else -> expression?.text?.trim('"')
        }
    }
}
