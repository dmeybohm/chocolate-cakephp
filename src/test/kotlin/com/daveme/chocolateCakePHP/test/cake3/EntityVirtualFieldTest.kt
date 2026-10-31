package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.entity.EntityVirtualFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.inspections.PhpUndefinedFieldInspection
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Method
import java.io.File

class EntityVirtualFieldTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/MovieController.php",
            "cake3/src/Model/Entity/BaseMovie.php",
            "cake3/src/Model/Entity/MovieAccessorsTrait.php",
            "cake3/src/Model/Entity/Movie.php",
            "cake3/src/Model/Entity/Rating.php",
            "cake3/src/Model/Table/MoviesTable.php",
        )
    }

    private fun configureController(members: String) {
        val path = "cake3/src/Controller/MovieController.php"
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
        configureAction("probe(\$id)", "\\Cake\\ORM\\TableRegistry::getTableLocator()->get('Movies')->get(\$id)->full<caret>_title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test getter type on a table member's entity`() {
        configureAction("probe(\$id)", "\$this->Movies->get(\$id)->full<caret>_title;")
        assertEquals(setOf("\\string"), fieldTypeAtCaret())
    }

    fun `test getter type on a view variable`() {
        configureAction("probe()", "\$this->set('movie', \\Cake\\ORM\\TableRegistry::getTableLocator()->get('Movies')->get(1));")
        val template = myFixture.addFileToProject("cake3/src/Template/Movie/probe.ctp", "<?php\n\$movie->full<caret>_title;\n")
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
            "cake3/src/Model/Entity/ChildMovie.php",
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
        configureAction("probe(\$id)", "\\Cake\\ORM\\TableRegistry::getTableLocator()->get('Movies')->get(\$id)-><caret>id;")
        assertEquals(setOf("\\mixed"), fieldTypeAtCaret())
    }

    fun `test accessor shaped method on a table is not a field`() {
        configureAction("probe(\\App\\Model\\Table\\MoviesTable \$table)", "\$table->la<caret>bel;")
        assertFalse("\\string" in fieldTypeAtCaret())
    }

    private fun typeTextOf(lookupString: String): String? {
        val element = myFixture.lookupElements!!.first { it.lookupString == lookupString }
        val presentation = LookupElementPresentation()
        element.renderElement(presentation)
        return presentation.typeText
    }

    private fun completeStrings(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings!!
    }

    private fun gotoMethodNames(): Set<String> =
        gotoDeclarationHandlerTargets(EntityVirtualFieldGotoDeclarationHandler())!!
            .map { (it as Method).name }
            .toSet()

    fun `test completion offers accessor fields in snake case`() {
        configureMovieParameter("\$movie-><caret>;")
        val result = completeStrings()
        assertContainsElements(result, "full_title", "runtime_hours", "title", "slug", "inherited_label", "trait_count")
        assertDoesntContain(result, "fullTitle", "FullTitle", "secret")
        assertEquals("string", typeTextOf("full_title"))
        assertEquals("float", typeTextOf("runtime_hours"))
    }

    fun `test completion does not list a declared field twice`() {
        configureMovieParameter("\$movie-><caret>;")
        assertEquals(1, completeStrings().count { it == "rating_count" })
        assertEquals("int", typeTextOf("rating_count"))
    }

    fun `test completion ignores accessor shaped methods on a table`() {
        configureAction("probe(\\App\\Model\\Table\\MoviesTable \$table)", "\$table-><caret>;")
        assertDoesntContain(completeStrings(), "label")
    }

    fun `test completion inside the entity`() {
        val entity = myFixture.addFileToProject(
            "cake3/src/Model/Entity/ChildMovie.php",
            "<?php\nnamespace App\\Model\\Entity;\nclass ChildMovie extends Movie {\n" +
                "    public function probe() { return \$this-><caret>; }\n}\n"
        )
        myFixture.configureFromExistingVirtualFile(entity.virtualFile)
        assertContainsElements(completeStrings(), "full_title")
    }

    fun `test completion and navigation on a union work per entity class`() {
        for (other in listOf("\\App\\Model\\Entity\\Rating", "\\App\\Model\\Table\\MoviesTable")) {
            val declaration = "/** @var \\App\\Model\\Entity\\Movie|$other \$either */\n\$either = make();\n"
            configureAction("probe()", "$declaration\$either-><caret>;")
            val result = completeStrings()
            assertContainsElements(result, "full_title")
            assertDoesntContain(result, "label")
            assertEquals("string", typeTextOf("full_title"))

            configureAction("probe()", "$declaration\$either->full<caret>_title;")
            assertEquals(setOf("_getFullTitle"), gotoMethodNames())
        }
    }

    fun `test goto declaration reaches the getter`() {
        configureMovieParameter("\$movie->full<caret>_title;")
        assertEquals(setOf("_getFullTitle"), gotoMethodNames())
    }

    fun `test goto declaration reaches the setter of an assignment`() {
        configureMovieParameter("\$movie->ti<caret>tle = 'x';")
        assertEquals(setOf("_setTitle"), gotoMethodNames())
    }

    fun `test goto declaration offers both accessors`() {
        configureMovieParameter("\$movie->sl<caret>ug;")
        assertEquals(setOf("_getSlug", "_setSlug"), gotoMethodNames())
    }

    fun `test goto declaration leaves other fields alone`() {
        configureMovieParameter("\$movie->not<caret>_a_field;")
        assertEmpty(gotoDeclarationHandlerTargets(EntityVirtualFieldGotoDeclarationHandler())!!)
    }

    fun `test magic field warning is only left on fields without an accessor`() {
        myFixture.enableInspections(PhpUndefinedFieldInspection::class.java)
        configureMovieParameter("\$movie->full_title;\n\$movie->title = 'x';\n\$movie->not_a_field;")
        val warned = myFixture.doHighlighting()
            .filter { it.description == "Property accessed via magic method" }
            .map { it.text }
        assertContainsElements(warned, "not_a_field")
        assertDoesntContain(warned, "full_title", "title")
    }
}
