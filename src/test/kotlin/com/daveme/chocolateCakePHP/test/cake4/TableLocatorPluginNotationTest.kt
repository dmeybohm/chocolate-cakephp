package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.jetbrains.php.lang.psi.elements.PhpClass

class TableLocatorPluginNotationTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Model/Table/RatingsTable.php",
            "cake4/src4/Model/Entity/Rating.php",
            "cake4/vendor/test/test_plugin/src/Model/Table/RatingsTable.php",
            "cake4/vendor/test/test_plugin/src/Model/Table/PluginItemsTable.php",
            "cake4/vendor/test/test_plugin/src/Model/Table/Sub/NestedTable.php",
            "cake4/vendor/test/test_plugin/src/Model/Entity/PluginItem.php",
            "cake4/vendor/acme/vendored/src/Model/Table/ThingsTable.php",
            "cake4/vendor/cakephp.php",
        )
    }

    private fun completionsInController(body: String): List<String> {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;
        use TestPlugin\Model\Table\RatingsTable;

        class MovieController extends Controller
        {
            public function index() {
                $body
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun tableCompletions(alias: String): List<String> =
        completionsInController("\$this->fetchTable('$alias')-><caret>")

    fun `test plugin alias has the plugin table type`() {
        val result = tableCompletions("TestPlugin.PluginItems")
        assertContainsElements(result, "pluginItemsMethod", "findAll")
    }

    fun `test plugin alias does not include the app table of the same name`() {
        val result = tableCompletions("TestPlugin.Ratings")
        assertContainsElements(result, "pluginRatingsMethod")
        assertDoesntContain(result, "appRatingsMethod")
    }

    fun `test bare alias does not include the plugin table of the same name`() {
        val result = tableCompletions("Ratings")
        assertContainsElements(result, "appRatingsMethod")
        assertDoesntContain(result, "pluginRatingsMethod")
    }

    fun `test getTableLocator get with a plugin alias has the plugin table type`() {
        val result = completionsInController("\$this->getTableLocator()->get('TestPlugin.Ratings')-><caret>")
        assertContainsElements(result, "pluginRatingsMethod")
        assertDoesntContain(result, "appRatingsMethod")
    }

    fun `test plugin alias in a variable keeps the plugin table type`() {
        val result = completionsInController("\$table = \$this->fetchTable('TestPlugin.Ratings');\n\$table-><caret>")
        assertContainsElements(result, "pluginRatingsMethod")
    }

    fun `test find on a plugin alias keeps the table in the query type`() {
        val result = completionsInController("\$this->fetchTable('TestPlugin.PluginItems')->find()->find('<caret>')")
        assertContainsElements(result, "featuredItems")
    }

    fun `test custom finders are completed for a plugin alias`() {
        val result = completionsInController("\$this->fetchTable('TestPlugin.PluginItems')->find('<caret>')")
        assertContainsElements(result, "featuredItems")
    }

    fun `test get on a plugin alias has the plugin entity type`() {
        val result = completionsInController("""
            ${'$'}table = ${'$'}this->fetchTable('TestPlugin.PluginItems');
            ${'$'}item = ${'$'}table->get(1);
            ${'$'}item-><caret>
        """.trimIndent())
        assertContainsElements(result, "pluginItemEntityMethod")
    }

    fun `test get on a plugin table does not have the app entity type`() {
        val result = completionsInController("""
            ${'$'}table = ${'$'}this->fetchTable('TestPlugin.Ratings');
            ${'$'}item = ${'$'}table->get(1);
            ${'$'}item-><caret>
        """.trimIndent())
        assertDoesntContain(result, "appRatingEntityMethod")
    }

    private fun configurePlugin(pluginName: String, namespace: String, pluginPath: String) {
        val settings = Settings.getInstance(project)
        settings.loadState(settings.state.copy(pluginConfigs = listOf(CakeThreePluginConfig(
            pluginName = pluginName,
            namespace = namespace,
            pluginPath = pluginPath,
        ))))
    }

    fun `test configured plugin name is not a table alias`() {
        configurePlugin("Renamed", "\\TestPlugin", "vendor/test/test_plugin")
        assertDoesntContain(tableCompletions("Renamed.Ratings"), "pluginRatingsMethod")
    }

    fun `test namespace of a renamed plugin is its table alias`() {
        configurePlugin("Renamed", "\\TestPlugin", "vendor/test/test_plugin")
        assertContainsElements(tableCompletions("TestPlugin.Ratings"), "pluginRatingsMethod")
    }

    fun `test last namespace segment of a vendor plugin is not a table alias`() {
        configurePlugin("", "\\Acme\\Vendored", "vendor/acme/vendored")
        assertDoesntContain(tableCompletions("Vendored.Things"), "vendoredThingsMethod")
    }

    fun `test goto declaration from the last namespace segment of a vendor plugin finds nothing`() {
        configurePlugin("", "\\Acme\\Vendored", "vendor/acme/vendored")
        assertEmpty(gotoTargets("\$this->fetchTable('<caret>Vendored.Things')"))
    }

    fun `test unconfigured vendor plugin resolves through the derived namespace`() {
        val result = tableCompletions("Acme/Vendored.Things")
        assertContainsElements(result, "vendoredThingsMethod")
    }

    fun `test slash in the table part selects a sub namespace`() {
        val result = tableCompletions("TestPlugin.Sub/Nested")
        assertContainsElements(result, "pluginNestedMethod")
    }

    fun `test fully qualified alias has that class type`() {
        val result = tableCompletions("TestPlugin\\Model\\Table\\RatingsTable")
        assertContainsElements(result, "pluginRatingsMethod")
    }

    fun `test empty plugin part means the app table`() {
        val result = tableCompletions(".Ratings")
        assertContainsElements(result, "appRatingsMethod")
        assertDoesntContain(result, "pluginRatingsMethod")
    }

    fun `test empty table part gives only the generic table`() {
        val result = tableCompletions("TestPlugin.")
        assertContainsElements(result, "findAll")
        assertDoesntContain(result, "pluginRatingsMethod", "appRatingsMethod")
    }

    fun `test extra dots give only the generic table`() {
        val result = tableCompletions("TestPlugin.Ratings.Extra")
        assertContainsElements(result, "findAll")
        assertDoesntContain(result, "pluginRatingsMethod", "appRatingsMethod")
    }

    fun `test bare alias of a plugin only table offers generic table methods`() {
        val result = tableCompletions("PluginItems")
        assertContainsElements(result, "findAll")
        assertDoesntContain(result, "pluginItemsMethod")
    }

    fun `test plugin alias of a missing table offers generic table methods`() {
        val result = tableCompletions("TestPlugin.Missing")
        assertContainsElements(result, "findAll")
    }

    fun `test className option accepts plugin notation`() {
        val result = completionsInController("\$this->fetchTable('Foo', ['className' => 'TestPlugin.Ratings'])-><caret>")
        assertContainsElements(result, "pluginRatingsMethod")
    }

    fun `test className option accepts a class constant`() {
        val result = completionsInController("\$this->fetchTable('Foo', ['className' => RatingsTable::class])-><caret>")
        assertContainsElements(result, "pluginRatingsMethod")
    }

    fun `test className option wins over a plugin alias`() {
        val result = completionsInController("\$this->fetchTable('TestPlugin.Ratings', ['className' => 'Ratings'])-><caret>")
        assertContainsElements(result, "appRatingsMethod")
        assertDoesntContain(result, "pluginRatingsMethod")
    }

    fun `test plugin alias in a plugin cell has the plugin table type`() {
        myFixture.configureByFilePathAndText("cake4/vendor/test/test_plugin/src/View/Cell/WidgetCell.php", """
        <?php
        namespace TestPlugin\View\Cell;

        use Cake\View\Cell;

        class WidgetCell extends Cell
        {
            public function display() {
                ${'$'}this->fetchTable('TestPlugin.Ratings')-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "pluginRatingsMethod")
    }

    fun `test bare alias in a plugin cell means the app table`() {
        myFixture.configureByFilePathAndText("cake4/vendor/test/test_plugin/src/View/Cell/WidgetCell.php", """
        <?php
        namespace TestPlugin\View\Cell;

        use Cake\View\Cell;

        class WidgetCell extends Cell
        {
            public function display() {
                ${'$'}this->fetchTable('Ratings')-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings.orEmpty()
        assertContainsElements(result, "appRatingsMethod")
        assertDoesntContain(result, "pluginRatingsMethod")
    }

    private fun gotoTargets(call: String): Set<String> {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                $call;
            }
        }
        """.trimIndent())
        val targets = gotoDeclarationHandlerTargets(TableLocatorGotoDeclarationHandler())
        assertNotNull(targets)
        return targets!!.map { (it as PhpClass).fqn }.toSet()
    }

    fun `test goto declaration from a plugin alias reaches the plugin table`() {
        assertEquals(
            setOf("\\TestPlugin\\Model\\Table\\PluginItemsTable"),
            gotoTargets("\$this->fetchTable('<caret>TestPlugin.PluginItems')")
        )
    }

    fun `test goto declaration from a plugin alias excludes the app table of the same name`() {
        assertEquals(
            setOf("\\TestPlugin\\Model\\Table\\RatingsTable"),
            gotoTargets("\$this->fetchTable('<caret>TestPlugin.Ratings')")
        )
    }

    fun `test goto declaration from a bare alias excludes the plugin table of the same name`() {
        assertEquals(
            setOf("\\App\\Model\\Table\\RatingsTable"),
            gotoTargets("\$this->fetchTable('<caret>Ratings')")
        )
    }

    fun `test goto declaration from a bare alias does not reach a plugin only table`() {
        assertEmpty(gotoTargets("\$this->fetchTable('<caret>PluginItems')"))
    }

    fun `test goto declaration from an unknown plugin finds nothing`() {
        assertEmpty(gotoTargets("\$this->fetchTable('<caret>Unknown.PluginItems')"))
    }

    fun `test goto declaration from a plugin alias of a missing table finds nothing`() {
        assertEmpty(gotoTargets("\$this->fetchTable('<caret>TestPlugin.Missing')"))
    }

    fun `test goto declaration from a getTableLocator get reaches the plugin table`() {
        assertEquals(
            setOf("\\TestPlugin\\Model\\Table\\RatingsTable"),
            gotoTargets("\$this->getTableLocator()->get('<caret>TestPlugin.Ratings')")
        )
    }

    fun `test goto declaration from an alias follows the className option`() {
        assertEquals(
            setOf("\\TestPlugin\\Model\\Table\\RatingsTable"),
            gotoTargets("\$this->fetchTable('<caret>Foo', ['className' => 'TestPlugin.Ratings'])")
        )
    }

    fun `test goto declaration from an unconfigured vendor plugin reaches its table`() {
        assertEquals(
            setOf("\\Acme\\Vendored\\Model\\Table\\ThingsTable"),
            gotoTargets("\$this->fetchTable('<caret>Acme/Vendored.Things')")
        )
    }

    fun `test goto declaration from a plugin cell reaches the plugin table`() {
        myFixture.configureByFilePathAndText("cake4/vendor/test/test_plugin/src/View/Cell/WidgetCell.php", """
        <?php
        namespace TestPlugin\View\Cell;

        use Cake\View\Cell;

        class WidgetCell extends Cell
        {
            public function display() {
                ${'$'}this->fetchTable('<caret>TestPlugin.Ratings');
            }
        }
        """.trimIndent())
        val targets = gotoDeclarationHandlerTargets(TableLocatorGotoDeclarationHandler())
        assertEquals(setOf("\\TestPlugin\\Model\\Table\\RatingsTable"), targets!!.map { (it as PhpClass).fqn }.toSet())
    }

    private fun aliasCompletions(call: String): List<String> =
        completionsInController("\$table = $call;")

    private fun chooseCompletion(lookupString: String) {
        val lookup = LookupManager.getActiveLookup(myFixture.editor) as LookupImpl
        lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == lookupString }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    fun `test completion offers app tables bare and plugin tables prefixed`() {
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertContainsElements(result, "Ratings", "TestPlugin.Ratings", "TestPlugin.PluginItems")
        assertDoesntContain(result, "PluginItems")
    }

    fun `test completion uses a slash for a table in a sub namespace`() {
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertContainsElements(result, "TestPlugin.Sub/Nested")
        assertDoesntContain(result, "Nested")
    }

    fun `test completion leaves out tables in unconfigured namespaces`() {
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertDoesntContain(result, "Things", "Acme/Vendored.Things")
    }

    fun `test completion ignores a configured plugin name`() {
        configurePlugin("Renamed", "\\TestPlugin", "vendor/test/test_plugin")
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertContainsElements(result, "TestPlugin.Ratings")
        assertDoesntContain(result, "Renamed.Ratings")
    }

    fun `test completion uses the full namespace of a vendor plugin with no plugin name`() {
        configurePlugin("", "\\Acme\\Vendored", "vendor/acme/vendored")
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertContainsElements(result, "Acme/Vendored.Things")
        assertDoesntContain(result, "Vendored.Things")
    }

    fun `test completion uses the full namespace of a vendor plugin with a short plugin name`() {
        configurePlugin("Vendored", "\\Acme\\Vendored", "vendor/acme/vendored")
        val result = aliasCompletions("\$this->fetchTable('<caret>')")
        assertContainsElements(result, "Acme/Vendored.Things")
        assertDoesntContain(result, "Vendored.Things")
    }

    fun `test completion after a plugin prefix offers only that plugins tables`() {
        val result = aliasCompletions("\$this->fetchTable('TestPlugin.<caret>')")
        assertContainsElements(result, "TestPlugin.Ratings", "TestPlugin.PluginItems")
        assertDoesntContain(result, "Ratings")
    }

    fun `test choosing a plugin table inserts the full alias`() {
        aliasCompletions("\$this->fetchTable('<caret>')")
        chooseCompletion("TestPlugin.Ratings")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("fetchTable('TestPlugin.Ratings');"))
    }

    fun `test choosing after a plugin prefix does not double the prefix`() {
        aliasCompletions("\$this->fetchTable('TestPlugin.<caret>')")
        chooseCompletion("TestPlugin.Ratings")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("fetchTable('TestPlugin.Ratings');"))
    }

    fun `test choosing with double quotes keeps the quotes`() {
        aliasCompletions("\$this->fetchTable(\"TestPlugin.<caret>\")")
        chooseCompletion("TestPlugin.Ratings")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("fetchTable(\"TestPlugin.Ratings\");"))
    }

    fun `test completion in a plugin cell offers plugin aliases`() {
        myFixture.configureByFilePathAndText("cake4/vendor/test/test_plugin/src/View/Cell/WidgetCell.php", """
        <?php
        namespace TestPlugin\View\Cell;

        use Cake\View\Cell;

        class WidgetCell extends Cell
        {
            public function display() {
                ${'$'}this->fetchTable('<caret>');
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "Ratings", "TestPlugin.PluginItems")
    }
}
