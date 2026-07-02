package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal object ArmeriaKotlinRouteCollector {
    private const val SERVER_BUILDER_SUFFIX = "ServerBuilder"
    private val REGISTRATION_METHOD_NAMES = setOf("service", "serviceUnder", "annotatedService")

    fun collectServiceRegistrationsFallback(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaRouteCollector.referencesArmeriaContent(ktFile)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromFile(ktFile, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromFile(
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
            addKotlinServiceRegistration(call, methodName, routes, seenServiceRegistrations)
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
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        } ?: return false
        val receiver = dotQualified.receiverExpression
        val receiverText = receiver.text
        if (receiverText.contains("Server.builder()") ||
            receiverText.contains("serverBuilder") ||
            receiverText.contains(SERVER_BUILDER_SUFFIX)
        ) {
            return true
        }
        if (receiver is KtNameReferenceExpression) {
            when (val resolved = receiver.references.firstOrNull()?.resolve()) {
                is com.intellij.psi.PsiVariable -> {
                    if (resolved.type.canonicalText.contains(SERVER_BUILDER_SUFFIX)) {
                        return true
                    }
                }
                is KtProperty -> {
                    if (resolved.typeReference?.text?.contains(SERVER_BUILDER_SUFFIX) == true) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun addKotlinServiceRegistration(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val registrationKey = "${call.containingKtFile.virtualFile.path}:${call.textRange.startOffset}"
        val arguments = call.valueArguments.map { it.getArgumentExpression() }
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = when (methodName) {
            "annotatedService" -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
            else -> arguments.getOrNull(1)
        } ?: return
        val targetInfo = extractKotlinTarget(implementationExpression)
        ArmeriaRouteCollector.addServiceRegistrationRoute(
            element = call,
            registrationKey = registrationKey,
            methodName = methodName,
            path = path,
            target = targetInfo.first,
            targetUnresolved = targetInfo.second,
            implementationText = implementationExpression.text,
            argumentCount = arguments.size,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
        )
    }

    private fun extractRegistrationPath(methodName: String, arguments: List<KtExpression?>): String? {
        return when (methodName) {
            "service", "serviceUnder" -> extractKotlinString(arguments.getOrNull(0))
            "annotatedService" -> if (arguments.size > 1) extractKotlinString(arguments.getOrNull(0)) else "/"
            else -> null
        }
    }

    private fun extractKotlinString(expression: KtExpression?): String? {
        return when (expression) {
            is KtStringTemplateExpression -> {
                if (expression.entries.size == 1) {
                    expression.entries[0].text.trim('"')
                } else {
                    expression.text.trim('"')
                }
            }
            else -> expression?.text?.trim('"')
        }
    }

    private fun extractKotlinTarget(expression: KtExpression): Pair<String, Boolean> {
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression
            if (selector is KtCallExpression) {
                return extractKotlinTarget(selector)
            }
        }
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            val methodName = when (callee) {
                is KtDotQualifiedExpression -> callee.selectorExpression?.text
                else -> callee?.text
            }
            if (methodName == "build") {
                val receiver = dotQualifiedReceiver(callee, expression) ?: return expression.text to true
                return extractKotlinTarget(receiver)
            }
            if (methodName == "builder") {
                expression.valueArguments.firstOrNull()?.getArgumentExpression()?.let { serviceArg ->
                    return extractKotlinTarget(serviceArg)
                }
                val receiver = dotQualifiedReceiver(callee, expression)
                if (receiver is KtNameReferenceExpression) {
                    val resolved = receiver.references.firstOrNull()?.resolve()
                    if (resolved is com.intellij.psi.PsiClass) {
                        return (resolved.qualifiedName ?: receiver.text) to false
                    }
                }
            }
            val reference = callee as? KtNameReferenceExpression ?: callee?.let {
                if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
            }
            val resolved = reference?.references?.firstOrNull()?.resolve()
            val qualifiedName = when (resolved) {
                is com.intellij.psi.PsiClass -> resolved.qualifiedName
                is com.intellij.psi.PsiMethod -> resolved.containingClass?.qualifiedName
                else -> null
            }
            if (qualifiedName != null) {
                return qualifiedName to false
            }
            val simpleName = reference?.getReferencedName() ?: expression.text
            return simpleName to true
        }
        if (expression is KtNameReferenceExpression) {
            val resolved = expression.references.firstOrNull()?.resolve()
            when (resolved) {
                is com.intellij.psi.PsiClass -> return (resolved.qualifiedName ?: expression.text) to false
            }
            return expression.text to true
        }
        return expression.text to expression.text.contains("()")
    }

    private fun dotQualifiedReceiver(callee: KtExpression?, expression: KtCallExpression): KtExpression? {
        return when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (expression.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
    }
}
