package com.nisecoder.intellij.plugins.armeria

import com.intellij.ui.IconManager
import javax.swing.Icon

object ArmeriaIcons {
    private fun load(path: String): Icon {
        return IconManager.getInstance().getIcon<ArmeriaIcons>(path)
    }

    val Armeria: Icon = load("icons/armeria.svg")
}
