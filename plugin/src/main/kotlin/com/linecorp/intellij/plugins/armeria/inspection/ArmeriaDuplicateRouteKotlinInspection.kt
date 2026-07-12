package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaDuplicateRouteKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.route.kotlin.display.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                super.visitClass(klass)
                val duplicateFunctions = linkedSetOf<KtNamedFunction>()
                val seen = mutableMapOf<Pair<String, String>, KtNamedFunction>()
                for (function in routeAnnotatedFunctions(klass)) {
                    val route = ArmeriaKotlinMethodRoute.from(function) ?: continue
                    for (path in route.paths) {
                        val key = route.httpMethod to path
                        val previous = seen.putIfAbsent(key, function)
                        if (previous != null) {
                            duplicateFunctions += previous
                            duplicateFunctions += function
                        }
                    }
                }
                for (function in duplicateFunctions) {
                    holder.registerProblem(
                        function.nameIdentifier ?: function,
                        message("inspection.duplicate.route.problem"),
                    )
                }
            }
        }
    }

    private fun routeAnnotatedFunctions(klass: KtClass): List<KtNamedFunction> {
        val functions = linkedSetOf<KtNamedFunction>()
        var current: KtClass? = klass
        while (current != null) {
            functions += current.declarations.filterIsInstance<KtNamedFunction>()
            current = superClass(current)
        }
        return functions.toList()
    }

    private fun superClass(klass: KtClass): KtClass? {
        val typeReference = klass.superTypeListEntries.firstOrNull()?.typeReference ?: return null
        (typeReference.references.firstOrNull()?.resolve() as? KtClass)?.let { return it }
        val superName = klass.superTypeListEntries.firstOrNull()?.typeAsUserType?.referencedName ?: return null
        klass.containingKtFile.declarations.filterIsInstance<KtClass>().firstOrNull { it.name == superName }?.let { return it }
        return klass.toLightClass()?.superClass?.originalElement as? KtClass
    }
}
