package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.elementDataKey
import com.intellij.util.indexing.FileContentImpl

class ElementReferenceIndexingTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test element calls are indexed without an element directory`() {
        val caller = myFixture.addFileToProject("cake3/src/Template/Movie/caller.ctp", """
            <?php
            echo ${'$'}this->element('nav', ['x' => ${'$'}y]);
        """.trimIndent()).virtualFile
        assertNull(caller.parent.parent.findChild("Element"))

        val content = FileContentImpl.createByFile(caller, project)
        assertTrue(ViewFileDataIndexer.map(content).containsKey("Element/nav"))
        assertTrue(ViewVariableASTDataIndexer.map(content).containsKey(elementDataKey("Element/nav")))
    }
}
