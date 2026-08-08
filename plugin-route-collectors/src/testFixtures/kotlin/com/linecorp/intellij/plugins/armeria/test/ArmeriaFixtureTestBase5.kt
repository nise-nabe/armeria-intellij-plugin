package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

/**
 * JUnit 5 shared Armeria PSI stubs for [ArmeriaLightJavaCodeInsightFixtureTestCase5] subclasses.
 */
abstract class ArmeriaFixtureTestBase5 : ArmeriaLightJavaCodeInsightFixtureTestCase5() {
    protected fun collectRoutes(): List<ArmeriaRoute> = ArmeriaRouteCollector.collect(project)

    override fun onFixtureSetUp() {
        registerArmeriaStubs()
    }

    protected open fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaServerStubs()
        registerArmeriaServiceStubs()
    }

    protected fun registerArmeriaAnnotationStubs() = myFixture.registerArmeriaAnnotationStubs()

    protected fun registerArmeriaBlockingAnnotationStubs() = myFixture.registerArmeriaBlockingAnnotationStubs()

    protected fun registerContentAnnotationStubs() = myFixture.registerContentAnnotationStubs()

    protected fun registerResolvableArmeriaServerStubs() = myFixture.registerResolvableArmeriaServerStubs()

    protected fun registerArmeriaServerStubs() = myFixture.registerArmeriaServerStubs()

    protected fun registerMinimalArmeriaServerStubs() = myFixture.registerMinimalArmeriaServerStubs()

    protected fun registerArmeriaServiceStubs() = myFixture.registerArmeriaServiceStubs()

    protected fun registerArmeriaIdlStubs() = myFixture.registerArmeriaIdlStubs()

    protected fun registerSpringAnnotationStubs() = myFixture.registerSpringAnnotationStubs()

    protected fun registerArmeriaSpringStubs() = myFixture.registerArmeriaSpringStubs()

    protected fun registerServletServiceStubs() = myFixture.registerServletServiceStubs()

    protected fun registerSpringWebMvcStubs() = myFixture.registerSpringWebMvcStubs()

    protected fun configureTomcatMount(path: String) = myFixture.configureTomcatMount(path)

    protected fun registerRouteDetailFormatterStubs() = myFixture.registerRouteDetailFormatterStubs()

    protected fun registerRouteDuplicateIndexStubs() = myFixture.registerRouteDuplicateIndexStubs()

    protected fun registerRouteCollectorStubs() = myFixture.registerRouteCollectorStubs()

    protected fun registerKotlinRouteCollectorStubs() = myFixture.registerKotlinRouteCollectorStubs()

    protected fun registerExtendedRegistrationCollectorStubs() = myFixture.registerExtendedRegistrationCollectorStubs()

    protected fun registerKotlinExtendedRegistrationCollectorStubs() = myFixture.registerKotlinExtendedRegistrationCollectorStubs()
}
