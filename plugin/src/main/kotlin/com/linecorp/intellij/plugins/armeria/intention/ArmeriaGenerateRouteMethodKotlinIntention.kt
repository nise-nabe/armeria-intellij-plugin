package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinMethodRoute
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.ImportPath

open class ArmeriaGenerateRouteMethodKotlinIntention : PsiElementBaseIntentionAction() {
    protected open val stubKind: ArmeriaRouteStubKind
        get() = ArmeriaRouteStubKind.GET

    override fun getText(): String =
        when (stubKind) {
            ArmeriaRouteStubKind.GET -> message("intention.generate.route.method")
            ArmeriaRouteStubKind.POST_JSON -> message("intention.generate.route.method.post.json")
        }

    override fun getFamilyName(): String = message("intention.generate.route.method.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(
        project: Project,
        editor: Editor,
        element: PsiElement,
    ): Boolean {
        val serviceClass = annotatedServiceClass(element) ?: return false
        return isMemberDeclarationContext(element, serviceClass)
    }

    override fun invoke(
        project: Project,
        editor: Editor,
        element: PsiElement,
    ) {
        val serviceClass = annotatedServiceClass(element) ?: return
        val httpMethod = ArmeriaRouteMethodStub.httpMethod(stubKind)
        val functions = serviceClass.declarations.filterIsInstance<KtNamedFunction>()
        val methodName =
            ArmeriaRouteMethodStub.suggestMethodName(
                usedMethodNames = functions.mapNotNullTo(linkedSetOf()) { it.name },
                usedPathsForHttpMethod = usedKotlinRoutePaths(serviceClass, httpMethod),
            )
        val path = "/$methodName"
        val suspend = shouldGenerateSuspend(project, serviceClass)
        val factory = KtPsiFactory(project)
        val function =
            factory.createFunction(
                ArmeriaRouteMethodStub.kotlinFunctionText(stubKind, methodName, path, suspend),
            )
        WriteCommandAction.runWriteCommandAction(
            project,
            getText(),
            null,
            {
                val added = serviceClass.addDeclaration(function) as KtNamedFunction
                val ktFile = serviceClass.containingKtFile
                for (fqName in ArmeriaRouteMethodStub.kotlinImports(stubKind)) {
                    insertKotlinImport(factory, ktFile, fqName)
                }
                added.nameIdentifier?.textRange?.let { range ->
                    editor.caretModel.moveToOffset(range.startOffset)
                }
            },
            serviceClass.containingFile,
        )
    }

    private fun annotatedServiceClass(element: PsiElement): KtClass? {
        val klass = element.getStrictParentOfType<KtClass>() ?: return null
        if (klass.isInterface() || klass.isEnum() || klass.isAnnotation()) {
            return null
        }
        if (klass.annotationEntries.any {
                ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
            }
        ) {
            return klass
        }
        val hasRoute =
            klass.declarations.filterIsInstance<KtNamedFunction>().any { ArmeriaKotlinMethodRoute.from(it) != null }
        return klass.takeIf { hasRoute }
    }

    private fun isMemberDeclarationContext(
        element: PsiElement,
        serviceClass: KtClass,
    ): Boolean {
        val body = serviceClass.body ?: return false
        if (!body.textRange.contains(element.textOffset)) {
            return false
        }
        if (PsiTreeUtil.getParentOfType(element, KtAnnotationEntry::class.java) != null) {
            return false
        }
        return PsiTreeUtil.getParentOfType(
            element,
            KtNamedFunction::class.java,
            KtProperty::class.java,
            KtClassInitializer::class.java,
        ) == null
    }

    private fun shouldGenerateSuspend(
        project: Project,
        serviceClass: KtClassOrObject,
    ): Boolean {
        if (hasSuspendFunction(serviceClass)) {
            return true
        }
        return JavaPsiFacade.getInstance(project).findClass(
            ARMERIA_KOTLIN_MARKER_CLASS,
            GlobalSearchScope.allScope(project),
        ) != null
    }

    private fun insertKotlinImport(
        factory: KtPsiFactory,
        ktFile: KtFile,
        fqName: String,
    ) {
        val path = FqName(fqName)
        if (ktFile.importDirectives.any { it.importedFqName == path }) {
            return
        }
        val directive = factory.createImportDirective(ImportPath.fromString(fqName))
        val importList = ktFile.importList
        if (importList != null) {
            importList.add(directive)
            return
        }
        val packageDirective = ktFile.packageDirective
        if (packageDirective != null) {
            ktFile.addAfter(directive, packageDirective)
        } else {
            ktFile.add(directive)
        }
    }

    private fun usedKotlinRoutePaths(
        klass: KtClassOrObject,
        httpMethod: String,
    ): Set<String> =
        klass.declarations
            .filterIsInstance<KtNamedFunction>()
            .mapNotNullTo(linkedSetOf()) { function ->
                val route = ArmeriaKotlinMethodRoute.from(function) ?: return@mapNotNullTo null
                if (route.httpMethod != httpMethod) {
                    return@mapNotNullTo null
                }
                route.rawPaths.firstOrNull()?.takeIf { it.isNotEmpty() }
            }

    private fun hasSuspendFunction(klass: KtClassOrObject): Boolean =
        klass.declarations.filterIsInstance<KtNamedFunction>().any { it.hasModifier(KtTokens.SUSPEND_KEYWORD) }

    companion object {
        const val ARMERIA_KOTLIN_MARKER_CLASS = "com.linecorp.armeria.internal.common.kotlin.ArmeriaKotlinUtil"
    }
}

class ArmeriaGeneratePostJsonRouteMethodKotlinIntention : ArmeriaGenerateRouteMethodKotlinIntention() {
    override val stubKind: ArmeriaRouteStubKind
        get() = ArmeriaRouteStubKind.POST_JSON
}
