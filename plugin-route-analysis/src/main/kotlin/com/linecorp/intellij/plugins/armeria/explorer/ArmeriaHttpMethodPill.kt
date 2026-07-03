package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import java.awt.Color

/**
 * HTTP Client / Swagger-style method pill colors (foreground and background).
 */
object ArmeriaHttpMethodPill {
    private val STANDARD_HTTP_METHODS: Set<String> = ArmeriaRouteSupport.routeAnnotations.values.toSet()

    fun pillLabel(route: ArmeriaRoute): String = when (route.routeMatch) {
        RouteMatch.ANNOTATED_HTTP -> route.httpMethod
        RouteMatch.ANNOTATED_SERVICE -> "ANN"
        RouteMatch.SERVICE -> "ALL"
        RouteMatch.SERVICE_UNDER -> "PRE"
        RouteMatch.NON_HTTP -> route.protocol.uppercase()
        RouteMatch.RUNTIME -> route.httpMethod
    }

    fun pillText(label: String): String = " $label "

    fun textAttributes(route: ArmeriaRoute): SimpleTextAttributes {
        val label = pillLabel(route)
        val colors = methodColors(label) ?: neutralColors()
        return SimpleTextAttributes(
            SimpleTextAttributes.STYLE_BOLD,
            colors.foreground,
            colors.background,
        )
    }

    fun isStandardHttpMethod(label: String): Boolean = label in STANDARD_HTTP_METHODS

    private fun methodColors(method: String): PillColors? = when (method) {
        "GET" -> PillColors(GET_FOREGROUND, GET_BACKGROUND)
        "POST" -> PillColors(POST_FOREGROUND, POST_BACKGROUND)
        "PUT" -> PillColors(PUT_FOREGROUND, PUT_BACKGROUND)
        "DELETE" -> PillColors(DELETE_FOREGROUND, DELETE_BACKGROUND)
        "PATCH" -> PillColors(PATCH_FOREGROUND, PATCH_BACKGROUND)
        "HEAD" -> PillColors(HEAD_FOREGROUND, HEAD_BACKGROUND)
        "OPTIONS" -> PillColors(OPTIONS_FOREGROUND, OPTIONS_BACKGROUND)
        "TRACE" -> PillColors(TRACE_FOREGROUND, TRACE_BACKGROUND)
        else -> null
    }

    private fun neutralColors(): PillColors = PillColors(NEUTRAL_FOREGROUND, NEUTRAL_BACKGROUND)

    private data class PillColors(val foreground: Color, val background: Color)

    // Swagger UI-like palette (fixed colors aligned with HTTP method tags in Services)
    private val GET_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val GET_BACKGROUND = JBColor(Color(0x61, 0xAF, 0xFE), Color(0x4A, 0x7F, 0xC4))

    private val POST_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val POST_BACKGROUND = JBColor(Color(0x49, 0xCC, 0x90), Color(0x3A, 0xA8, 0x72))

    private val PUT_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val PUT_BACKGROUND = JBColor(Color(0xFC, 0xA1, 0x30), Color(0xC4, 0x7E, 0x24))

    private val DELETE_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val DELETE_BACKGROUND = JBColor(Color(0xF9, 0x3E, 0x3E), Color(0xC4, 0x2F, 0x2F))

    private val PATCH_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val PATCH_BACKGROUND = JBColor(Color(0x50, 0xE3, 0xC2), Color(0x3A, 0xB5, 0x98))

    private val HEAD_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val HEAD_BACKGROUND = JBColor(Color(0x90, 0x12, 0xFE), Color(0x6E, 0x0E, 0xC4))

    private val OPTIONS_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val OPTIONS_BACKGROUND = JBColor(Color(0x0D, 0x5A, 0xA7), Color(0x0A, 0x45, 0x82))

    private val TRACE_FOREGROUND = Color(0xFF, 0xFF, 0xFF)
    private val TRACE_BACKGROUND = JBColor(Color(0x70, 0x70, 0x70), Color(0x55, 0x55, 0x55))

    private val NEUTRAL_FOREGROUND = JBColor(Color(0x33, 0x33, 0x33), Color(0xBB, 0xBB, 0xBB))
    private val NEUTRAL_BACKGROUND = JBColor(Color(0xE8, 0xE8, 0xE8), Color(0x4A, 0x4A, 0x4A))
}
