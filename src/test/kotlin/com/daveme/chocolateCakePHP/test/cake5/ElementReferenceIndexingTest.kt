package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.elementDataKey
import com.intellij.util.indexing.FileContentImpl

class ElementReferenceIndexingTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test element calls are indexed without an element directory`() {
        val caller = myFixture.addFileToProject("cake5/templates/Movie/caller.php", """
            <?php
            echo ${'$'}this->element('nav', ['x' => ${'$'}y]);
        """.trimIndent()).virtualFile
        assertNull(caller.parent.parent.findChild("element"))

        val content = FileContentImpl.createByFile(caller, project)
        assertTrue(ViewFileDataIndexer.map(content).containsKey("element/nav"))
        assertTrue(ViewVariableASTDataIndexer.map(content).containsKey(elementDataKey("element/nav")))
    }
}
