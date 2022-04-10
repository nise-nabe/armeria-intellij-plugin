@file:Suppress("unused")

package com.linecorp.intellij.plugins.armeria.test.utils

import org.intellij.lang.annotations.Language
import java.io.File

fun File.writeKotlin(@Language("kotlin") src: String) {
    writeText(src)
}

fun File.writeGroovy(@Language("Groovy") src: String) {
    writeText(src)
}

fun File.writeXml(@Language("XML") src: String) {
    writeText(src)
}
