@file:Suppress("UnstableApiUsage")

package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.starters.local.Library
import com.intellij.ide.starters.local.LibraryCategory
import com.intellij.ide.starters.shared.LibraryLink
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message

val ArmeriaCategory =
    LibraryCategory(
        id = "armeria",
        icon = ArmeriaIcons.Armeria,
        title = message("module.category.armeria.title"),
        description = message("module.category.armeria.description"),
    )

fun armeriaLibrary(
    id: String,
    title: String,
    description: String,
    links: List<LibraryLink> = emptyList(),
) = Library(
    id = id,
    icon = null,
    title = title,
    description = description,
    group = "com.linecorp.armeria",
    artifact = id,
    category = ArmeriaCategory,
    links = links,
)
