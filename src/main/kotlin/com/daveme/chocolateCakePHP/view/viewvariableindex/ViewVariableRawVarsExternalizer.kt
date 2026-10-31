package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ViewVariableRawVarsExternalizer : DataExternalizer<ViewVariablesWithRawVars> {
    override fun save(out: DataOutput, value: ViewVariablesWithRawVars) {
        out.writeInt(value.calls.size)
        for (call in value.calls) {
            out.writeInt(call.offset)
            out.writeInt(call.entries.size)
            for (entry in call.entries) {
                out.writeUTF(entry.variableName)
                out.writeInt(entry.varKind.ordinal)
                out.writeInt(entry.offset)
                out.writeInt(entry.varHandle.sourceKind.ordinal)
                out.writeUTF(entry.varHandle.symbolName)
                out.writeInt(entry.varHandle.offset)
            }
        }
    }

    override fun read(input: DataInput): ViewVariablesWithRawVars {
        val calls = MutableList(input.readInt()) {
            val offset = input.readInt()
            val entries = List(input.readInt()) {
                RawViewVar(input.readUTF(), VarKind.values()[input.readInt()], input.readInt(),
                    VarHandle(SourceKind.values()[input.readInt()], input.readUTF(), input.readInt()))
            }
            ViewVariableCall(offset, entries)
        }
        return ViewVariablesWithRawVars(calls)
    }
}
