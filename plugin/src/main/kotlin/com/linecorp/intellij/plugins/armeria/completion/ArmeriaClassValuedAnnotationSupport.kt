package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaClassValuedAnnotationSupport {
    const val EXCEPTION_HANDLER_FUNCTION = "com.linecorp.armeria.server.annotation.ExceptionHandlerFunction"
    const val REQUEST_CONVERTER_FUNCTION = "com.linecorp.armeria.server.annotation.RequestConverterFunction"
    const val RESPONSE_CONVERTER_FUNCTION = "com.linecorp.armeria.server.annotation.ResponseConverterFunction"
    const val DECORATING_HTTP_SERVICE_FUNCTION = "com.linecorp.armeria.server.DecoratingHttpServiceFunction"

    private val EXPECTED_INTERFACES =
        mapOf(
            ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION to
                (EXCEPTION_HANDLER_FUNCTION to "completion.exception.handler.type"),
            ArmeriaRouteSupport.REQUEST_CONVERTER_ANNOTATION to
                (REQUEST_CONVERTER_FUNCTION to "completion.request.converter.type"),
            ArmeriaRouteSupport.RESPONSE_CONVERTER_ANNOTATION to
                (RESPONSE_CONVERTER_FUNCTION to "completion.response.converter.type"),
            ArmeriaRouteSupport.DECORATOR_ANNOTATION to
                (DECORATING_HTTP_SERVICE_FUNCTION to "completion.decorator.type"),
        )

    fun expectedInterfaceFqn(annotationFqn: String?): String? = EXPECTED_INTERFACES[annotationFqn]?.first

    private fun typeTextKey(annotationFqn: String?): String? = EXPECTED_INTERFACES[annotationFqn]?.second

    fun lookupElements(
        contextElement: PsiElement,
        annotationFqn: String?,
        kotlinClassLiteral: Boolean,
    ): List<LookupElement> {
        val interfaceFqn = expectedInterfaceFqn(annotationFqn) ?: return emptyList()
        val typeKey = typeTextKey(annotationFqn) ?: return emptyList()
        val typeText = message(typeKey)
        val insertHandler = if (kotlinClassLiteral) KotlinClassLiteralInsertHandler else JavaClassLiteralInsertHandler
        return implementingClasses(contextElement, interfaceFqn).mapNotNull { psiClass ->
            val name = psiClass.name ?: return@mapNotNull null
            LookupElementBuilder
                .create(name)
                .withPsiElement(psiClass)
                .withTypeText(typeText)
                .withInsertHandler(insertHandler)
        }
    }

    fun implementingClasses(
        contextElement: PsiElement,
        interfaceFqn: String,
    ): List<PsiClass> {
        val project = contextElement.project
        val scope = GlobalSearchScope.allScope(project)
        val base = JavaPsiFacade.getInstance(project).findClass(interfaceFqn, scope) ?: return emptyList()
        val found = linkedMapOf<String, PsiClass>()

        fun add(candidate: PsiClass) {
            if (candidate.isInterface || candidate.isAnnotationType || candidate.isEnum) {
                return
            }
            if (candidate.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return
            }
            val key = candidate.qualifiedName ?: candidate.name ?: return
            found.putIfAbsent(key, candidate)
        }
        val file = contextElement.containingFile
        if (file is PsiClassOwner) {
            file.classes.forEach { candidate ->
                if (candidate.isInheritor(base, true)) {
                    add(candidate)
                }
            }
        }
        PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java).forEach { candidate ->
            if (candidate.isInheritor(base, true)) {
                add(candidate)
            }
        }
        try {
            ClassInheritorsSearch.search(base, GlobalSearchScope.projectScope(project), true).forEach { candidate ->
                ProgressManager.checkCanceled()
                add(candidate)
            }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            // Same-file implementors are still offered.
        }
        return found.values.toList()
    }
}

private object JavaClassLiteralInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement,
    ) {
        appendClassLiteralSuffix(context, ".class")
    }
}

private object KotlinClassLiteralInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement,
    ) {
        appendClassLiteralSuffix(context, "::class")
    }
}

private fun appendClassLiteralSuffix(
    context: InsertionContext,
    suffix: String,
) {
    val document = context.document
    val offset = context.tailOffset
    if (offset > document.textLength) {
        return
    }
    val remaining = document.immutableCharSequence.subSequence(offset, document.textLength).toString()
    if (remaining.startsWith(suffix) || remaining.startsWith(".class") || remaining.startsWith("::class")) {
        return
    }
    document.insertString(offset, suffix)
}

internal fun javaClassValuedAnnotation(start: PsiElement): PsiAnnotation? {
    if (PsiTreeUtil.getParentOfType(start, PsiLiteralExpression::class.java, false) != null) {
        return null
    }
    val annotation = PsiTreeUtil.getParentOfType(start, PsiAnnotation::class.java) ?: return null
    if (ArmeriaClassValuedAnnotationSupport.expectedInterfaceFqn(annotation.qualifiedName) == null) {
        return null
    }
    return annotation
}
