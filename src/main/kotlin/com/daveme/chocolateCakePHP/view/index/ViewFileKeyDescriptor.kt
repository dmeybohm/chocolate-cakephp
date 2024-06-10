package com.daveme.chocolateCakePHP.view.index

import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

object ViewFileKeyDescriptor : KeyDescriptor<ViewFileLocation> {
    override fun getHashCode(value: ViewFileLocation?): Int = value.hashCode()

    override fun isEqual(val1: ViewFileLocation, val2: ViewFileLocation): Boolean =
        val1.equals(val2)

    override fun save(out: DataOutput, value: ViewFileLocation) {
        val viewStr = if (value.viewType == ViewFileLocation.ViewType.VIEW)
            "V"
        else
            "E"
        IOUtil.writeUTF(out, viewStr)
        IOUtil.writeUTF(out, value.filename)
        IOUtil.writeUTF(out, value.prefixPath)
    }

    override fun read(`in`: DataInput): ViewFileLocation {
        val viewType = if (IOUtil.readUTF(`in`) == "V")
            ViewFileLocation.ViewType.VIEW
        else
            ViewFileLocation.ViewType.ELEMENT
        return ViewFileLocation(
            filename = IOUtil.readUTF(`in`),
            prefixPath = IOUtil.readUTF(`in`),
            viewType = viewType
        )
    }
}