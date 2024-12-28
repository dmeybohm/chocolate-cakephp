package com.daveme.chocolateCakePHP.view.elementvariableindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ElementVariableDataExternalizer : DataExternalizer<ElementVariables> {

    override fun save(out: DataOutput, value: ElementVariables) {
        out.writeInt(value.size)
        value.forEach { (key, value) ->
            out.writeUTF(key)
            val viewValue = value
            out.writeUTF(viewValue.possiblyIncompleteType)
            out.writeInt(viewValue.startOffset)
        }
    }

    override fun read(`in`: DataInput): ElementVariables {
        val size = `in`.readInt()
        val result = ElementVariables()
        repeat(size) {
            val key = `in`.readUTF()
            val type = `in`.readUTF()
            val offset = `in`.readInt()
            result[key] = ElementVariableValue(type, offset)
        }
        return result
    }

}