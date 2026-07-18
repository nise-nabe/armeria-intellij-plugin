package com.linecorp.intellij.plugins.armeria.client

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaClientCollector {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun collect(project: Project): List<ArmeriaClientEndpoint> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            computeProjectEndpoints(project)
        }

    private fun computeProjectEndpoints(project: Project): CachedValueProvider.Result<List<ArmeriaClientEndpoint>> {
        val scope = GlobalSearchScope.projectScope(project)
        val endpoints = mutableListOf<ArmeriaClientEndpoint>()
        val seenEndpoints = mutableSetOf<String>()
        collectJava(project, scope, endpoints, seenEndpoints)
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinClientCollector.collect(project, scope, endpoints, seenEndpoints)
        }
        val sorted = endpoints.sortedWith(compareBy({ it.clientType }, { it.uri }, { it.target }))
        return CachedValueProvider.Result.create(
            sorted,
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    private fun collectJava(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!ArmeriaRouteCollector.referencesArmeriaJavaContent(file)) {
                continue
            }
            file.accept(
                object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                        collectClientFromMethodCall(expression, endpoints, seenEndpoints)
                        super.visitMethodCallExpression(expression)
                    }
                },
            )
        }
    }

    internal fun collectClientFromMethodCall(
        expression: PsiMethodCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        if (isNestedInsideClientFactoryArgument(expression)) {
            return
        }
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return
        }
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName ?: return
        val protocol = ArmeriaClientSupport.protocolForClass(resolvedClass) ?: return
        val metadata = extractClientMetadata(expression, methodName, protocol) ?: return
        val target =
            expression.methodExpression.qualifierExpression?.text
                ?: resolvedClass.substringAfterLast('.')
        addEndpoint(
            element = expression,
            protocol = protocol,
            target = target,
            uri = metadata.uri,
            endpoints = endpoints,
            seenEndpoints = seenEndpoints,
            decorators = metadata.decorators,
            endpointGroup = metadata.endpointGroup,
            transport = metadata.transport,
        )
    }

    private data class ClientMetadata(
        val uri: String,
        val decorators: List<String> = emptyList(),
        val endpointGroup: String? = null,
        val transport: String? = null,
    )

    private fun extractClientMetadata(
        expression: PsiMethodCallExpression,
        methodName: String,
        protocol: ClientProtocol,
    ): ClientMetadata? {
        val arguments = expression.argumentList.expressions
        val decorators = ArmeriaClientDecoratorSupport.collectJavaClientDecorators(expression)
        return when (methodName) {
            "newClient", "of" -> {
                when (protocol) {
                    ClientProtocol.RETROFIT -> extractRetrofitMetadata(arguments, decorators)
                    else -> {
                        val uri = extractString(arguments.firstOrNull()) ?: return null
                        ClientMetadata(uri = uri, decorators = decorators)
                    }
                }
            }
            "builder" -> {
                when {
                    arguments.isEmpty() -> null
                    arguments.size >= 2 && isEndpointGroupArgument(arguments[1]) -> {
                        val endpointGroup =
                            ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(arguments[1])
                                ?: return null
                        ClientMetadata(
                            uri =
                                ArmeriaClientEndpointGroupSupport.extractJavaEndpointGroupUri(arguments[1])
                                    ?: endpointGroup,
                            decorators = decorators,
                            endpointGroup = endpointGroup,
                        )
                    }
                    protocol == ClientProtocol.RETROFIT -> extractRetrofitMetadata(arguments, decorators)
                    else -> {
                        val uri = extractString(arguments.firstOrNull()) ?: return null
                        ClientMetadata(uri = uri, decorators = decorators)
                    }
                }
            }
            else -> null
        }
    }

    private fun extractRetrofitMetadata(
        arguments: Array<PsiExpression>,
        decorators: List<String>,
    ): ClientMetadata? {
        val firstArg = arguments.firstOrNull() ?: return null
        val webClientInfo = extractWebClientTransport(firstArg)
        if (webClientInfo != null) {
            return ClientMetadata(
                uri = webClientInfo.uri,
                decorators = (decorators + webClientInfo.decorators).distinct(),
                endpointGroup = webClientInfo.endpointGroup,
                transport = message("client.explorer.transport.webClient"),
            )
        }
        if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
            val endpointGroup = ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(arguments[1]) ?: return null
            return ClientMetadata(
                uri = ArmeriaClientEndpointGroupSupport.extractJavaEndpointGroupUri(arguments[1]) ?: endpointGroup,
                decorators = decorators,
                endpointGroup = endpointGroup,
            )
        }
        val uri = extractString(firstArg) ?: return null
        return ClientMetadata(uri = uri, decorators = decorators)
    }

    private data class WebClientTransportInfo(
        val uri: String,
        val decorators: List<String>,
        val endpointGroup: String? = null,
    )

    private fun extractWebClientTransport(expression: PsiExpression): WebClientTransportInfo? {
        val call = expression as? PsiMethodCallExpression
        if (call != null) {
            val methodName = call.methodExpression.referenceName
            val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
            if (ArmeriaClientSupport.isWebClientClass(resolvedClass) &&
                methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES
            ) {
                val arguments = call.argumentList.expressions
                val decorators = ArmeriaClientDecoratorSupport.collectJavaClientDecorators(call)
                if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
                    val endpointGroup =
                        ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(arguments[1])
                            ?: return null
                    val uri =
                        ArmeriaClientEndpointGroupSupport.extractJavaEndpointGroupUri(arguments[1])
                            ?: endpointGroup
                    return WebClientTransportInfo(uri = uri, decorators = decorators, endpointGroup = endpointGroup)
                }
                val uri = extractString(arguments.firstOrNull()) ?: return null
                return WebClientTransportInfo(uri = uri, decorators = decorators)
            }
            // Unwrap fluent WebClientBuilder chains passed to Retrofit, e.g.
            // WebClient.builder(uri).decorator(...).build() or without a trailing build().
            if (isWebClientBuilderChainCall(call, resolvedClass)) {
                val factoryCall = findWebClientFactoryInQualifierChain(call) ?: return null
                return extractWebClientTransport(factoryCall)
            }
        }
        val reference = expression as? PsiReferenceExpression
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved is PsiVariable) {
                return extractWebClientTransport(resolved.initializer ?: return null)
            }
        }
        return null
    }

    private fun isWebClientBuilderChainCall(
        call: PsiMethodCallExpression,
        resolvedClass: String?,
    ): Boolean {
        if (resolvedClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = call.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(qualifierText) ||
            qualifierText.contains("WebClient")
    }

    private fun findWebClientFactoryInQualifierChain(expression: PsiMethodCallExpression): PsiMethodCallExpression? {
        var current: PsiExpression? = expression.methodExpression.qualifierExpression
        while (current != null) {
            val call = current as? PsiMethodCallExpression ?: break
            val methodName = call.methodExpression.referenceName
            val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
            if (ArmeriaClientSupport.isWebClientClass(resolvedClass) && methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
                return call
            }
            current = call.methodExpression.qualifierExpression
        }
        return null
    }

    private fun isEndpointGroupArgument(expression: PsiExpression): Boolean =
        ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(expression) != null

    internal fun addEndpoint(
        element: PsiElement,
        protocol: ClientProtocol,
        target: String,
        uri: String,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
        decorators: List<String> = emptyList(),
        endpointGroup: String? = null,
        transport: String? = null,
    ) {
        val virtualFile = element.containingFile?.virtualFile ?: return
        val dedupeKey = "${virtualFile.path}:${element.textRange.startOffset}"
        if (!seenEndpoints.add(dedupeKey)) {
            return
        }
        endpoints +=
            ArmeriaClientEndpoint.create(
                element = element,
                clientType = protocol.presentableName(),
                target = target,
                uri = uri,
                decorators = decorators,
                endpointGroup = endpointGroup,
                transport = transport,
            )
    }

    internal fun extractString(expression: PsiExpression?): String? =
        when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> {
                val constantValue =
                    JavaPsiFacade
                        .getInstance(expression.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(expression) as? String
                constantValue ?: expression.text.takeIf { StringUtil.isNotEmpty(it) }
            }
        }

    private fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)

    private fun isNestedInsideClientFactoryArgument(expression: PsiMethodCallExpression): Boolean {
        var element: PsiElement? = expression.parent
        while (element != null) {
            val outerCall =
                element as? PsiMethodCallExpression ?: run {
                    element = element.parent
                    continue
                }
            val methodName = outerCall.methodExpression.referenceName
            if (methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
                ArmeriaClientSupport.protocolForClass(outerCall.resolveMethod()?.containingClass?.qualifiedName) != null &&
                isDescendantOfArgumentList(expression, outerCall.argumentList)
            ) {
                return true
            }
            element = element.parent
        }
        return false
    }

    private fun isDescendantOfArgumentList(
        expression: PsiElement,
        argumentList: PsiExpressionList,
    ): Boolean {
        var current: PsiElement? = expression
        while (current != null) {
            if (current.parent == argumentList) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
