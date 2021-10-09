package com.nisecoder.intellij.plugins.armeria

import com.intellij.ui.IconManager
import javax.swing.Icon

inline fun <reified T> IconManager.getIcon(path: String): Icon {
    return getIcon(path, T::class.java)
}
