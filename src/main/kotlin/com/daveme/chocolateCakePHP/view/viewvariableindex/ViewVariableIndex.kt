package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class ViewVariableIndex : FileBasedIndexExtension<String, List<Int>>() {

    override fun getName() = VIEW_FILE_INDEX_KEY

    override fun getIndexer(): DataIndexer<String, List<Int>, FileContent> =
        ViewVariableDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<Int>> =
        ViewVariableDataExternalizer

    override fun getVersion(): Int {
        return 1
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
