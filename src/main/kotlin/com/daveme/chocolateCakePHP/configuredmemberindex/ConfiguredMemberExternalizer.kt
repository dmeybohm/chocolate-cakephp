package com.daveme.chocolateCakePHP.configuredmemberindex

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object ConfiguredMemberExternalizer : DataExternalizer<ClassConfiguration> {

    private const val NO_ASSOCIATION_TYPE = -1

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
            out.writeInt(load.associationType?.ordinal ?: NO_ASSOCIATION_TYPE)
            writeNullableUTF(out, load.propertyName)
            out.writeBoolean(load.hasUnreadPropertyName)
            writeNullableMap(out, load.methodAliases)
            out.writeBoolean(load.hasUnreadMethodAliases)
            writeNullableMap(out, load.finderAliases)
            out.writeBoolean(load.hasUnreadFinderAliases)
            out.writeBoolean(load.viaTable)
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
                associationType = input.readInt().takeIf { it != NO_ASSOCIATION_TYPE }
                    ?.let { AssociationType.values()[it] },
                propertyName = readNullableUTF(input),
                hasUnreadPropertyName = input.readBoolean(),
                methodAliases = readNullableMap(input),
                hasUnreadMethodAliases = input.readBoolean(),
                finderAliases = readNullableMap(input),
                hasUnreadFinderAliases = input.readBoolean(),
                viaTable = input.readBoolean(),
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

    private fun writeNullableMap(out: DataOutput, value: Map<String, String>?) {
        out.writeBoolean(value != null)
        if (value != null) {
            out.writeInt(value.size)
            for ((key, entry) in value) {
                out.writeUTF(key)
                out.writeUTF(entry)
            }
        }
    }

    private fun readNullableMap(input: DataInput): Map<String, String>? {
        if (!input.readBoolean()) {
            return null
        }
        val result = linkedMapOf<String, String>()
        repeat(input.readInt()) {
            result[input.readUTF()] = input.readUTF()
        }
        return result
    }

    private fun readNullableUTF(input: DataInput): String? =
        if (input.readBoolean()) input.readUTF() else null
}
