package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class ViewVariableIndex : FileBasedIndexExtension<ViewVariablesKey, ViewVariablesWithRawVars>() {

    override fun getName() = VIEW_VARIABLE_INDEX_KEY

    override fun getIndexer(): DataIndexer<ViewVariablesKey, ViewVariablesWithRawVars, FileContent> =
        ViewVariableASTDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<ViewVariablesWithRawVars> =
        ViewVariableRawVarsExternalizer

    override fun getVersion(): Int {
        return 17
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
