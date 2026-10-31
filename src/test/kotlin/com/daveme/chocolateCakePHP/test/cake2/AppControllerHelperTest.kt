package com.daveme.chocolateCakePHP.test.cake2

class AppControllerHelperTest : HelperFixtureTestCase() {

    override fun setUpTestFiles() {
        configureByFiles("cake2/vendor/cakephp.php")
        addHelpers("BootstrapHtmlHelper", "AdminHtmlHelper")
        addController("AppController", "Controller", htmlAlias("BootstrapHtml"))
        addController("MoviesController", "AppController", "public function index() {}")
        addController("AdminController", "AppController", htmlAlias("AdminHtml") + "public function index() {}")
    }

    fun `test an AppController alias replaces the convention type in a template`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Movies/index.ctp"))
    }

    fun `test a controller's alias wins over AppController's in its template`() {
        assertEquals(setOf("\\AdminHtmlHelper"), htmlTypeIn("cake2/app/View/Admin/index.ctp"))
    }

    fun `test a layout unions the aliases without the convention type`() {
        assertEquals(
            setOf("\\BootstrapHtmlHelper", "\\AdminHtmlHelper"),
            htmlTypeIn("cake2/app/View/Layouts/default.ctp")
        )
    }
}
