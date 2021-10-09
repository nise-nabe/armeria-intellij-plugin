package com.nisecoder.intellij.plugins.armeria

import com.intellij.ui.IconManager
import javax.swing.Icon

object ArmeriaIcons {
    private fun load(path: String): Icon {
        return IconManager.getInstance().getIcon(path, ArmeriaIcons::class.java)
    }

    val Armeria: Icon = load("icons/armeria.svg")
}
