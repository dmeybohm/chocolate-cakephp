package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import kotlin.random.Random

class ViewVariableIndex : FileBasedIndexExtension<ViewVariablesKey, ViewVariables>() {

    override fun getName() = VIEW_VARIABLE_INDEX_KEY

    override fun getIndexer(): DataIndexer<ViewVariablesKey, ViewVariables, FileContent> =
        ViewVariableDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<ViewVariables> =
        ViewVariableDataExternalizer

    override fun getVersion(): Int {
        return Random.nextInt(1, Int.MAX_VALUE)
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
