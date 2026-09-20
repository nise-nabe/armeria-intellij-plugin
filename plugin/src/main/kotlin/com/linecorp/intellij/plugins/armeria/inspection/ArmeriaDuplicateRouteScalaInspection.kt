package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class ArmeriaDuplicateRouteScalaInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.route.scala.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : ScalaElementVisitor() {
            override fun visitTypeDefinition(definition: ScTypeDefinition) {
                super.visitTypeDefinition(definition)
                val duplicateFunctions = linkedSetOf<PsiMethod>()
                val seen = mutableMapOf<Pair<String, String>, PsiMethod>()
                for (function in routeAnnotatedFunctions(definition)) {
                    val route =
                        ArmeriaScalaInspectionSupport.methodRoute(function) ?: continue
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
                        (function as? ScNamedElement)?.nameId() ?: function,
                        message("inspection.duplicate.route.problem"),
                    )
                }
            }
        }

    private fun routeAnnotatedFunctions(definition: ScTypeDefinition): List<PsiMethod> {
        val functions = linkedSetOf<PsiMethod>()
        val visited = mutableSetOf<PsiClass>()
        val queue = ArrayDeque<PsiClass>()
        queue.add(definition)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            // ScTypeDefinition#getMethods() wraps functions in light PsiMethod adapters, so
            // enumerate Scala members natively and fall back to Java PSI for superclasses.
            if (current is ScTemplateDefinition) {
                val functionsIterator = current.functions().iterator()
                while (functionsIterator.hasNext()) {
                    functions += functionsIterator.next()
                }
            } else {
                functions += current.methods.asList()
            }
            current.supers.forEach(queue::add)
        }
        return functions.toList()
    }
}
