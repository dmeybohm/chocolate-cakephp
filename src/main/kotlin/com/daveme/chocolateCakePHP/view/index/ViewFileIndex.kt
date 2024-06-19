package com.daveme.chocolateCakePHP.view.index

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor

class ViewFileIndex : FileBasedIndexExtension<ViewFileLocation, Void?>() {

    override fun getName(): ID<ViewFileLocation, Void?> = VIEW_FILE_INDEX_KEY

    override fun getIndexer(): DataIndexer<ViewFileLocation, Void?, FileContent> =
        ViewFileDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<ViewFileLocation> =
        ViewFileKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<Void?> =
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
