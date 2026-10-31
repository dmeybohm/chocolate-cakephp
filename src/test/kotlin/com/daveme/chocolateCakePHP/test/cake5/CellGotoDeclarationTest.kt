package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.CellGotoDeclarationHandler
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class CellGotoDeclarationTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
            "cake5/src5/View/Cell/Admin/ReportCell.php",
            "cake5/vendor/test/test_plugin/src/View/Cell/WidgetCell.php",
            "cake5/templates/Movie/artist.php",
        )
    }

    private fun targets(): List<Any> =
        gotoDeclarationHandlerTargets(CellGotoDeclarationHandler())?.toList() ?: emptyList()

    private fun assertGoesToClass(fqn: String) {
        val classes = targets().filterIsInstance<PhpClass>()
        assertEquals(listOf(fqn), classes.map { it.fqn })
    }

    private fun assertGoesToMethod(classFqn: String, methodName: String) {
        val methods = targets().filterIsInstance<Method>()
        assertEquals(1, methods.size)
        assertEquals(methodName, methods[0].name)
        assertEquals(classFqn, methods[0].containingClass?.fqn)
    }

    fun `test cell name goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('<caret>Inventory');
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\InventoryCell")
    }

    fun `test cell name with action goes to the action method`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory::<caret>compact');
        """.trimIndent())
        assertGoesToMethod("\\App\\View\\Cell\\InventoryCell", "compact")
    }

    fun `test cell name with a missing action goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory::<caret>missing');
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\InventoryCell")
    }

    fun `test plugin cell name goes to the plugin cell class`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('TestPlugin.<caret>Widget');
        """.trimIndent())
        assertGoesToClass("\\TestPlugin\\View\\Cell\\WidgetCell")
    }

    fun `test sub-namespace cell name goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Admin/<caret>Report');
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\Admin\\ReportCell")
    }

    fun `test cell call in a controller using CellTrait goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\View\CellTrait;

        class MovieController extends Controller
        {
            use CellTrait;

            public function index() {
                ${'$'}cell = ${'$'}this->cell('<caret>Inventory');
            }
        }
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\InventoryCell")
    }

    fun `test cell call through the view in a helper goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        use Cake\View\Helper;

        class MovieFormatterHelper extends Helper
        {
            public function show() {
                return ${'$'}this->_View->cell('<caret>Inventory');
            }
        }
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\InventoryCell")
    }

    fun `test cell call on another object does not navigate`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        echo ${'$'}obj->cell('<caret>Inventory');
        """.trimIndent())
        assertEmpty(targets())
    }

    fun `test cell call in a class with its own cell method does not navigate`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function cell(string ${'$'}name) {}

            public function index() {
                ${'$'}this->cell('<caret>Inventory');
            }
        }
        """.trimIndent())
        assertEmpty(targets())
    }

    fun `test goto declaration on false branch of ternary in cell`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell(${'$'}admin ? 'Inventory' : '<caret>Admin/Report');
        """.trimIndent())
        assertGoesToClass("\\App\\View\\Cell\\Admin\\ReportCell")
    }

}
