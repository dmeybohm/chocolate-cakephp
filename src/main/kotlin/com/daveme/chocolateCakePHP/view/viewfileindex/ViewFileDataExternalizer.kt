package com.daveme.chocolateCakePHP.view.viewfileindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewReferenceDataExternalizer : DataExternalizer<List<ViewReferenceData>> {

    override fun save(out: DataOutput, value: List<ViewReferenceData>) {
        out.writeInt(value.size)
        value.forEach { data ->
            out.writeUTF(data.methodName)
            out.writeUTF(data.elementType)
            out.writeInt(data.offset)
        }
    }

    override fun read(`in`: DataInput): List<ViewReferenceData> {
        val size = `in`.readInt()
        return List(size) {
            ViewReferenceData(
                methodName = `in`.readUTF(),
                elementType = `in`.readUTF(),
                offset = `in`.readInt()
            )
        }
    }

}