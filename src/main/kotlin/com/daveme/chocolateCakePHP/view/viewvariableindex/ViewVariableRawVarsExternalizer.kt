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
            // Save VarHandle
            out.writeInt(rawVar.varHandle.sourceKind.ordinal)
            out.writeUTF(rawVar.varHandle.symbolName)
            out.writeInt(rawVar.varHandle.offset)
        }
    }

    override fun read(`in`: DataInput): ViewVariablesWithRawVars {
        val size = `in`.readInt()
        val result = ViewVariablesWithRawVars()
        repeat(size) {
            val key = `in`.readUTF()
            val variableName = `in`.readUTF()
            val varKindOrdinal = `in`.readInt()
            val offset = `in`.readInt()
            // Read VarHandle
            val sourceKindOrdinal = `in`.readInt()
            val symbolName = `in`.readUTF()
            val handleOffset = `in`.readInt()
            
            val varKind = VarKind.values()[varKindOrdinal]
            val sourceKind = SourceKind.values()[sourceKindOrdinal]
            val varHandle = VarHandle(sourceKind, symbolName, handleOffset)
            val rawVar = RawViewVar(variableName, varKind, offset, varHandle)
            result[key] = rawVar
        }
        return result
    }
}