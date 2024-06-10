package com.daveme.chocolateCakePHP.view.index

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewFileDataExternalizer : DataExternalizer<Void?> {
    override fun save(out: DataOutput, value: Void?) {
        return
    }

    override fun read(`in`: DataInput): Void? {
        return null
    }
}