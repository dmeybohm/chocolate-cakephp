package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.configuredmemberindex.AssociationType
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.entity.EntityAssociations
import com.daveme.chocolateCakePHP.entity.EntityVirtualFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.io.File

class EntityAssociationFieldTest : Cake3BaseTestCase() {

    private val entities = "\\App\\Model\\Entity"

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/MovieController.php",
            "cake3/src/Model/Entity/BaseMovie.php",
            "cake3/src/Model/Entity/MovieAccessorsTrait.php",
            "cake3/src/Model/Entity/Movie.php",
            "cake3/src/Model/Entity/Review.php",
            "cake3/src/Model/Entity/User.php",
            "cake3/src/Model/Entity/Profile.php",
            "cake3/src/Model/Entity/Post.php",
            "cake3/src/Model/Entity/Critique.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ReviewsTable.php",
            "cake3/src/Model/Table/UsersTable.php",
            "cake3/src/Model/Table/ProfilesTable.php",
            "cake3/src/Model/Table/CommentsTable.php",
            "cake3/src/Model/Table/PostsTable.php",
            "cake3/src/Model/Table/CritiquesTable.php",
            "cake3/vendor/test/test_plugin/src/Model/Table/RatingsTable.php",
            "cake3/vendor/test/test_plugin/src/Model/Entity/Rating.php",
        )
    }

    private fun configureAction(signature: String, body: String) {
        val path = "cake3/src/Controller/MovieController.php"
        val original = File("src/test/fixtures/$path").readText().trimEnd()
        myFixture.configureByFilePathAndText(
            path,
            original.removeSuffix("}") + "\n    public function $signature\n    {\n        $body\n    }\n}\n"
        )
    }

    private fun configureEntityParameter(entity: String, body: String) =
        configureAction("probe($entities\\$entity \$entity)", body)

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    private fun assertFieldType(entity: String, field: String, vararg expected: String) {
        val caretAt = field.length / 2
        configureEntityParameter(entity, "\$entity->${field.substring(0, caretAt)}<caret>${field.substring(caretAt)};")
        assertEquals(expected.toSet(), fieldTypeAtCaret())
    }

    private fun completeStrings(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings!!
    }

    private fun typeTextOf(lookupString: String): String? {
        val element = myFixture.lookupElements!!.single { it.lookupString == lookupString }
        val presentation = LookupElementPresentation()
        element.renderElement(presentation)
        return presentation.typeText
    }

    fun `test belongsTo is singular and nullable`() {
        assertFieldType("Review", "movie", "$entities\\Movie", "\\null")
    }

    fun `test alias is not the class`() {
        assertFieldType("Review", "author", "$entities\\User", "\\null")
    }

    fun `test addAssociations works like a direct call`() {
        assertFieldType("Review", "editor", "$entities\\User", "\\null")
    }

    fun `test missing entity falls back to the framework entity`() {
        assertFieldType("Review", "comments", "\\Cake\\ORM\\Entity[]")
    }

    fun `test plugin target`() {
        assertFieldType("Review", "ratings", "\\TestPlugin\\Model\\Entity\\Rating[]")
    }

    fun `test hasMany is an array`() {
        assertFieldType("User", "reviews", "$entities\\Review[]")
    }

    fun `test hasOne is singular and nullable`() {
        assertFieldType("User", "profile", "$entities\\Profile", "\\null")
    }

    fun `test propertyName option names the property`() {
        assertFieldType("Post", "written", "$entities\\Review[]")
        assertFieldType("Post", "reviews", "\\mixed")
    }

    fun `test unreadable propertyName gives no property`() {
        assertFieldType("Post", "comments", "\\mixed")
        assertFieldType("Post", "remarks", "\\mixed")
    }

    fun `test method call receiver`() {
        configureAction("probe(\$id)", "\\Cake\\ORM\\TableRegistry::getTableLocator()->get('Reviews')->get(\$id)->au<caret>thor;")
        assertEquals(setOf("$entities\\User", "\\null"), fieldTypeAtCaret())
    }

    fun `test array element of a to-many property`() {
        configureEntityParameter("User", "\$entity->reviews[0]->au<caret>thor;")
        assertEquals(setOf("$entities\\User", "\\null"), fieldTypeAtCaret())
    }

    fun `test accessor wins over the association`() {
        assertFieldType("Critique", "author", "\\string")
        assertFieldType("Critique", "movie", "$entities\\Movie", "\\null")

        val authors = ConfiguredMemberService.associationLoads(project, listOf("\\App\\Model\\Table\\CritiquesTable"))
            .map { it.load }
            .single { it.alias == "Authors" }
        assertEquals(AssociationType.BELONGS_TO, authors.associationType)
        assertNull(authors.propertyName)
        assertEquals("author", EntityAssociations.propertyName(authors))
        assertEquals(
            listOf("\\App\\Model\\Table\\UsersTable"),
            ConfiguredMemberService.loadClasses(project, authors).map { it.fqn }
        )

        configureEntityParameter("Critique", "\$entity-><caret>;")
        val result = completeStrings()
        assertEquals(1, result.count { it == "author" })
        assertEquals("string", typeTextOf("author"))
    }

    fun `test completion offers association properties`() {
        configureEntityParameter("Review", "\$entity-><caret>;")
        val result = completeStrings()
        assertContainsElements(result, "movie", "author", "editor", "comments", "ratings")
        assertDoesntContain(result, "authors", "Authors")
        assertTrue(typeTextOf("author")!!.contains("User"))
    }

    fun `test completion on an entity whose table configures none`() {
        configureEntityParameter("Movie", "\$entity-><caret>;")
        val result = completeStrings()
        assertContainsElements(result, "full_title")
        assertDoesntContain(result, "reviews", "author")
    }

    fun `test goto declaration reaches the call and the entity`() {
        configureEntityParameter("Review", "\$entity->au<caret>thor;")
        val targets = gotoDeclarationHandlerTargets(EntityVirtualFieldGotoDeclarationHandler())!!
        assertEquals(listOf("$entities\\User"), targets.filterIsInstance<PhpClass>().map { it.fqn })
        val call = targets.single { it !is PhpClass }
        assertEquals("ReviewsTable.php", call.containingFile.name)
        assertTrue(call.text, call.parent.text.contains("'Authors'"))
    }

    fun `test goto declaration reaches the addAssociations entry`() {
        configureEntityParameter("Review", "\$entity->edi<caret>tor;")
        val targets = gotoDeclarationHandlerTargets(EntityVirtualFieldGotoDeclarationHandler())!!
        val entry = targets.single { it !is PhpClass }
        assertEquals("ReviewsTable.php", entry.containingFile.name)
        assertTrue(entry.text, entry.parent.text.contains("'Editors'"))
    }
}
