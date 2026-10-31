package com.daveme.chocolateCakePHP.test.cake2

class FrameworkNotIndexedHelperTest : HelperFixtureTestCase() {

    override fun setUpTestFiles() {
        addHelpers("BootstrapHtmlHelper", "FooHtmlHelper")
        addController("AppController", "Controller", htmlAlias("BootstrapHtml"))
        addPluginController("BarsController", "Controller", htmlAlias("FooHtml"))
    }

    fun `test a layout finds a direct Controller subclass when the framework class is not indexed`() {
        assumeStubVendor("The real framework is always attached")
        assertEquals(
            setOf("\\BootstrapHtmlHelper", "\\FooHtmlHelper"),
            htmlTypeIn("cake2/app/View/Layouts/default.ctp")
        )
    }
}
