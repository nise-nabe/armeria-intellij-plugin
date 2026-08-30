package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaSpringBootConfiguratorBeanCollector {
    private val EXPLORER_TYPES =
        listOf(
            ArmeriaRouteSupport.DOC_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.HEALTH_CHECK_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.METRIC_COLLECTING_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.ARMERIA_CLIENT_CONFIGURATOR_CLASS,
        )

    private val INSPECTION_EXTRA_TYPES =
        setOf(
            ArmeriaRouteSupport.DOC_SERVICE_CLASS,
        )

    fun collect(project: Project): List<ArmeriaSpringBootConfigFile> {
        val beans =
            try {
                collectBeans(project, includeInspectionExtras = false)
            } catch (_: IndexNotReadyException) {
                emptyList()
            }
        if (beans.isEmpty()) {
            return emptyList()
        }
        return listOf(
            ArmeriaSpringBootConfigFile(
                fileName = message("springboot.config.beans.fileName"),
                filePath = message("springboot.config.beans.filePath"),
                entries = beans,
                synthetic = true,
            ),
        )
    }

    /**
     * Configurator (and related) FQNs present in the project.
     * Returns `null` when indexes are not ready so callers can skip bean-dependent warnings.
     */
    fun presentInspectionFqns(project: Project): Set<String>? {
        if (DumbService.isDumb(project)) {
            return null
        }
        return try {
            collectBeans(project, includeInspectionExtras = true)
                .mapNotNull { it.configuratorFqn }
                .toSet()
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun collectBeans(
        project: Project,
        includeInspectionExtras: Boolean,
    ): List<ArmeriaSpringBootConfigEntry> {
        val projectScope = GlobalSearchScope.projectScope(project)
        // `@Bean` and Armeria configurator types live in library jars (spring-context /
        // armeria-spring). Resolve them with allScope; search annotated methods and
        // inheritors only in project content so library examples are not listed.
        val classpathScope = GlobalSearchScope.allScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val seen = linkedMapOf<String, ArmeriaSpringBootConfigEntry>()
        val beanAnnotation = psiFacade.findClass(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION, classpathScope)
        if (beanAnnotation != null) {
            AnnotatedElementsSearch.searchPsiMethods(beanAnnotation, projectScope).forEach { method ->
                ProgressManager.checkCanceled()
                val configuratorFqn =
                    matchingConfiguratorFqn(method, psiFacade, classpathScope, includeInspectionExtras)
                        ?: return@forEach
                addBean(seen, configuratorFqn, method.name, navigationTarget(method), method)
            }
        }
        for (fqn in EXPLORER_TYPES) {
            val iface = psiFacade.findClass(fqn, classpathScope) ?: continue
            ClassInheritorsSearch.search(iface, projectScope, true).forEach { psiClass ->
                ProgressManager.checkCanceled()
                if (psiClass.isInterface || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return@forEach
                }
                val className = psiClass.name
                if (className.isNullOrBlank()) {
                    return@forEach
                }
                addBean(seen, fqn, className, navigationTarget(psiClass), psiClass)
            }
        }
        return seen.values.toList()
    }

    private fun matchingConfiguratorFqn(
        method: PsiMethod,
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
        includeInspectionExtras: Boolean,
    ): String? {
        val returnType = method.returnType ?: return null
        serverBuilderConsumerFqn(returnType)?.let { return it }
        val psiClass = (returnType as? PsiClassType)?.resolve()
        if (psiClass != null) {
            return matchingConfiguratorFqn(psiClass, psiFacade, scope, includeInspectionExtras)
        }
        val canonical = returnType.canonicalText
        return matchCanonical(canonical, includeInspectionExtras)
    }

    private fun matchingConfiguratorFqn(
        psiClass: PsiClass,
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
        includeInspectionExtras: Boolean,
    ): String? {
        val qualifiedName = psiClass.qualifiedName
        matchCanonical(qualifiedName, includeInspectionExtras)?.let { return it }
        val types = explorerAndInspectionTypes(includeInspectionExtras)
        for (fqn in types) {
            val iface = psiFacade.findClass(fqn, scope) ?: continue
            if (psiClass.isInheritor(iface, true)) {
                return fqn
            }
        }
        return null
    }

    private fun matchCanonical(
        canonical: String?,
        includeInspectionExtras: Boolean,
    ): String? {
        if (canonical == null) {
            return null
        }
        EXPLORER_TYPES.firstOrNull { it == canonical }?.let { return it }
        if (includeInspectionExtras) {
            INSPECTION_EXTRA_TYPES.firstOrNull { it == canonical }?.let { return it }
        }
        return null
    }

    private fun explorerAndInspectionTypes(includeInspectionExtras: Boolean): List<String> =
        if (includeInspectionExtras) {
            EXPLORER_TYPES + INSPECTION_EXTRA_TYPES
        } else {
            EXPLORER_TYPES
        }

    private fun serverBuilderConsumerFqn(returnType: PsiType): String? {
        val classType = returnType as? PsiClassType ?: return null
        val resolvedName = classType.resolve()?.qualifiedName ?: classType.canonicalText.substringBefore('<')
        if (resolvedName != ArmeriaRouteSupport.CONSUMER_CLASS) {
            return null
        }
        val typeArgs = classType.parameters
        if (typeArgs.size != 1) {
            return null
        }
        val argument = typeArgs[0]
        val argumentName =
            (argument as? PsiClassType)?.resolve()?.qualifiedName
                ?: argument.canonicalText.substringBefore('<')
        if (argumentName != ArmeriaRouteSupport.SERVER_BUILDER_CLASS) {
            return null
        }
        return ArmeriaRouteSupport.SERVER_BUILDER_CONSUMER_TYPE
    }

    private fun addBean(
        seen: MutableMap<String, ArmeriaSpringBootConfigEntry>,
        configuratorFqn: String,
        displayName: String,
        navigation: PsiElement,
        source: PsiElement,
    ) {
        val virtualFile = source.containingFile?.virtualFile
        val identity = (virtualFile?.path ?: "") + ":" + navigation.textOffset + ":" + configuratorFqn
        if (identity in seen) {
            return
        }
        val pointer = SmartPointerManager.getInstance(source.project).createSmartPsiElementPointer(navigation)
        seen[identity] =
            ArmeriaSpringBootConfigEntry(
                key = displayName,
                value = displayType(configuratorFqn),
                navigationPointer = pointer,
                configuratorFqn = configuratorFqn,
            )
    }

    private fun displayType(configuratorFqn: String): String =
        if (configuratorFqn == ArmeriaRouteSupport.SERVER_BUILDER_CONSUMER_TYPE) {
            "Consumer<ServerBuilder>"
        } else {
            configuratorFqn.substringAfterLast('.')
        }

    private fun navigationTarget(element: PsiElement): PsiElement =
        (element as? PsiNameIdentifierOwner)?.nameIdentifier
            ?: PsiTreeUtil.getDeepestFirst(element)
}
