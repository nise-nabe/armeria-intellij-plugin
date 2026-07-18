package com.linecorp.intellij.plugins.armeria.test

/**
 * Shared Armeria PSI stubs for [LightJavaCodeInsightFixtureTestCase] subclasses.
 */
abstract class ArmeriaFixtureTestBase : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
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

    protected fun registerRouteDetailFormatterStubs() = myFixture.registerRouteDetailFormatterStubs()

    protected fun registerRouteDuplicateIndexStubs() = myFixture.registerRouteDuplicateIndexStubs()

    protected fun registerRouteCollectorStubs() = myFixture.registerRouteCollectorStubs()

    protected fun registerKotlinRouteCollectorStubs() = myFixture.registerKotlinRouteCollectorStubs()

    protected fun registerExtendedRegistrationCollectorStubs() = myFixture.registerExtendedRegistrationCollectorStubs()

    protected fun registerKotlinExtendedRegistrationCollectorStubs() = myFixture.registerKotlinExtendedRegistrationCollectorStubs()
}
