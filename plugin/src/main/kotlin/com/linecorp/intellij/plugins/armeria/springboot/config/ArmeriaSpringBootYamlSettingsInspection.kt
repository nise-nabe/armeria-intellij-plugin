package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaSpringBootYamlSettingsInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.springboot.settings.display.name")

    override fun getStaticDescription(): String = message("inspection.springboot.settings.description")

    override fun isAvailableForFile(file: PsiFile): Boolean =
        ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(file.name) &&
            (file.name.endsWith(".yml") || file.name.endsWith(".yaml"))

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                ArmeriaSpringBootSettingsInspectionSupport.inspect(file, holder, YamlSettingsHighlightLocator)
            }
        }
}

private object YamlSettingsHighlightLocator : SettingsHighlightLocator {
    override fun locate(
        file: PsiFile,
        finding: ArmeriaSpringBootSettingsConflict.Finding,
    ) = when (finding.kind) {
        ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT ->
            ArmeriaSpringBootYamlPsiSupport.highlightForPath(file, finding.highlightPath)
        ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE -> {
            val includeId = finding.includeId
            if (includeId == null) {
                ArmeriaSpringBootYamlPsiSupport.highlightForPath(file, finding.highlightPath)
            } else {
                ArmeriaSpringBootYamlPsiSupport.highlightIncludeToken(file, includeId)
            }
        }
    }
}
