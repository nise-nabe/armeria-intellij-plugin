package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaSpringBootSettingsInspectionSupport {
    fun inspect(
        file: PsiFile,
        holder: ProblemsHolder,
        locator: SettingsHighlightLocator,
    ) {
        if (!ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(file.name)) {
            return
        }
        val entries =
            ArmeriaSpringBootConfigParser
                .parseFile(file.name, file.text)
                .associate { it.key to it.value }
        if (entries.isEmpty()) {
            return
        }
        val beans = ArmeriaSpringBootConfiguratorBeanCollector.presentInspectionFqns(file.project)
        for (finding in ArmeriaSpringBootSettingsConflict.findings(entries, beans)) {
            val element = locator.locate(file, finding) ?: continue
            holder.registerProblem(
                element,
                problemMessage(finding),
                highlightType(finding.kind),
            )
        }
    }

    fun problemMessage(finding: ArmeriaSpringBootSettingsConflict.Finding): String =
        when (finding.kind) {
            ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT ->
                message("inspection.springboot.settings.port.conflict")
            ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE ->
                message(
                    "inspection.springboot.settings.internal.missing",
                    finding.includeId.orEmpty(),
                    finding.pathKey.orEmpty(),
                )
        }

    private fun highlightType(kind: ArmeriaSpringBootSettingsConflict.Kind): ProblemHighlightType =
        when (kind) {
            ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT -> ProblemHighlightType.WARNING
            ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE -> ProblemHighlightType.WEAK_WARNING
        }
}

internal fun interface SettingsHighlightLocator {
    fun locate(
        file: PsiFile,
        finding: ArmeriaSpringBootSettingsConflict.Finding,
    ): PsiElement?
}
