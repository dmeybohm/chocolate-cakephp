package com.daveme.chocolateCakePHP

fun Class<*>.allInterfaces(): String {
    val interfaces = this.interfaces
    val builder = StringBuilder()
    builder.append("{")
    for (intface in interfaces) {
        if (builder.length > 1) {
            builder.append(", ")
        }
        builder.append(intface.canonicalName)
    }
    builder.append("}")
    return builder.toString()
}
