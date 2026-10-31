package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import java.io.File

class EntityVirtualFieldTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/Model/Entity/BaseMovie.php",
            "cake5/src5/Model/Entity/MovieAccessorsTrait.php",
            "cake5/src5/Model/Entity/Movie.php",
            "cake5/src5/Model/Entity/Rating.php",
            "cake5/src5/Model/Table/MoviesTable.php",
        )
    }

    private fun configureController(members: String) {
        val path = "cake5/src5/Controller/MovieController.php"
        val original = File("src/test/fixtures/$path").readText().trimEnd()
        myFixture.configureByFilePathAndText(path, original.removeSuffix("}") + "\n$members\n}\n")
    }

    private fun configureAction(signature: String, body: String) =
        configureController("    public function $signature\n    {\n        $body\n    }")

    private fun configureMovieParameter(body: String) =
        configureAction("probe(\\App\\Model\\Entity\\Movie \$movie)", body)

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test getter type on a parameter`() {
        configureMovieParameter("\$movie->full<caret>_title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test getter type on a fetched table's entity`() {
        configureAction("probe(\$id)", "\$this->fetchTable('Movies')->get(\$id)->full<caret>_title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test getter type on a table member's entity`() {
        configureAction("probe(\$id)", "\$this->Movies->get(\$id)->full<caret>_title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test getter type on a view variable`() {
        configureAction("probe()", "\$this->set('movie', \$this->fetchTable('Movies')->get(1));")
        val template = myFixture.addFileToProject("cake5/templates/Movie/probe.php", "<?php\n\$movie->full<caret>_title;\n")
        myFixture.configureFromExistingVirtualFile(template.virtualFile)
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test return tag gives the type`() {
        configureMovieParameter("\$movie->runtime<caret>_hours;")
        assertEquals(setOf("\\float"), fieldTypeAtCaret())
    }

    fun `test camel case spelling resolves`() {
        configureMovieParameter("\$movie->full<caret>Title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test accessor from a parent entity`() {
        configureMovieParameter("\$movie->inherited<caret>_label;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test accessor from a trait`() {
        configureMovieParameter("\$movie->trait<caret>_count;")
        assertEquals(setOf("\\int"), fieldTypeAtCaret())
    }

    fun `test property tag keeps its declared type`() {
        configureMovieParameter("\$movie->rating<caret>_count;")
        assertEquals(setOf("\\int"), fieldTypeAtCaret())
    }

    fun `test setter only field keeps native resolution`() {
        configureMovieParameter("\$movie->ti<caret>tle;")
        assertEquals(setOf("\\mixed"), fieldTypeAtCaret())
    }

    fun `test private getter is not an accessor`() {
        configureMovieParameter("\$movie->sec<caret>ret;")
        assertEquals(setOf("\\mixed"), fieldTypeAtCaret())
    }

    fun `test inside the entity`() {
        val entity = myFixture.addFileToProject(
            "cake5/src5/Model/Entity/ChildMovie.php",
            "<?php\nnamespace App\\Model\\Entity;\nclass ChildMovie extends Movie {\n" +
                "    public function probe() { return \$this->full<caret>_title; }\n}\n"
        )
        myFixture.configureFromExistingVirtualFile(entity.virtualFile)
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test union with an entity lacking the getter keeps that branch's native type`() {
        configureAction(
            "probe()",
            "/** @var \\App\\Model\\Entity\\Movie|\\App\\Model\\Entity\\Rating \$either */\n\$either = make();\n\$either->full<caret>_title;"
        )
        assertEquals(setOf("\\string", "\\mixed"), fieldTypeAtCaret())
    }

    fun `test union with a non-entity keeps that branch's native type`() {
        configureAction(
            "probe()",
            "/** @var \\App\\Model\\Entity\\Movie|\\App\\Model\\Table\\MoviesTable \$either */\n\$either = make();\n\$either->full<caret>_title;"
        )
        assertEquals(setOf("\\string", "\\Cake\\ORM\\Association"), fieldTypeAtCaret())
    }

    fun `test field declared by the framework's entity interface keeps native resolution`() {
        configureAction("probe(\$id)", "\$this->fetchTable('Movies')->get(\$id)-><caret>id;")
        assertEquals(setOf("\\mixed"), fieldTypeAtCaret())
    }

    fun `test accessor shaped method on a table is not a field`() {
        configureAction("probe(\\App\\Model\\Table\\MoviesTable \$table)", "\$table->la<caret>bel;")
        assertFalse("\\string" in fieldTypeAtCaret())
    }
}
