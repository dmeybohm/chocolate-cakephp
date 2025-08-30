package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewVariableRawVarsExternalizer : DataExternalizer<ViewVariablesWithRawVars> {

    override fun save(out: DataOutput, value: ViewVariablesWithRawVars) {
        out.writeInt(value.size)
        value.forEach { (key, rawVar) ->
            out.writeUTF(key)
            out.writeUTF(rawVar.variableName)
            out.writeInt(rawVar.varKind.ordinal)
            out.writeInt(rawVar.offset)
            val rawTokenText = rawVar.rawTokenText
            if (rawTokenText != null) {
                out.writeBoolean(true)
                out.writeUTF(rawTokenText)
            } else {
                out.writeBoolean(false)
            }
        }
    }

    override fun read(`in`: DataInput): ViewVariablesWithRawVars {
        val size = `in`.readInt()
        val result = ViewVariablesWithRawVars()
        repeat(size) {
            val key = `in`.readUTF()
            val variableName = `in`.readUTF()
            val assignmentKindOrdinal = `in`.readInt()
            val offset = `in`.readInt()
            val hasRawTokenText = `in`.readBoolean()
            val rawTokenText = if (hasRawTokenText) `in`.readUTF() else null
            
            val varKind = VarKind.values()[assignmentKindOrdinal]
            val rawVar = RawViewVar(variableName, varKind, offset, rawTokenText)
            result[key] = rawVar
        }
        return result
    }
}