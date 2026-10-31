package com.daveme.chocolateCakePHP.test.cake2

class DirectControllerHelperTest : HelperFixtureTestCase() {

    override fun setUpTestFiles() {
        configureByFiles("cake2/vendor/cakephp.php")
        addHelpers("BootstrapHtmlHelper", "FooHtmlHelper")
        addController("AppController", "Controller", htmlAlias("BootstrapHtml"))
        addController("MoviesController", "AppController", "public function index() {}")
        addController("PlainController", "Controller", "public function index() {}")
        addPluginController("FooAppController", "Controller", "")
        addPluginController("BarsController", "FooAppController", htmlAlias("FooHtml") + "public function index() {}")
    }

    fun `test a direct Controller subclass keeps the convention type in its template`() {
        assertEquals(setOf("\\HtmlHelper"), htmlTypeIn("cake2/app/View/Plain/index.ctp"))
    }

    fun `test a layout unions the aliases and the convention type`() {
        assertEquals(
            setOf("\\BootstrapHtmlHelper", "\\FooHtmlHelper", "\\HtmlHelper"),
            htmlTypeIn("cake2/app/View/Layouts/default.ctp")
        )
    }

    fun `test a plugin bypassing AppController keeps its alias in its own layout`() {
        assertTrue(htmlTypeIn("cake2/app/Plugin/Foo/View/Layouts/default.ctp").contains("\\FooHtmlHelper"))
    }
}
