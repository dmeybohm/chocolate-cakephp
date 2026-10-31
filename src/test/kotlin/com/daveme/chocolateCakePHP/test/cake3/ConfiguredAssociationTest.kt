package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.io.File

class ConfiguredAssociationTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ReviewsTable.php",
            "cake3/src/Model/Table/UsersTable.php",
            "cake3/src/Model/Table/AuthorsTable.php",
            "cake3/src/Model/Table/CommentsTable.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Table/RatingsTable.php",
            "cake3/vendor/test/test_plugin/src/Model/Table/RatingsTable.php",
            "cake3/vendor/cakephp.php",
        )
    }

    private fun configureReviewsTable(body: String) {
        val path = "cake3/src/Model/Table/ReviewsTable.php"
        val original = File("src/test/fixtures/$path").readText().trimEnd()
        val text = original.removeSuffix("}") +
                "\n    public function probe()\n    {\n        $body\n    }\n}\n"
        myFixture.configureByFilePathAndText(path, text)
    }

    private fun configureController(body: String) {
        myFixture.configureByText("ReviewController.php", """
        <?php
        namespace App\Controller;

        class ReviewController extends AppController
        {
            public function index() {
                $body
            }
        }
        """.trimIndent())
    }

    private fun tableTypesAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") && it.endsWith("Table") }
            .toSet()
    }

    private fun completions(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    fun `test aliased association inside the table resolves to the class name`() {
        configureReviewsTable("\$this->Auth<caret>ors;")
        assertEquals(setOf("\\App\\Model\\Table\\UsersTable"), tableTypesAtCaret())
    }

    fun `test plugin prefixed association resolves to the plugin table only`() {
        configureReviewsTable("\$this->Rat<caret>ings;")
        assertEquals(setOf("\\TestPlugin\\Model\\Table\\RatingsTable"), tableTypesAtCaret())
    }

    fun `test addAssociations aliased leaf resolves to the class name`() {
        configureReviewsTable("\$this->Edit<caret>ors;")
        assertEquals(setOf("\\App\\Model\\Table\\UsersTable"), tableTypesAtCaret())
    }

    fun `test addAssociations plain leaf resolves by name`() {
        configureReviewsTable("\$this->Comm<caret>ents;")
        assertEquals(setOf("\\App\\Model\\Table\\CommentsTable"), tableTypesAtCaret())
    }

    fun `test unconfigured association inside a table falls back to convention`() {
        configureReviewsTable("\$this->Artic<caret>les;")
        assertEquals(setOf("\\App\\Model\\Table\\ArticlesTable"), tableTypesAtCaret())
    }

    fun `test association on fetchTable resolves to the class name`() {
        configureController("\$this->fetchTable('Reviews')->Authors-><caret>")
        val result = completions()
        assertTrue(result.contains("usersTableMethod"))
    }

    fun `test association on a variable from fetchTable resolves to the class name`() {
        configureController("\$reviews = \$this->fetchTable('Reviews');\n\$reviews->Ratings-><caret>")
        val result = completions()
        assertTrue(result.contains("pluginRatingsMethod"))
        assertFalse(result.contains("appRatingsMethod"))
    }

    fun `test association on a controller table property resolves to the class name`() {
        configureController("\$this->Reviews->Editors-><caret>")
        val result = completions()
        assertTrue(result.contains("usersTableMethod"))
    }

    fun `test nested completion offers only the configured associations`() {
        configureController("\$this->Reviews-><caret>")
        val result = completions()
        for (alias in listOf("Movies", "Authors", "Ratings", "Editors", "Comments")) {
            assertTrue("Expected $alias in $result", result.contains(alias))
        }
        assertFalse(result.contains("Articles"))
    }

    fun `test goto declaration on an association inside the table`() {
        configureReviewsTable("\$this->Auth<caret>ors;")
        val targets = gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("\\App\\Model\\Table\\UsersTable"), targets!!.map { (it as PhpClass).fqn })
    }

    fun `test association chain walks one configured alias at a time`() {
        configureController("\$this->Reviews->Authors-><caret>")
        assertTrue(completions().contains("usersTableMethod"))
    }

    fun `test longer association chain resolves through the fallback hop`() {
        configureController("\$this->Movies->Reviews->Authors-><caret>")
        assertTrue(completions().contains("usersTableMethod"))
    }

    private fun queryTypesAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val method = PsiTreeUtil.getParentOfType(element, MethodReference::class.java)!!
        return method.type.lookupCompleteType(project, null).types
    }

    fun `test query from an association chain carries the table`() {
        configureController("\$this->Movies->Reviews->fi<caret>nd();")
        assertContainsElements(queryTypesAtCaret(), QUERY_ON_REVIEWS)
    }

    fun `test query method chained after an association chain keeps the table`() {
        configureController("\$this->Movies->Reviews->find()->wh<caret>ere([]);")
        assertContainsElements(queryTypesAtCaret(), QUERY_ON_REVIEWS)
    }

    fun `test query from an association of this inside a table carries the table`() {
        configureReviewsTable("\$this->Authors->fi<caret>nd();")
        assertContainsElements(queryTypesAtCaret(), QUERY_ON_USERS)
    }

    private companion object {
        const val QUERY_ON_USERS = "\\Best\\ChocolateCakePHP\\QueryBuilder\\App\\Model\\Table\\UsersTable"
        const val QUERY_ON_REVIEWS = "\\Best\\ChocolateCakePHP\\QueryBuilder\\App\\Model\\Table\\ReviewsTable"
    }
}
