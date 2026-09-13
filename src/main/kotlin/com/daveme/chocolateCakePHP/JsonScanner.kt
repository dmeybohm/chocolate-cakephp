package com.daveme.chocolateCakePHP

/**
 * One key found while scanning a JSON document.
 *
 * [path] is the list of object keys enclosing this entry (arrays are
 * transparent and contribute nothing). [value] is the string value if the
 * value was a string literal, and null for objects, arrays, numbers,
 * booleans, null, or a missing value.
 */
data class JsonEntry(val path: List<String>, val key: String, val value: String?)

/**
 * A deliberately fault-tolerant scanner that extracts the object keys of a
 * JSON document, with their paths and string values, without building a tree.
 *
 * This is hand-rolled rather than using Gson, Jackson, or kotlinx.serialization
 * (all bundled in the IntelliJ platform) because those parsers reject malformed
 * input outright. It is used by [CakePhpAutoDetector] to read composer.json,
 * and detection is re-run on every content change to that file. Users
 * routinely save composer.json in a half-edited state, so the scanner must
 * keep going through anything it does not understand.
 *
 * The rule is simple: a string followed by `:` is a key; any other string in
 * an object is the value of the pending key, if there is one. Everything else
 * is skipped. As a result the scanner never throws and resynchronises after:
 *
 *  - trailing and double commas
 *  - a missing comma between members
 *  - a missing colon (that key is dropped; scanning continues)
 *  - a missing value (emitted with a null value; scanning continues)
 *  - an unquoted key (skipped)
 *  - a truncated document (everything before the end is kept)
 *  - an unterminated string (stops at the end of its line and is still
 *    emitted, so later lines are unaffected)
 *  - an extra closing brace anywhere (the root is never closed, so
 *    everything after the brace is still scanned as part of the root)
 *
 * Without this, the plugin's auto-detected CakePHP settings would flip to
 * defaults whenever composer.json is momentarily invalid, and CakePHP
 * features would flicker off until the file is fixed.
 */
fun scanJsonEntries(json: String): List<JsonEntry> = scan(tokenize(json))

private sealed class Token {
    data class Str(val text: String) : Token()
    object LBrace : Token()
    object RBrace : Token()
    object LBracket : Token()
    object RBracket : Token()
    object Colon : Token()
    object Comma : Token()
    object Other : Token()
}

private fun isStructural(c: Char): Boolean =
    c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' || c == '"'

private fun tokenize(json: String): List<Token> {
    val tokens = ArrayList<Token>()
    val n = json.length
    var i = 0
    while (i < n) {
        val c = json[i]
        when {
            c.isWhitespace() -> i++
            c == '{' -> { tokens.add(Token.LBrace); i++ }
            c == '}' -> { tokens.add(Token.RBrace); i++ }
            c == '[' -> { tokens.add(Token.LBracket); i++ }
            c == ']' -> { tokens.add(Token.RBracket); i++ }
            c == ':' -> { tokens.add(Token.Colon); i++ }
            c == ',' -> { tokens.add(Token.Comma); i++ }
            c == '"' -> {
                val (text, next) = readString(json, i + 1)
                tokens.add(Token.Str(text))
                i = next
            }
            else -> {
                // Numbers, true/false/null, and anything unrecognised.
                while (i < n && !json[i].isWhitespace() && !isStructural(json[i])) i++
                tokens.add(Token.Other)
            }
        }
    }
    return tokens
}

/**
 * Read a string literal starting just after its opening quote. Returns the
 * unescaped text and the index just past the closing quote.
 *
 * JSON forbids raw newlines inside strings, so a newline means the closing
 * quote is missing. The string ends there (the newline is left for the
 * tokenizer) so that a missing quote cannot swallow the rest of the document.
 * An unterminated string on the last line runs to the end of input.
 */
private fun readString(json: String, start: Int): Pair<String, Int> {
    val sb = StringBuilder()
    val n = json.length
    var i = start
    while (i < n) {
        val c = json[i]
        if (c == '"') {
            return sb.toString() to i + 1
        }
        if (c == '\n' || c == '\r') {
            return sb.toString() to i
        }
        if (c == '\\' && i + 1 < n) {
            when (val esc = json[i + 1]) {
                '"', '\\', '/' -> { sb.append(esc); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                'f' -> { sb.append('\u000C'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'u' -> {
                    val code = fourHexDigits(json, i + 2)
                    if (code >= 0) {
                        sb.append(code.toChar()); i += 6
                    } else {
                        // Malformed escape: keep the backslash literally and move on.
                        sb.append(c); i++
                    }
                }
                else -> { sb.append(c); i++ }
            }
        } else {
            sb.append(c); i++
        }
    }
    return sb.toString() to n
}

/**
 * The value of exactly four hex digits starting at [start], or -1 if any of
 * the four characters is missing or is not a hex digit. Unlike
 * `toIntOrNull(16)`, a sign is not accepted.
 */
private fun fourHexDigits(json: String, start: Int): Int {
    if (start + 4 > json.length) return -1
    var code = 0
    for (k in 0 until 4) {
        val d = hexDigit(json[start + k])
        if (d < 0) return -1
        code = code * 16 + d
    }
    return code
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

private class Frame(val isObject: Boolean, val path: List<String>) {
    var pendingKey: String? = null
}

private fun scan(tokens: List<Token>): List<JsonEntry> {
    val entries = ArrayList<JsonEntry>()
    val stack = ArrayDeque<Frame>()

    fun flush(frame: Frame, value: String?) {
        val key = frame.pendingKey ?: return
        entries.add(JsonEntry(frame.path, key, value))
        frame.pendingKey = null
    }

    for ((i, tok) in tokens.withIndex()) {
        val frame = stack.lastOrNull()
        if (frame == null) {
            when (tok) {
                Token.LBrace -> stack.addLast(Frame(true, emptyList()))
                Token.LBracket -> stack.addLast(Frame(false, emptyList()))
                else -> {} // Ignore anything before the root opens.
            }
            continue
        }
        when (tok) {
            is Token.Str -> if (frame.isObject) {
                if (tokens.getOrNull(i + 1) == Token.Colon) {
                    flush(frame, null)
                    frame.pendingKey = tok.text
                } else {
                    flush(frame, tok.text)
                }
            }
            Token.LBrace, Token.LBracket -> {
                val key = frame.pendingKey
                val childPath = if (frame.isObject && key != null) frame.path + key else frame.path
                flush(frame, null)
                stack.addLast(Frame(tok == Token.LBrace, childPath))
            }
            Token.RBrace, Token.RBracket -> {
                flush(frame, null)
                // Never pop the root: an extra closing brace left behind
                // mid-edit must not hide everything after it.
                if (stack.size > 1) {
                    stack.removeLast()
                }
            }
            Token.Other -> if (frame.isObject) flush(frame, null)
            Token.Colon, Token.Comma -> {}
        }
    }
    return entries
}
