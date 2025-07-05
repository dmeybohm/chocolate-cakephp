package com.daveme.chocolateCakePHP.view.viewfileindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class ViewFileIndex : FileBasedIndexExtension<String, List<ViewReferenceData>>() {

    override fun getName() = VIEW_FILE_INDEX_KEY

    override fun getIndexer(): DataIndexer<String, List<ViewReferenceData>, FileContent> =
        ViewFileDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<ViewReferenceData>> =
        ViewReferenceDataExternalizer

    override fun getVersion(): Int {
        return 14
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
