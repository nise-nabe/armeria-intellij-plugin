package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message

class ArmeriaDuplicateRegistrationKotlinInspection : ArmeriaDuplicateRegistrationInspection() {
    override fun getDisplayName(): String = message("inspection.duplicate.registration.kotlin.display.name")
}
