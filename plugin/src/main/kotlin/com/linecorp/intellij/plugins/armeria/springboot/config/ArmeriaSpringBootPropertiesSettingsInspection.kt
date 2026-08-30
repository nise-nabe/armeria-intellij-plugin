package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.explorer.spring.SpringArmeriaConfigSemantics
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaSpringBootPropertiesSettingsInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.springboot.settings.properties.display.name")

    override fun getStaticDescription(): String = message("inspection.springboot.settings.description")

    override fun isAvailableForFile(file: PsiFile): Boolean =
        ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(file.name) &&
            file.name.endsWith(".properties")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                ArmeriaSpringBootSettingsInspectionSupport.inspect(file, holder, PropertiesSettingsHighlightLocator)
            }
        }
}

private object PropertiesSettingsHighlightLocator : SettingsHighlightLocator {
    override fun locate(
        file: PsiFile,
        finding: ArmeriaSpringBootSettingsConflict.Finding,
    ): PsiElement? {
        val propertiesFile = file as? PropertiesFile ?: return null
        return when (finding.kind) {
            ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT ->
                highlightProperty(propertiesFile, finding.highlightPath)
            ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE -> {
                val includeId = finding.includeId
                if (includeId == null) {
                    highlightProperty(propertiesFile, finding.highlightPath)
                } else {
                    highlightIncludeToken(propertiesFile, includeId)
                }
            }
        }
    }

    private fun highlightIncludeToken(
        file: PropertiesFile,
        includeId: String,
    ): PsiElement? {
        var last: PsiElement? = null
        for (property in file.properties) {
            val key = property.unescapedKey ?: continue
            if (ArmeriaSpringBootConfigSupport.canonicalConfigKey(key) !=
                ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE
            ) {
                continue
            }
            val value = property.value ?: continue
            val tokens =
                SpringArmeriaConfigSemantics.parseIncludeTokens(
                    ArmeriaSpringBootConfigSupport.stripInlineComment(value),
                )
            if (includeId in SpringArmeriaConfigSemantics.expandIncludes(tokens)) {
                last = property.psiElement
            }
        }
        return last
    }

    private fun highlightProperty(
        file: PropertiesFile,
        canonicalPath: String,
    ): PsiElement? {
        val property =
            file.properties.lastOrNull { candidate ->
                val key = candidate.unescapedKey ?: return@lastOrNull false
                ArmeriaSpringBootConfigSupport.canonicalConfigKey(key) == canonicalPath
            } ?: return null
        return property.psiElement
    }
}
