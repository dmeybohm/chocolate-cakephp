package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

abstract class ConfiguredControllerFixtureTestCase : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.addFileToProject("app/Classes.php", """
            <?php
            class AppComponent extends Component {}
            class MovieMetadataComponent extends AppComponent {
                public function generateMetadata() {}
            }
            class UnloadedComponent extends AppComponent {}
            class MyAuthComponent extends AppComponent {}
            class AppModel extends Model {}
            class Movie extends AppModel {}
            class Artist extends AppModel {}
            class User extends AppModel {}
        """.trimIndent())
    }

    protected fun appController(body: String = "") {
        myFixture.addFileToProject("app/Controller/AppController.php", "<?php\nclass AppController extends Controller {\n$body\n}\n")
    }

    protected fun configureController(body: String, action: String) {
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

    protected fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    protected fun completions(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }
}
