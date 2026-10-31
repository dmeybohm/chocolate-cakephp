package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

abstract class HelperFixtureTestCase : Cake2BaseTestCase() {

    protected fun addHelpers(vararg htmlHelperSubclasses: String) {
        val subclasses = htmlHelperSubclasses.joinToString("\n") { "class $it extends HtmlHelper {}" }
        myFixture.addFileToProject(
            "cake2/app/View/Helper/Helpers.php",
            "<?php\nclass HtmlHelper {}\n$subclasses\n"
        )
    }

    protected fun addController(name: String, parent: String, body: String) {
        myFixture.addFileToProject("cake2/app/Controller/$name.php", "<?php\nclass $name extends $parent {\n$body\n}\n")
    }

    protected fun addPluginController(name: String, parent: String, body: String) {
        myFixture.addFileToProject(
            "cake2/app/Plugin/Foo/Controller/$name.php",
            "<?php\nclass $name extends $parent {\n$body\n}\n"
        )
    }

    protected fun htmlAlias(className: String): String =
        "public \$helpers = array('Html' => array('className' => '$className'));\n"

    protected fun htmlTypeIn(path: String): Set<String> {
        val code = "<?php\n\$this->Html;\n"
        myFixture.addFileToProject(path, code)
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Html") + 1)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }
}
