package com.daveme.chocolateCakePHP.configuredmemberindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ConfiguredMemberExternalizer : DataExternalizer<List<MemberLoad>> {

    override fun save(out: DataOutput, value: List<MemberLoad>) {
        out.writeInt(value.size)
        for (load in value) {
            out.writeInt(load.kind.ordinal)
            out.writeInt(load.source.ordinal)
            out.writeUTF(load.alias)
            out.writeUTF(load.loadedName)
            writeNullableUTF(out, load.className)
            writeNullableUTF(out, load.methodName)
            out.writeInt(load.offset)
        }
    }

    override fun read(input: DataInput): List<MemberLoad> {
        return List(input.readInt()) {
            MemberLoad(
                kind = MemberKind.values()[input.readInt()],
                source = LoadSource.values()[input.readInt()],
                alias = input.readUTF(),
                loadedName = input.readUTF(),
                className = readNullableUTF(input),
                methodName = readNullableUTF(input),
                offset = input.readInt(),
            )
        }
    }

    private fun writeNullableUTF(out: DataOutput, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) {
            out.writeUTF(value)
        }
    }

    private fun readNullableUTF(input: DataInput): String? =
        if (input.readBoolean()) input.readUTF() else null
}
