package com.daveme.chocolateCakePHP.configuredmemberindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ConfiguredMemberExternalizer : DataExternalizer<ClassConfiguration> {

    override fun save(out: DataOutput, value: ClassConfiguration) {
        out.writeInt(value.loads.size)
        for (load in value.loads) {
            out.writeInt(load.kind.ordinal)
            out.writeInt(load.source.ordinal)
            out.writeUTF(load.alias)
            out.writeUTF(load.loadedName)
            writeNullableUTF(out, load.className)
            writeNullableUTF(out, load.methodName)
            out.writeInt(load.offset)
            writeNullableUTF(out, load.property)
            out.writeBoolean(load.hasUnreadClassName)
        }
        out.writeInt(value.declarations.size)
        for (declaration in value.declarations) {
            out.writeUTF(declaration.property)
            out.writeInt(declaration.kind.ordinal)
            out.writeInt(declaration.value.ordinal)
            out.writeBoolean(declaration.isComplete)
            out.writeInt(declaration.offset)
        }
    }

    override fun read(input: DataInput): ClassConfiguration {
        val loads = List(input.readInt()) {
            MemberLoad(
                kind = MemberKind.values()[input.readInt()],
                source = LoadSource.values()[input.readInt()],
                alias = input.readUTF(),
                loadedName = input.readUTF(),
                className = readNullableUTF(input),
                methodName = readNullableUTF(input),
                offset = input.readInt(),
                property = readNullableUTF(input),
                hasUnreadClassName = input.readBoolean(),
            )
        }
        val declarations = List(input.readInt()) {
            PropertyDeclaration(
                property = input.readUTF(),
                kind = MemberKind.values()[input.readInt()],
                value = DeclaredValue.values()[input.readInt()],
                isComplete = input.readBoolean(),
                offset = input.readInt(),
            )
        }
        return ClassConfiguration(loads, declarations)
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
