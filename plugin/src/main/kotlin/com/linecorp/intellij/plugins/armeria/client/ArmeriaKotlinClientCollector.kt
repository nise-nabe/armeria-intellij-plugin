package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.idea.KotlinFileType
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

internal object ArmeriaKotlinClientCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(file)) {
                continue
            }
            file.forEachDescendant { element ->
                val call = element as? KtCallExpression ?: return@forEachDescendant
                collectClientFromCall(call, endpoints, seenEndpoints)
            }
        }
    }

    private fun collectClientFromCall(
        call: KtCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        if (isNestedInsideClientFactoryArgument(call)) {
            return
        }
        val methodName = resolveCallName(call) ?: return
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return
        }
        val resolvedClass = resolveContainingClass(call) ?: return
        val protocol = ArmeriaClientSupport.protocolForClass(resolvedClass) ?: return
        val metadata = extractClientMetadata(call, methodName, protocol) ?: return
        val target = resolveTargetName(call) ?: resolvedClass.substringAfterLast('.')
        ArmeriaClientCollector.addEndpoint(
            element = call,
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
        call: KtCallExpression,
        methodName: String,
        protocol: ClientProtocol,
    ): ClientMetadata? {
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        val decorators = ArmeriaKotlinClientDecoratorSupport.collectKotlinClientDecorators(call)
        return when (methodName) {
            "newClient", "of" -> {
                when (protocol) {
                    ClientProtocol.RETROFIT -> extractRetrofitMetadata(arguments, decorators)
                    else -> {
                        val uri = extractKotlinString(arguments.firstOrNull()) ?: return null
                        ClientMetadata(uri = uri, decorators = decorators)
                    }
                }
            }
            "builder" -> {
                when {
                    arguments.isEmpty() -> null
                    arguments.size >= 2 && isEndpointGroupArgument(arguments[1]) -> {
                        val endpointGroup = ArmeriaClientEndpointGroupSupport.labelKotlinEndpointGroup(arguments[1])
                            ?: return null
                        ClientMetadata(
                            uri = ArmeriaClientEndpointGroupSupport.extractKotlinEndpointGroupUri(arguments[1])
                                ?: endpointGroup,
                            decorators = decorators,
                            endpointGroup = endpointGroup,
                        )
                    }
                    protocol == ClientProtocol.RETROFIT -> extractRetrofitMetadata(arguments, decorators)
                    else -> {
                        val uri = extractKotlinString(arguments.firstOrNull()) ?: return null
                        ClientMetadata(uri = uri, decorators = decorators)
                    }
                }
            }
            else -> null
        }
    }

    private fun extractRetrofitMetadata(
        arguments: List<KtExpression>,
        decorators: List<String>,
    ): ClientMetadata? {
        val firstArg = arguments.firstOrNull() ?: return null
        val webClientInfo = extractWebClientTransport(firstArg)
        if (webClientInfo != null) {
            val (uri, innerDecorators) = webClientInfo
            return ClientMetadata(
                uri = uri,
                decorators = (decorators + innerDecorators).distinct(),
                transport = message("client.explorer.transport.webClient"),
            )
        }
        if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
            val endpointGroup = ArmeriaClientEndpointGroupSupport.labelKotlinEndpointGroup(arguments[1]) ?: return null
            return ClientMetadata(
                uri = ArmeriaClientEndpointGroupSupport.extractKotlinEndpointGroupUri(arguments[1]) ?: endpointGroup,
                decorators = decorators,
                endpointGroup = endpointGroup,
            )
        }
        val uri = extractKotlinString(firstArg) ?: return null
        return ClientMetadata(uri = uri, decorators = decorators)
    }

    private fun extractWebClientTransport(expression: KtExpression): Pair<String, List<String>>? {
        val call = expression as? KtCallExpression
        if (call != null) {
            val methodName = resolveCallName(call)
            val resolvedClass = resolveContainingClass(call)
            if (methodName == "build" &&
                resolvedClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true
            ) {
                val factoryCall = findWebClientFactoryInQualifierChain(call) ?: return null
                return extractWebClientTransport(factoryCall)
            }
            if (ArmeriaClientSupport.isWebClientClass(resolvedClass) && methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
                val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
                if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
                    val uri = ArmeriaClientEndpointGroupSupport.extractKotlinEndpointGroupUri(arguments[1]) ?: return null
                    return uri to ArmeriaKotlinClientDecoratorSupport.collectKotlinClientDecorators(call)
                }
                val uri = extractKotlinString(arguments.firstOrNull()) ?: return null
                return uri to ArmeriaKotlinClientDecoratorSupport.collectKotlinClientDecorators(call)
            }
        }
        if (expression is KtNameReferenceExpression) {
            val resolved = expression.references.firstOrNull()?.resolve()
            if (resolved is KtProperty) {
                return extractWebClientTransport(resolved.initializer ?: return null)
            }
            if (resolved is PsiVariable) {
                return extractWebClientTransport(resolved.initializer as? KtExpression ?: return null)
            }
        }
        return null
    }

    private fun isEndpointGroupArgument(expression: KtExpression): Boolean {
        return ArmeriaClientEndpointGroupSupport.labelKotlinEndpointGroup(expression) != null
    }

    private fun findWebClientFactoryInQualifierChain(call: KtCallExpression): KtCallExpression? {
        var current: KtExpression? = qualifierReceiver(call)
        while (current != null) {
            val factoryCall = callExpressionInChain(current)
            if (factoryCall != null) {
                val methodName = resolveCallName(factoryCall)
                val resolvedClass = resolveContainingClass(factoryCall)
                if (ArmeriaClientSupport.isWebClientClass(resolvedClass) &&
                    methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES
                ) {
                    return factoryCall
                }
            }
            current = qualifierReceiver(current)
        }
        return null
    }

    private fun callExpressionInChain(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun qualifierReceiver(expression: KtExpression): KtExpression? {
        return when (expression) {
            is KtCallExpression -> {
                when (val callee = expression.calleeExpression) {
                    is KtDotQualifiedExpression -> callee.receiverExpression
                    else -> (expression.parent as? KtDotQualifiedExpression)?.receiverExpression
                }
            }
            is KtDotQualifiedExpression -> expression.receiverExpression
            else -> null
        }
    }

    private fun isNestedInsideClientFactoryArgument(call: KtCallExpression): Boolean {
        var element: PsiElement? = call.parent
        while (element != null) {
            val outerCall = element as? KtCallExpression ?: run {
                element = element.parent
                continue
            }
            val methodName = resolveCallName(outerCall)
            if (methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
                ArmeriaClientSupport.protocolForClass(resolveContainingClass(outerCall)) != null &&
                isDescendantOfValueArgument(call, outerCall)
            ) {
                return true
            }
            element = element.parent
        }
        return false
    }

    private fun isDescendantOfValueArgument(call: KtCallExpression, outerCall: KtCallExpression): Boolean {
        return outerCall.valueArguments.mapNotNull { it.getArgumentExpression() }.any { argument ->
            PsiTreeUtil.isAncestor(argument, call, false)
        }
    }

    private fun resolveTargetName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val receiver = when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (call.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
        return receiver?.text
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun resolveContainingClass(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        for (reference in references) {
            val resolved = reference.resolve()
            val qualifiedName = when (resolved) {
                is PsiMethod -> resolved.containingClass?.qualifiedName
                is PsiClass -> resolved.qualifiedName
                else -> null
            }
            if (ArmeriaClientSupport.protocolForClass(qualifiedName) != null) {
                return qualifiedName
            }
        }
        val qualifierText = when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression.text
            else -> (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
        }.orEmpty()
        return protocolForClassBySimpleName(qualifierText, call.containingFile as? KtFile)
    }

    private fun protocolForClassBySimpleName(qualifierText: String, file: KtFile?): String? {
        if (qualifierText.isBlank()) {
            return null
        }
        if (qualifierText.startsWith("com.linecorp.armeria")) {
            return qualifierText.takeIf { ArmeriaClientSupport.protocolForClass(it) != null }
        }
        val simpleName = qualifierText.substringAfterLast('.')
        val importFqcn = file?.importList?.imports?.firstOrNull { import ->
            import.importedFqName?.shortName()?.asString() == simpleName
        }?.importedFqName?.asString()
        return importFqcn?.takeIf { ArmeriaClientSupport.protocolForClass(it) != null }
    }

    private fun extractKotlinString(expression: KtExpression?): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtDotQualifiedExpression -> extractKotlinStringFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinStringFromReference(unwrapped)
            else -> unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinStringFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinString(resolved.initializer)?.let { return it }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass = receiver.references.firstOrNull()?.resolve() as? PsiClass ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim('"').takeIf { it.isNotEmpty() }
    }

    private fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }
}
