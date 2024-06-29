package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewVariableDataExternalizer : DataExternalizer<List<Int>> {

    override fun save(out: DataOutput, value: List<Int>) {
        out.writeInt(value.size)
        value.map { out.writeInt(it) }
    }

    override fun read(`in`: DataInput): List<Int> {
        val size = `in`.readInt()
        return List(size) {
            `in`.readInt()
        }
    }

}