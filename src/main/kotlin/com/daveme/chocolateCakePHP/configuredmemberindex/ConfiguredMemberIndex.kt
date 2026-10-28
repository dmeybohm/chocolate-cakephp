package com.daveme.chocolateCakePHP.configuredmemberindex

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

val CONFIGURED_MEMBER_INDEX_KEY: ID<String, List<MemberLoad>> =
    ID.create("com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberIndex")

class ConfiguredMemberIndex : FileBasedIndexExtension<String, List<MemberLoad>>() {

    override fun getName() = CONFIGURED_MEMBER_INDEX_KEY

    override fun getIndexer(): DataIndexer<String, List<MemberLoad>, FileContent> =
        ConfiguredMemberDataIndexer

    override fun getKeyDescriptor(): KeyDescriptor<String> =
        EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<MemberLoad>> =
        ConfiguredMemberExternalizer

    override fun getVersion(): Int {
        return 2
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            file.fileType.name == "PHP"
        }
    }

    override fun dependsOnFileContent(): Boolean {
        return true
    }

}
