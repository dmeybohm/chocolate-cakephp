package com.daveme.chocolateCakePHP.view.index

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class ViewFileIndex : FileBasedIndexExtension<String, List<Int>>() {

    override fun getName() = VIEW_FILE_INDEX_KEY

    override fun getIndexer(): DataIndexer<String, List<Int>, FileContent> =
        ViewFileDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<Int>> =
        ViewFileDataExternalizer

    override fun getVersion(): Int {
        // TODO change this to stable number
        return kotlin.random.Random.nextInt()
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
