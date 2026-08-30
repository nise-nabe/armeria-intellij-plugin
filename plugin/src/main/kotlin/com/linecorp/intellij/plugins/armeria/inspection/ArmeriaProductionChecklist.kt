package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport

internal object ArmeriaProductionChecklist {
    const val CLIENT_FACTORY_CLASS = "com.linecorp.armeria.client.ClientFactory"
    const val FLAGS_PROVIDER_CLASS = "com.linecorp.armeria.common.FlagsProvider"
    const val FLAGS_PROVIDER_SPI_FILE = "com.linecorp.armeria.common.FlagsProvider"

    val SERVER_LIMIT_METHODS =
        listOf(
            "maxNumConnections",
            "requestTimeout",
            "maxRequestLength",
        )

    val DYNAMIC_ENDPOINT_GROUP_SIMPLE_NAMES =
        setOf(
            "DnsAddressEndpointGroup",
            "DnsServiceEndpointGroup",
            "DnsTextEndpointGroup",
            "ZooKeeperEndpointGroup",
            "EurekaEndpointGroup",
            "ConsulEndpointGroup",
        )

    val RESILIENCE_DECORATOR_SIMPLE_NAMES =
        setOf(
            "RetryingClient",
            "CircuitBreakerClient",
        )

    val SCOPE_FUNCTION_NAMES = setOf("apply", "also", "run", "let", "with")

    fun isTestSource(file: PsiFile?): Boolean {
        val virtualFile = file?.virtualFile ?: return false
        return ProjectRootManager.getInstance(file.project).fileIndex.isInTestSourceContent(virtualFile)
    }

    fun missingServerLimits(present: Set<String>): List<String> =
        SERVER_LIMIT_METHODS.filter { method ->
            when (method) {
                "requestTimeout" -> "requestTimeout" !in present && "requestTimeoutMillis" !in present
                else -> method !in present
            }
        }

    fun formatMissingLimits(missing: List<String>): String = missing.joinToString(", ")

    fun isResilienceDecorator(simpleName: String): Boolean = simpleName in RESILIENCE_DECORATOR_SIMPLE_NAMES

    fun isDynamicEndpointGroup(simpleName: String): Boolean = simpleName in DYNAMIC_ENDPOINT_GROUP_SIMPLE_NAMES

    fun isDynamicEndpointGroupTypeName(simpleName: String): Boolean {
        if (isDynamicEndpointGroup(simpleName)) {
            return true
        }
        return simpleName.endsWith("Builder") &&
            isDynamicEndpointGroup(simpleName.removeSuffix("Builder"))
    }

    fun isClientFactoryClass(qualifiedName: String?): Boolean =
        qualifiedName == CLIENT_FACTORY_CLASS || qualifiedName?.endsWith(".ClientFactory") == true

    fun isArmeriaClientClass(qualifiedName: String?): Boolean {
        if (qualifiedName == null) {
            return false
        }
        if (ArmeriaClientSupport.protocolForClass(qualifiedName) != null) {
            return true
        }
        return !qualifiedName.contains('.') &&
            ArmeriaClientSupport.protocolForSimpleName(qualifiedName) != null
    }

    fun isFlagsProviderRegistered(psiClass: PsiClass): Boolean {
        val fqcn = psiClass.qualifiedName ?: return true
        return isFlagsProviderRegistered(psiClass, fqcn)
    }

    fun isFlagsProviderRegistered(
        element: PsiElement,
        fqcn: String,
    ): Boolean {
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        return try {
            FilenameIndex.getVirtualFilesByName(FLAGS_PROVIDER_SPI_FILE, scope).any { file ->
                val text = loadSpiText(file) ?: return@any false
                spiListsClass(text, fqcn)
            }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            true
        }
    }

    private fun loadSpiText(file: VirtualFile): String? =
        try {
            LoadTextUtil.loadText(file).toString()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private fun spiListsClass(
        text: String,
        fqcn: String,
    ): Boolean =
        text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith('#') && trimmed == fqcn
        }
}
