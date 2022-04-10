package com.linecorp.intellij.plugins.armeria.test.intellij

import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile

/** @see  com.intellij.ide.starters.local.GeneratorContext */
@Suppress("unused")
interface GeneratorContextForTest {
    val starterId: String
    val moduleName: String
    val group: String
    val artifact: String
    val version: String
    val testRunnerId: String?
    val rootPackage: String
    val sdkVersion: JavaSdkVersion?
    val assets: List<GeneratorAsset>
    val outputDirectory: VirtualFile

    fun hasLanguage(languageId: String): Boolean
    fun hasLibrary(libraryId: String): Boolean
    fun hasAnyLibrary(vararg ids: String): Boolean
    fun hasAllLibraries(vararg ids: String): Boolean
    fun getVersion(group: String, artifact: String): String?
    fun getBomProperty(propertyId: String): String?
    fun getProperty(propertyId: String): String?
    fun asPlaceholder(propertyId: String): String
    fun isSdkAtLeast(version: String): Boolean
    val rootPackagePath: String
    val sdkFeatureVersion: Int
}
