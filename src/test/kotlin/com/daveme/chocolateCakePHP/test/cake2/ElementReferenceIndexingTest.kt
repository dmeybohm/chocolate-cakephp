package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.elementDataKey
import com.intellij.util.indexing.FileContentImpl

class ElementReferenceIndexingTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test element calls are indexed without an element directory`() {
        val caller = myFixture.addFileToProject("cake2/app/View/Movie/caller.ctp", """
            <?php
            echo ${'$'}this->element('nav', ['x' => ${'$'}y]);
        """.trimIndent()).virtualFile
        assertNull(caller.parent.parent.findChild("Elements"))

        val content = FileContentImpl.createByFile(caller, project)
        assertTrue(ViewFileDataIndexer.map(content).containsKey("Elements/nav"))
        assertTrue(ViewVariableASTDataIndexer.map(content).containsKey(elementDataKey("Elements/nav")))
    }
}
