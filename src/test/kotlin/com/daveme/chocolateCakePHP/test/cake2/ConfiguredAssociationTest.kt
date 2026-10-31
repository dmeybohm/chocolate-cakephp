package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.controller.ControllerFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass

class ConfiguredAssociationTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles("cake2/vendor/cakephp.php")
        myFixture.addFileToProject("app/Controller/AppController.php", """
            <?php
            class AppController extends Controller {}
        """.trimIndent())
        myFixture.addFileToProject("app/Model/Classes.php", """
            <?php
            class AppModel extends Model {}
            class Movie extends AppModel {
                public ${'$'}belongsTo = array('Director' => array('className' => 'Person'));
                public ${'$'}hasMany = 'Rating';
                public ${'$'}hasAndBelongsToMany = array(
                    'Tag' => array('with' => 'MoviesTag'),
                    'Genre' => array('with' => array('MoviesGenre' => array('genre_id'))),
                );
                public function withCritics() {
                    ${'$'}this->bindModel(array('hasMany' => array('Critic' => array('className' => 'User'))));
                }
            }
            class Remake extends Movie {
                public ${'$'}belongsTo = array('Director');
            }
            class Person extends AppModel {
                public ${'$'}hasMany = array('Studio' => array('className' => 'Company'));
            }
            class Director extends AppModel {}
            class Rating extends AppModel {}
            class Tag extends AppModel {}
            class Genre extends AppModel {}
            class MoviesTag extends AppModel {}
            class MoviesGenre extends AppModel {}
            class User extends AppModel {}
            class Company extends AppModel {}
            class Artist extends AppModel {}
            class Studio extends AppModel {}
            class Review extends AppModel {
                public function reviewMethod() {}
            }
        """.trimIndent())
    }

    private fun configureController(body: String, action: String) {
        myFixture.configureByText("MoviesController.php", """
            <?php
            class MoviesController extends AppController {
                $body
                public function index() {
                    $action
                }
            }
        """.trimIndent())
    }

    private fun configureModel(parent: String, action: String) {
        myFixture.configureByText("Sequel.php", """
            <?php
            class Sequel extends $parent {
                public function probe() {
                    $action
                }
            }
        """.trimIndent())
    }

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    private fun completions(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    fun `test an aliased association resolves inside the model`() {
        configureModel("Movie", "\$this->Direc<caret>tor;")
        assertEquals(setOf("\\Person"), fieldTypeAtCaret())
    }

    fun `test an aliased association resolves through a controller`() {
        configureController("", "\$this->Movie->Direc<caret>tor;")
        assertEquals(setOf("\\Person"), fieldTypeAtCaret())
    }

    fun `test a redeclared association property replaces the parent's`() {
        configureModel("Remake", "\$this->Direc<caret>tor;")
        assertEquals(setOf("\\Director"), fieldTypeAtCaret())
    }

    fun `test a comma-separated association resolves`() {
        configureController("", "\$this->Movie->Rat<caret>ing;")
        assertEquals(setOf("\\Rating"), fieldTypeAtCaret())
    }

    fun `test completion offers only configured associations`() {
        configureController("", "\$this->Movie-><caret>")
        val nested = completions()
        for (alias in listOf("Director", "Rating", "Tag", "Genre", "MoviesTag", "MoviesGenre", "Critic")) {
            assertTrue("Expected $alias in $nested", nested.contains(alias))
        }
        assertFalse(nested.contains("Studio"))
        assertFalse(nested.contains("Review"))

        configureModel("Movie", "\$this-><caret>")
        val inside = completions()
        assertTrue("Expected Director in $inside", inside.contains("Director"))
        assertFalse(inside.contains("Studio"))
    }

    fun `test an externally bound association resolves by name but is not offered`() {
        configureController(
            "",
            "\$this->Movie->bindModel(array('hasMany' => array('Review')));\n\$this->Movie->Revi<caret>ew;"
        )
        assertEquals(setOf("\\Review"), fieldTypeAtCaret())

        configureController("", "\$this->Movie->Review-><caret>")
        assertTrue(completions().contains("reviewMethod"))

        configureController("", "\$this->Movie->Revi<caret>ew;")
        val targets = gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler())
        assertEquals(setOf("Review"), targets.orEmpty().filterIsInstance<PhpClass>().map { it.name }.toSet())
    }

    fun `test an in-model bindModel resolves through its className`() {
        configureModel("Movie", "\$this->Cri<caret>tic;")
        assertEquals(setOf("\\User"), fieldTypeAtCaret())
    }

    fun `test with join models resolve in both forms`() {
        configureModel("Movie", "\$this->Movies<caret>Tag;")
        assertEquals(setOf("\\MoviesTag"), fieldTypeAtCaret())

        configureController("", "\$this->Movie->Movies<caret>Genre;")
        assertEquals(setOf("\\MoviesGenre"), fieldTypeAtCaret())
    }

    fun `test a chain resolves each link against the aliased class`() {
        configureController("", "\$this->Movie->Director->Stu<caret>dio;")
        assertEquals(setOf("\\Company"), fieldTypeAtCaret())
    }

    // In a parent class, the method's return type only reaches the field as a chained signature:
    private fun configureControllerWithFilmMethod(returnType: String) {
        myFixture.addFileToProject("app/Controller/FilmsBaseController.php", """
            <?php
            class FilmsBaseController extends AppController {
                /** @return $returnType */
                public function film() {}
            }
        """.trimIndent())
        myFixture.configureByText("MoviesController.php", """
            <?php
            class MoviesController extends FilmsBaseController {
                public function index() {
                    ${'$'}this->film()->Direc<caret>tor;
                }
            }
        """.trimIndent())
    }

    fun `test receivers that are all models union their types`() {
        configureControllerWithFilmMethod("Movie|Remake")
        assertEquals(setOf("\\Person", "\\Director"), fieldTypeAtCaret())
    }

    fun `test a receiver that is also another class keeps that class's field type`() {
        myFixture.addFileToProject("app/Lib/Plain.php", """
            <?php
            class Plain {
                /** @var Review */
                public ${'$'}Director;
            }
        """.trimIndent())
        configureControllerWithFilmMethod("Movie|Plain")
        val types = fieldTypeAtCaret()
        assertTrue("Expected \\Review in $types", types.contains("\\Review"))
    }

    fun `test a model the controller does not load has no nested type or completion`() {
        configureController("public \$uses = array('Artist');", "\$this->Movie->Direc<caret>tor;")
        assertEquals(emptySet<String>(), fieldTypeAtCaret())

        configureController("public \$uses = array('Artist');", "\$this->Movie-><caret>")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings.orEmpty()
        assertFalse(result.contains("Director"))
    }

    fun `test goto on an aliased association goes to its className`() {
        configureController("", "\$this->Movie->Direc<caret>tor;")
        val targets = gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler())
        assertEquals(setOf("Person"), targets.orEmpty().filterIsInstance<PhpClass>().map { it.name }.toSet())
    }
}
