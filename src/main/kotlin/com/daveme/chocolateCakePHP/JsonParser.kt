package com.daveme.chocolateCakePHP

import java.lang.StringBuilder

fun jsonParse(json: String): Any? {
    return JsonParser(json).parse()
}

class JsonParser(val json: String) {
    private var index = 0
    private val end = json.length

    fun parse(): Any? {
        return parseValue()
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (index >= end) {
            throw IllegalArgumentException("No value found")
        }
        return when (json[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            in '0'..'9', '-' -> parseNumber()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> throw IllegalArgumentException("Unexpected character: ${json[index]}")
        }
    }

    private fun skipWhitespace() {
        while (index < end && json[index].isWhitespace()) {
            index++
        }
    }

    private fun parseString(): String {
        val sb = StringBuilder()
        index++ // Skip '"'
        while (index < end && json[index] != '"') {
            if (json[index] == '\\') {
                index++ // Skip '\'
                if (index >= end) {
                    break
                }
                when (val esc = json[index]) {
                    '"', '\\', '/' -> sb.append(esc)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        val hex = json.substring(index + 1, index + 5)
                        sb.append(hex.toInt(16).toChar())
                        index += 4 // Skip the 4 hex digits
                    }
                    else -> throw IllegalArgumentException("Unexpected escape sequence: \\$esc")
                }
            } else {
                sb.append(json[index])
            }
            index++
        }
        index++ // Skip closing '"'
        return sb.toString()
    }

    private fun parseObject(): Map<String, Any?> {
        val obj = mutableMapOf<String, Any?>()
        index++ // Skip '{'
        skipWhitespace()
        while (index < end && json[index] != '}') {
            val key = parseString()
            skipWhitespace()
            if (index >= end) {
                break
            }
            if (json[index] != ':') {
                throw IllegalArgumentException("Expected ':' after key")
            }
            index++ // Skip ':'
            val value = parseValue()
            obj[key] = value
            skipWhitespace()
            if (index >= end) {
                break
            }
            if (json[index] == ',') {
                index++ // Skip ','
                skipWhitespace()
            } else if (json[index] != '}') {
                throw IllegalArgumentException("Expected ',' or '}' in object")
            }
        }
        index++ // Skip '}'
        return obj
    }

    private fun parseArray(): List<Any?> {
        val array = mutableListOf<Any?>()
        index++ // Skip '['
        skipWhitespace()
        while (index < end && json[index] != ']') {
            array.add(parseValue())
            skipWhitespace()
            if (index < end && json[index] == ',') {
                index++ // Skip ','
                skipWhitespace()
            } else if (index < end && json[index] != ']') {
                throw IllegalArgumentException("Expected ',' or ']' in array")
            }
        }
        index++ // Skip ']'
        return array
    }

    private fun parseNumber(): Number {
        val start = index
        if (index < end && json[index] == '-') {
            index++
        }
        while (index < end && json[index] in '0'..'9') {
            index++
        }
        if (index < end && json[index] == '.') {
            index++
            while (index < end && json[index] in '0'..'9') {
                index++
            }
        }
        if (index < end && (json[index] == 'e' || json[index] == 'E')) {
            index++
            if (index < end && (json[index] == '+' || json[index] == '-')) {
                index++
            }
            while (index < end && json[index] in '0'..'9') {
                index++
            }
        }
        return json.substring(start, index).toDouble()
    }

    private fun parseBoolean(): Boolean {
        return if (json.startsWith("true", index)) {
            index += 4
            true
        } else if (json.startsWith("false", index)) {
            index += 5
            false
        } else {
            throw IllegalArgumentException("Expected 'true' or 'false'")
        }
    }

    private fun parseNull(): Any? {
        if (json.startsWith("null", index)) {
            index += 4
            return null
        } else {
            throw IllegalArgumentException("Expected 'null'")
        }
    }
}
