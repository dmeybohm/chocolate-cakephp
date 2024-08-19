package com.daveme.chocolateCakePHP.view.elementvariableindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class ElementVariableIndex : FileBasedIndexExtension<ElementVariablesKey, ElementVariables>() {

    override fun getName() = ELEMENT_VARIABLE_INDEX_KEY

    override fun getIndexer(): DataIndexer<ElementVariablesKey, ElementVariables, FileContent> =
        ElementVariableDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<ElementVariables> =
        ElementVariableDataExternalizer

    override fun getVersion(): Int {
        return 13
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            val isEqual = file.fileType.name == "PHP"
            isEqual
        }
    }

    override fun dependsOnFileContent(): Boolean {
        return true
    }

}
