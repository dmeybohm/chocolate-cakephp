package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.model.CustomFinderGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.jetbrains.php.lang.psi.elements.Method

class BehaviorFinderTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Model/Behavior/AuditBehavior.php",
            "cake5/src5/Model/Behavior/CounterCacheBehavior.php",
            "cake5/src5/Model/Behavior/FindersMappedBehavior.php",
            "cake5/src5/Model/Behavior/SluggableBehavior.php",
            "cake5/src5/Model/Table/ChildPagesTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/src5/Model/Table/PagesTable.php",
            "cake5/src5/Model/Table/RepeatPagesTable.php",
        )
    }

    private fun configureAction(body: String) {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                $body
            }
        }
        """.trimIndent())
    }

    private fun completeFinders(body: String): List<String> {
        configureAction(body)
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    private fun tailText(lookupString: String): String? {
        val element = myFixture.lookupElements!!.single { it.lookupString == lookupString }
        val presentation = LookupElementPresentation()
        element.renderElement(presentation)
        return presentation.tailText
    }

    private fun finderTargets(body: String): List<String> {
        configureAction(body)
        return gotoDeclarationHandlerTargets(CustomFinderGotoDeclarationHandler())!!
            .map { it as Method }
            .map { "${it.containingClass!!.name}::${it.name}" }
    }

    fun `test find offers the finders of the attached behaviors`() {
        val result = completeFinders("\$this->Pages->find('<caret>');")

        assertContainsElements(result, "slugged", "audited", "children", "path", "treeList")
        assertEquals(1, result.count { it == "slugged" })
        assertDoesntContain(result, "latest", "oldest", "recent")
        assertEquals(" (AuditBehavior)", tailText("audited"))
        assertEquals(" (TreeBehavior)", tailText("children"))
    }

    fun `test a behavior finder navigates to the behavior method`() {
        assertEquals(
            listOf("AuditBehavior::findAudited"),
            finderTargets("\$this->Pages->find('aud<caret>ited');")
        )
    }

    fun `test a table finder shadows the behavior finder of the same name`() {
        assertEquals(
            listOf("PagesTable::findSlugged"),
            finderTargets("\$this->Pages->find('slug<caret>ged');")
        )
    }

    fun `test a core behavior finder navigates to the core class`() {
        assertEquals(
            listOf("TreeBehavior::findTreeList"),
            finderTargets("\$this->Pages->find('tree<caret>List');")
        )
    }

    fun `test find on a query offers the behavior finders`() {
        val result = completeFinders("\$this->Pages->find()->find('<caret>');")

        assertContainsElements(result, "slugged", "audited", "children", "path", "treeList")
    }

    fun `test a child table offers its parent's behavior finders`() {
        val result = completeFinders("\$this->ChildPages->find('<caret>');")

        assertContainsElements(result, "slugged", "audited", "children", "path", "treeList")
    }

    fun `test a table without behaviors offers only its own finders`() {
        val result = completeFinders("\$this->Movies->find('<caret>');")

        assertContainsElements(result, "ownedBy")
        assertDoesntContain(result, "audited", "children", "path", "treeList", "slugged")
    }

    fun `test a half-mapped behavior offers only its mapped finders`() {
        val result = completeFinders("\$this->RepeatPages->find('<caret>');")

        assertContainsElements(result, "recent", "audited", "slugged", "children")
        assertDoesntContain(result, "latest", "oldest")
    }

    fun `test a dotted alias with a className is a separate behavior`() {
        myFixture.addFileToProject("cake5/src5/Model/Table/AliasedPagesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Table;

        class AliasedPagesTable extends Table
        {
            public function initialize(array ${'$'}config): void
            {
                ${'$'}this->addBehavior('Audit');
                ${'$'}this->addBehavior('Probe.Audit', ['className' => 'Sluggable']);
            }
        }
        """.trimIndent())

        val result = completeFinders("\$this->AliasedPages->find('<caret>');")

        assertContainsElements(result, "audited", "slugged")
    }
}
