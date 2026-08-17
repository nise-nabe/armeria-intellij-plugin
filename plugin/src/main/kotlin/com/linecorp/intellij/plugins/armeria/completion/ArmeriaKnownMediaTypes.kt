package com.linecorp.intellij.plugins.armeria.completion

internal object ArmeriaKnownMediaTypes {
    val NAMES =
        listOf(
            "application/json",
            "application/json; charset=utf-8",
            "application/problem+json",
            "application/graphql+json",
            "application/vnd.api+json",
            "text/plain",
            "text/plain; charset=utf-8",
            "text/event-stream",
            "application/xml",
            "application/binary",
            "application/octet-stream",
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "application/protobuf",
        )
}
