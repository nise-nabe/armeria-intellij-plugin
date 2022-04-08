package com.linecorp.intellij.plugins.armeria

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

class ArmeriaBundle: DynamicBundle(BUNDLE) {
    companion object {
        const val BUNDLE = "messages.ArmeriaBundle"
    }
}

val INSTANCE = ArmeriaBundle()

@Nls
fun message(
    key: @PropertyKey(resourceBundle = ArmeriaBundle.BUNDLE) String,
    vararg params: Any
): String {
    return INSTANCE.getMessage(key, *params)
}
