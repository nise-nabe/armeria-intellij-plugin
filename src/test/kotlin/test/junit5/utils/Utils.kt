@file:Suppress("unused")

package com.linecorp.intellij.plugins.armeria.test.junit5.utils

import org.junit.platform.commons.util.AnnotationUtils
import org.junit.platform.commons.util.ReflectionUtils
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN
import java.lang.reflect.AccessibleObject
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import kotlin.reflect.KClass

inline fun <reified T : Annotation> AnnotatedElement.isAnnotated(): Boolean {
    return AnnotationUtils.isAnnotated(this, T::class.java)
}

inline fun <reified T : Annotation> KClass<Any>.findAnnotatedFields(
    crossinline predicate: (Field) -> Boolean = ReflectionUtils::isNotStatic
): List<Field> {
    return java.findAnnotatedFields<T>(predicate)
}

inline fun <reified T : Annotation> Class<Any>.findAnnotatedFields(
    crossinline predicate: (Field) -> Boolean = ReflectionUtils::isNotStatic
): List<Field> {
    return AnnotationUtils.findAnnotatedFields(this, T::class.java) { predicate.invoke(it) }
}

inline fun <reified T> Class<Any>.isAssignableTo(): Boolean {
    return ReflectionUtils.isAssignableTo(this, T::class.java)
}

inline fun <reified T> KClass<Any>.isAssignableTo(): Boolean {
    return ReflectionUtils.isAssignableTo(java, T::class.java)
}

inline fun <reified T> Field.isAssignableTo(): Boolean {
    return ReflectionUtils.isAssignableTo(type, T::class.java)
}

inline fun <reified T> AnnotatedElement.isAssignableTo(): Boolean {
    return ReflectionUtils.isAssignableTo(this, T::class.java)
}

inline fun <reified T> KClass<out Any>.findFields(): List<Field> {
    return java.findFields<T>()
}

inline fun <reified T> Class<out Any>.findFields(): List<Field> {
    return ReflectionUtils.findFields(this, { it.isAssignableTo<T>() }, TOP_DOWN)
}

fun <T:  AccessibleObject> T.toAccessible(): T {
    return ReflectionUtils.makeAccessible(this)
}
