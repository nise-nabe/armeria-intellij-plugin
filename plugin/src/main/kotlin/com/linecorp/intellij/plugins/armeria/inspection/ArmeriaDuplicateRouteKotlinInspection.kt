package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
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
                for (function in klass.declarations.filterIsInstance<KtNamedFunction>()) {
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
}
