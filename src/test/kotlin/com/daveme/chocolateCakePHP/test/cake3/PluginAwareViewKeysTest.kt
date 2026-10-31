package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.view.ElementToUsagesGotoRelatedProvider
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.daveme.chocolateCakePHP.view.directReferencesTo
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Variable

class PluginAwareViewKeysTest : Cake3BaseTestCase() {

    private val app = "cake3/src/Template"
    private val blog = "cake3/plugins/Blog/src/Template"
    private val other = "cake3/plugins/Other/src/Template"
    private val override = "cake3/src/Template/Plugin/Blog"
    private val extension = "ctp"
    private val element = "Element"
    private val cell = "Cell"
    private val appSource = "cake3/src"
    private val blogSource = "cake3/plugins/Blog/src"
    private val otherSource = "cake3/plugins/Other/src"

    private val settings get() = Settings.getInstance(project)

    override fun setUpTestFiles() {
        configureByFiles("cake3/vendor/cakephp.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.$extension", "<?php $code")

    private fun controller(source: String, namespace: String, actions: String, name: String = "Articles") {
        myFixture.addFileToProject("$source/Controller/${name}Controller.php", """
            <?php
            namespace $namespace\Controller;
            class ${name}Controller extends \Cake\Controller\Controller {
                $actions
            }
        """.trimIndent())
    }

    private fun widgetCell(source: String, namespace: String, variable: String) {
        myFixture.addFileToProject("$source/View/Cell/WidgetCell.php", """
            <?php
            namespace $namespace\View\Cell;
            class WidgetCell extends \Cake\View\Cell {
                public function display() { ${'$'}this->set('$variable', 1); }
            }
        """.trimIndent())
    }

    private fun appAction(variable: String = "appVar") =
        controller(appSource, "App", "public function index() { \$this->set('$variable', 1); }")

    private fun blogAction(variable: String = "blogVar") =
        controller(blogSource, "Blog", "public function index() { \$this->set('$variable', 1); }")

    private fun viewFile(file: PsiFile) = viewFileOf(project, settings, file.virtualFile)!!

    private fun variables(file: PsiFile): Set<String> =
        ViewVariableIndexService.lookupVariablesFromViewPathInSmartReadAction(project, settings, viewFile(file)).keys

    private fun exists(file: PsiFile, name: String) =
        ViewVariableIndexService.variableExistsInViewPath(project, settings, viewFile(file), name)

    private fun relatedOwners(file: PsiFile): Set<String> =
        ViewToControllerGotoRelatedProvider().getItems(file.firstChild).mapNotNull { item ->
            val method = item.element as? Method ?: PsiTreeUtil.getParentOfType(item.element, Method::class.java)
            method?.containingClass?.fqn
        }.toSet()

    private fun referringPaths(file: PsiFile): Set<String> =
        directReferencesTo(project, settings, viewFile(file)).map { it.path.substringAfter("/cake3/") }.toSet()

    private fun elementUsages(file: PsiFile): Set<String> =
        ElementToUsagesGotoRelatedProvider().getItems(file.firstChild)
            .mapNotNull { it.element?.containingFile?.virtualFile?.path?.substringAfter("/cake3/") }.toSet()

    fun `test plugin with own template keeps controller variables apart`() {
        appAction()
        blogAction()
        val appTemplate = template("$app/Articles/index")
        val blogTemplate = template("$blog/Articles/index")

        assertEquals(setOf("appVar"), variables(appTemplate))
        assertEquals(setOf("blogVar"), variables(blogTemplate))
        assertFalse(exists(appTemplate, "blogVar"))
        assertTrue(exists(blogTemplate, "blogVar"))
    }

    fun `test plugin without its own template renders the app's`() {
        appAction()
        blogAction()
        val appTemplate = template("$app/Articles/index")

        assertEquals(setOf("appVar", "blogVar"), variables(appTemplate))
        assertEquals(setOf("\\App\\Controller\\ArticlesController", "\\Blog\\Controller\\ArticlesController"), relatedOwners(appTemplate))
    }

    fun `test adding the plugin template drops the plugin's class from the app's`() {
        appAction()
        blogAction()
        val appTemplate = template("$app/Articles/index")
        assertEquals(setOf("appVar", "blogVar"), variables(appTemplate))

        template("$blog/Articles/index")
        assertEquals(setOf("appVar"), variables(appTemplate))
        assertEquals(setOf("\\App\\Controller\\ArticlesController"), relatedOwners(appTemplate))
    }

    fun `test related items list only the matching class`() {
        appAction()
        blogAction()
        val appTemplate = template("$app/Articles/index")
        val blogTemplate = template("$blog/Articles/index")

        assertEquals(setOf("\\App\\Controller\\ArticlesController"), relatedOwners(appTemplate))
        assertEquals(setOf("\\Blog\\Controller\\ArticlesController"), relatedOwners(blogTemplate))
        assertEquals(setOf("plugins/Blog/src/Controller/ArticlesController.php"), referringPaths(blogTemplate))
    }

    fun `test cell variables are kept apart`() {
        widgetCell(appSource, "App", "appVar")
        widgetCell(blogSource, "Blog", "blogVar")
        val appTemplate = template("$app/$cell/Widget/display")
        val blogTemplate = template("$blog/$cell/Widget/display")

        assertEquals(setOf("appVar"), variables(appTemplate))
        assertEquals(setOf("blogVar"), variables(blogTemplate))
    }

    fun `test plugin cell without its own template renders the app's`() {
        widgetCell(appSource, "App", "appVar")
        widgetCell(blogSource, "Blog", "blogVar")
        assertEquals(setOf("appVar", "blogVar"), variables(template("$app/$cell/Widget/display")))
    }

    fun `test prefixed render links to the plugin template, or the app's without one`() {
        controller(appSource, "App", "public function show() { \$this->render('Blog.other'); }", "Pages")
        val appTemplate = template("$app/Pages/other")
        assertEquals(setOf("src/Controller/PagesController.php"), referringPaths(appTemplate))

        val blogTemplate = template("$blog/Pages/other")
        assertEquals(emptySet<String>(), referringPaths(appTemplate))
        assertEquals(setOf("src/Controller/PagesController.php"), referringPaths(blogTemplate))
    }

    fun `test render of another plugin never reaches the calling plugin`() {
        controller(blogSource, "Blog", "public function show() { \$this->render('Other.x'); }", "Pages")
        val blogTemplate = template("$blog/Pages/x")
        val appTemplate = template("$app/Pages/x")
        val otherTemplate = template("$other/Pages/x")

        assertEquals(emptySet<String>(), referringPaths(blogTemplate))
        assertEquals(emptySet<String>(), referringPaths(appTemplate))
        assertEquals(setOf("plugins/Blog/src/Controller/PagesController.php"), referringPaths(otherTemplate))
    }

    fun `test cell call resolves in its prefix or the app, not the calling file`() {
        val caller = "\$this->cell('Blog.Widget');"
        val appCaller = template("$app/Pages/home", caller)
        val appWidget = template("$app/$cell/Widget/display")
        assertTrue(directReferencesTo(project, settings, viewFile(appWidget)).any { it.file == appCaller.virtualFile })

        val blogWidget = template("$blog/$cell/Widget/display")
        assertFalse(directReferencesTo(project, settings, viewFile(appWidget)).any { it.file == appCaller.virtualFile })
        assertTrue(directReferencesTo(project, settings, viewFile(blogWidget)).any { it.file == appCaller.virtualFile })

        val pluginCaller = template("$blog/Pages/home", "\$this->cell('Widget');")
        assertFalse(directReferencesTo(project, settings, viewFile(blogWidget)).any { it.file == pluginCaller.virtualFile })
        assertTrue(directReferencesTo(project, settings, viewFile(appWidget)).any { it.file == pluginCaller.virtualFile })
    }

    fun `test override template resolves to the plugin's controller`() {
        appAction()
        blogAction()
        val overrideTemplate = template("$override/Articles/index")

        assertEquals(setOf("blogVar"), variables(overrideTemplate))
        assertEquals(setOf("\\Blog\\Controller\\ArticlesController"), relatedOwners(overrideTemplate))
    }

    fun `test element data stays with the plugin of the calling template`() {
        val appElement = template("$app/$element/shared")
        val blogElement = template("$blog/$element/shared")
        template("$blog/Pages/home", "\$this->element('shared', ['blogData' => 1]);")
        template("$app/Pages/home", "\$this->element('shared', ['appData' => 1]);")

        assertEquals(setOf("appData"), variables(appElement))
        assertEquals(setOf("blogData"), variables(blogElement))
    }

    fun `test two calls in one template are told apart by offset`() {
        appAction()
        template("$app/Articles/index", """
            ${'$'}this->element('Blog.shared', ['blogOnly' => 1]);
            ${'$'}this->element('shared', ['appOnly' => 1]);
        """.trimIndent())
        val appElement = template("$app/$element/shared")
        val blogElement = template("$blog/$element/shared")

        assertEquals(setOf("appVar", "appOnly"), variables(appElement))
        assertEquals(setOf("blogOnly"), variables(blogElement) - "appVar")
    }

    fun `test element data past the lookup budget is still read`() {
        val shared = template("$app/$element/shared")
        for (i in 1..20) {
            template("$app/Pages/page$i", "\$this->element('shared', ['data$i' => 1]);")
        }
        assertEquals((1..20).map { "data$it" }.toSet(), variables(shared))
    }

    fun `test type follows the template's own class`() {
        controller(appSource, "App", "public function index() { \$this->set('shared', 1); }")
        controller(blogSource, "Blog", "public function index() { \$this->set('shared', 'text'); }")
        val appTemplate = template("$app/Articles/index", "echo \$shared;")
        val blogTemplate = template("$blog/Articles/index", "echo \$shared;")

        assertEquals("int", variableType(appTemplate))
        assertEquals("string", variableType(blogTemplate))
    }

    private fun variableType(file: PsiFile): String =
        PsiTreeUtil.findChildrenOfType(file, Variable::class.java).first { it.name == "shared" }
            .type.global(project).types.map { it.removePrefix("\\") }.filterNot { it == "?" }.joinToString("|")

    fun `test element usages follow the rule from both elements`() {
        val appElement = template("$app/$element/shared")
        val blogElement = template("$blog/$element/shared")
        template("$app/Pages/plain", "\$this->element('shared');")
        template("$app/Pages/prefixed", "\$this->element('Blog.shared');")
        template("$blog/Pages/plain", "\$this->element('shared');")

        assertEquals(setOf("src/Template/Pages/plain.ctp"), elementUsages(appElement))
        assertEquals(setOf("src/Template/Pages/prefixed.ctp", "plugins/Blog/src/Template/Pages/plain.ctp"), elementUsages(blogElement))
    }

    fun `test element in the override directory finds its usages`() {
        val overrideElement = template("$override/$element/shared")
        template("$blog/$element/unrelated")
        template("$blog/Pages/plain", "\$this->element('shared');")
        assertEquals(setOf("plugins/Blog/src/Template/Pages/plain.ctp"), elementUsages(overrideElement))
    }

    fun `test element in a fallback template follows the rendering plugin controller`() {
        blogAction()
        template("$app/Articles/index", "\$this->element('shared');")
        val appElement = template("$app/$element/shared")
        val blogElement = template("$blog/$element/shared")

        assertEquals(setOf("blogVar"), variables(blogElement))
        assertEquals(emptySet<String>(), variables(appElement))
    }

    fun `test prefixed hop keeps carried references`() {
        appAction()
        template("$app/Articles/index", "\$this->element('Blog.a');")
        template("$blog/$element/a", "\$this->element('b');")
        val appB = template("$app/$element/b")
        val blogB = template("$blog/$element/b")

        assertEquals(setOf("appVar"), variables(appB))
        assertEquals(emptySet<String>(), variables(blogB))
    }

    fun `test set in an including template follows the rendering class`() {
        blogAction()
        template("$app/Articles/index", "\$this->set('fromTemplate', 1); \$this->element('shared', ['passed' => 1]);")
        val appElement = template("$app/$element/shared")
        val blogElement = template("$blog/$element/shared")

        assertEquals(setOf("blogVar", "fromTemplate", "passed"), variables(blogElement))
        assertEquals(emptySet<String>(), variables(appElement))
    }

    fun `test configured vendor plugin is a plugin`() {
        val state = settings.state.copy()
        state.pluginConfigs = listOf(com.daveme.chocolateCakePHP.CakeThreePluginConfig(
            pluginName = "Vendored", namespace = "\\Vendored", pluginPath = "vendor/acme/vendored"
        ))
        settings.loadState(state)
        val file = template("cake3/vendor/acme/vendored/src/Template/Articles/index")
        assertEquals("Vendored", viewFile(file).plugin)
        assertEquals("Articles/index", viewFile(file).templateKey)
    }
}
