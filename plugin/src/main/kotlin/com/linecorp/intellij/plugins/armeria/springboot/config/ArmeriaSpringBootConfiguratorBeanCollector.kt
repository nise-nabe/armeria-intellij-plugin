package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaSpringBootConfiguratorBeanCollector {
    private val CONFIGURATOR_TYPES =
        listOf(
            ArmeriaRouteSupport.DOC_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.HEALTH_CHECK_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.METRIC_COLLECTING_SERVICE_CONFIGURATOR_CLASS,
            ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS,
        )

    fun collect(project: Project): List<ArmeriaSpringBootConfigFile> {
        val beans =
            try {
                collectBeans(project)
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

    private fun collectBeans(project: Project): List<ArmeriaSpringBootConfigEntry> {
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
                val configuratorFqn = matchingConfiguratorFqn(method, psiFacade, classpathScope) ?: return@forEach
                addBean(seen, configuratorFqn, method.name, navigationTarget(method), method)
            }
        }
        for (fqn in CONFIGURATOR_TYPES) {
            val iface = psiFacade.findClass(fqn, classpathScope) ?: continue
            ClassInheritorsSearch.search(iface, projectScope, true).forEach { psiClass ->
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
    ): String? {
        val returnType = method.returnType ?: return null
        val psiClass = (returnType as? PsiClassType)?.resolve()
        if (psiClass != null) {
            return matchingConfiguratorFqn(psiClass, psiFacade, scope)
        }
        val canonical = returnType.canonicalText
        return CONFIGURATOR_TYPES.firstOrNull { it == canonical }
    }

    private fun matchingConfiguratorFqn(
        psiClass: PsiClass,
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
    ): String? {
        val qualifiedName = psiClass.qualifiedName
        CONFIGURATOR_TYPES.firstOrNull { it == qualifiedName }?.let { return it }
        for (fqn in CONFIGURATOR_TYPES) {
            val iface = psiFacade.findClass(fqn, scope) ?: continue
            if (psiClass.isInheritor(iface, true)) {
                return fqn
            }
        }
        return null
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
                value = configuratorFqn.substringAfterLast('.'),
                navigationPointer = pointer,
                configuratorFqn = configuratorFqn,
            )
    }

    private fun navigationTarget(element: PsiElement): PsiElement =
        (element as? PsiNameIdentifierOwner)?.nameIdentifier
            ?: PsiTreeUtil.getDeepestFirst(element)
}
