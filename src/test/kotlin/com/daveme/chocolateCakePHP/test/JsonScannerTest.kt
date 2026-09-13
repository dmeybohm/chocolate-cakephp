package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.JsonEntry
import com.daveme.chocolateCakePHP.scanJsonEntries
import junit.framework.TestCase

class JsonScannerTest : TestCase() {

    private fun e(key: String, value: String?, vararg path: String) =
        JsonEntry(path.toList(), key, value)

    fun `test flat object`() {
        val entries = scanJsonEntries("""{"a": "1", "b": "2"}""")
        assertEquals(listOf(e("a", "1"), e("b", "2")), entries)
    }

    fun `test nested paths`() {
        val entries = scanJsonEntries("""{"autoload": {"psr-4": {"App\\": "src/"}}}""")
        assertEquals(
            listOf(
                e("autoload", null),
                e("psr-4", null, "autoload"),
                e("App\\", "src/", "autoload", "psr-4"),
            ),
            entries
        )
    }

    fun `test arrays are transparent and their strings are ignored`() {
        val entries = scanJsonEntries("""{"check": ["@test", "@cs-check"], "after": "x"}""")
        assertEquals(listOf(e("check", null), e("after", "x")), entries)
    }

    fun `test objects inside arrays keep the enclosing key path`() {
        val entries = scanJsonEntries("""{"list": [{"k": "v"}, {"k": "w"}]}""")
        assertEquals(listOf(e("list", null), e("k", "v", "list"), e("k", "w", "list")), entries)
    }

    fun `test scalar values yield null`() {
        val entries = scanJsonEntries("""{"n": 1.5, "t": true, "z": null, "s": "str"}""")
        assertEquals(listOf(e("n", null), e("t", null), e("z", null), e("s", "str")), entries)
    }

    fun `test escapes are decoded`() {
        val entries = scanJsonEntries("""{"App\\": "a\"b\/cA\n"}""")
        assertEquals(listOf(e("App\\", "a\"b/cA\n")), entries)
    }

    fun `test malformed unicode escape is kept literally`() {
        val entries = scanJsonEntries("""{"k": "x\u26Zy"}""")
        assertEquals(listOf(e("k", "x\\u26Zy")), entries)
    }

    fun `test trailing and double commas`() {
        assertEquals(listOf(e("a", "1")), scanJsonEntries("""{"a": "1",}"""))
        assertEquals(listOf(e("a", "1"), e("b", "2")), scanJsonEntries("""{"a": "1",, "b": "2"}"""))
    }

    fun `test truncated document keeps everything before the end`() {
        val entries = scanJsonEntries("""{"require": {"a": "1", "b": "2""")
        assertEquals(listOf(e("require", null), e("a", "1", "require"), e("b", "2", "require")), entries)
    }

    fun `test unterminated string value is emitted`() {
        val entries = scanJsonEntries("""{"key": "unterminated}""")
        assertEquals(listOf(e("key", "unterminated}")), entries)
    }

    fun `test missing value before next key`() {
        val entries = scanJsonEntries("""{"php": "cakephp/cakephp": "^5"}""")
        assertEquals(listOf(e("php", null), e("cakephp/cakephp", "^5")), entries)
    }

    fun `test missing value before closing brace`() {
        val entries = scanJsonEntries("""{"a": "1", "b": }""")
        assertEquals(listOf(e("a", "1"), e("b", null)), entries)
    }

    fun `test missing comma between members`() {
        val entries = scanJsonEntries("""{"a": "1" "b": "2"}""")
        assertEquals(listOf(e("a", "1"), e("b", "2")), entries)
    }

    fun `test missing colon drops that key and continues`() {
        val entries = scanJsonEntries("""{"a" "1", "b": "2"}""")
        assertEquals(listOf(e("b", "2")), entries)
    }

    fun `test unquoted key is skipped`() {
        val entries = scanJsonEntries("""{"a": "1", b: "2", "c": "3"}""")
        assertEquals(listOf(e("a", "1"), e("c", "3")), entries)
    }

    fun `test extra closing brace and trailing garbage are ignored`() {
        val entries = scanJsonEntries("""{"a": "1"}} garbage {"b": "2"}""")
        assertEquals(listOf(e("a", "1")), entries)
    }

    fun `test empty and non-container input yields no entries`() {
        assertEquals(emptyList<JsonEntry>(), scanJsonEntries(""))
        assertEquals(emptyList<JsonEntry>(), scanJsonEntries("   "))
        assertEquals(emptyList<JsonEntry>(), scanJsonEntries("\"hello\""))
        assertEquals(emptyList<JsonEntry>(), scanJsonEntries("123"))
        assertEquals(emptyList<JsonEntry>(), scanJsonEntries("not json at all"))
    }

    fun `test array root`() {
        val entries = scanJsonEntries("""[{"a": "1"}, "loose", {"b": "2"}]""")
        assertEquals(listOf(e("a", "1"), e("b", "2")), entries)
    }
}
