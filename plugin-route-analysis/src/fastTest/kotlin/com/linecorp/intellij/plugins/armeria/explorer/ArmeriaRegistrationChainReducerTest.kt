package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.message
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaRegistrationChainReducerTest {
    @Test
    fun reduceFluentRouteChain_stripsFullyQualifiedHttpMethodNames() {
        val chain =
            ArmeriaRegistrationChainReducer.reduceFluentRouteChain(
                stepsFromBuildUpward =
                    listOf(
                        RegistrationChainStep(
                            methodName = "methods",
                            firstStringArg = null,
                            rawMethodArgs =
                                listOf(
                                    "com.linecorp.armeria.common.HttpMethod.POST",
                                    "HttpMethod.PUT",
                                ),
                        ),
                        RegistrationChainStep(
                            methodName = "path",
                            firstStringArg = "/items",
                            rawMethodArgs = emptyList(),
                        ),
                        RegistrationChainStep(
                            methodName = "route",
                            firstStringArg = null,
                            rawMethodArgs = emptyList(),
                        ),
                    ),
                requireRouteAnchor = true,
                handlerTarget = "handler",
                defaultTarget = message("route.explorer.target.fluentRoute"),
            )

        assertEquals("POST, PUT", chain?.httpMethod)
    }

    @Test
    fun reduceRouteDecoratorChain_stripsFullyQualifiedHttpMethodNames() {
        val chain =
            ArmeriaRegistrationChainReducer.reduceRouteDecoratorChain(
                steps =
                    listOf(
                        RegistrationChainStep(
                            methodName = "methods",
                            firstStringArg = null,
                            rawMethodArgs = listOf("com.linecorp.armeria.common.HttpMethod.DELETE"),
                        ),
                        RegistrationChainStep(
                            methodName = "path",
                            firstStringArg = "/decorated",
                            rawMethodArgs = emptyList(),
                        ),
                    ),
                defaultDecoratorLabel = message("route.explorer.target.routeDecorator"),
            )

        assertEquals("DELETE", chain.methods)
    }
}
