package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewVariableDataExternalizer : DataExternalizer<ViewVariables> {

    override fun save(out: DataOutput, value: ViewVariables) {
        out.writeInt(value.size)
        value.forEach { (key, value) ->
            out.writeUTF(key)
            val viewValue = value
            out.writeUTF(viewValue.possiblyIncompleteType)
            out.writeInt(viewValue.startOffset)
        }
    }

    override fun read(`in`: DataInput): ViewVariables {
        val size = `in`.readInt()
        val result = ViewVariables()
        repeat(size) {
            val key = `in`.readUTF()
            val type = `in`.readUTF()
            val offset = `in`.readInt()
            result[key] = ViewVariableValue(type, offset)
        }
        return result
    }

}