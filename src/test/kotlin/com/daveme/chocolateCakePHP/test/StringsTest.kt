package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.substringOrNull
import com.daveme.chocolateCakePHP.removeImmediateParentDir
import com.daveme.chocolateCakePHP.joinViewPath
import junit.framework.TestCase

class StringsTest : BaseTestCase() {

    private val text = "Kotlin is fun!"

    // Test case 1: Valid indices within bounds
    fun testValidIndicesWithinBounds() {
        assertEquals("Kotlin", text.substringOrNull(0, 6))
    }

    // Test case 2: Only startIndex provided, endIndex defaults to the end of the string
    fun testOnlyStartIndexProvided() {
        assertEquals("is fun!", text.substringOrNull(7))
    }

    // Test case 3: Full string when startIndex is 0 and endIndex is the length of the string
    fun testFullString() {
        assertEquals("Kotlin is fun!", text.substringOrNull(0, text.length))
    }

    // Test case 4: startIndex is out of bounds
    fun testStartIndexOutOfBounds() {
        assertNull(text.substringOrNull(20, 25))
    }

    // Test case 5: endIndex is out of bounds
    fun testEndIndexOutOfBounds() {
        assertNull(text.substringOrNull(0, 20))
        val result2 = "foo/json/bar/json/foo.php".removeImmediateParentDir("json")
        assertEquals("foo/json/bar/foo.php", result2)
    }

    // Test case 6: startIndex > endIndex, invalid range
    fun testStartIndexGreaterThanEndIndex() {
        assertNull(text.substringOrNull(5, 3))
    }

    // Test case 7: Empty substring when startIndex equals endIndex
    fun testEmptySubstring() {
        assertEquals("", text.substringOrNull(5, 5))
    }

    // Test case 8: startIndex equals length, expecting empty string
    fun testStartIndexAtLength() {
        assertEquals("", text.substringOrNull(text.length))
    }

    // Test case 9: Both indices are zero, expecting empty string
    fun testBothIndicesZero() {
        assertEquals("", text.substringOrNull(0, 0))
    }

    fun `test replace json in path`() {
        val result1 = "foo/foo.php".removeImmediateParentDir("json")
        assertEquals("foo/foo.php", result1)

        val result2 = "foo/json/bar/json/foo.php".removeImmediateParentDir("json")
        assertEquals("foo/json/bar/foo.php", result2)
    }

    // joinViewPath() tests

    fun `test joinViewPath basic path joining`() {
        val result = joinViewPath("Admin/Users", "edit")
        assertEquals("Admin/Users/edit", result)
    }

    fun `test joinViewPath normalize leading and trailing slashes`() {
        val result = joinViewPath("/Admin//Users/", "/edit/")
        assertEquals("Admin/Users/edit", result)
    }

    fun `test joinViewPath trim whitespace`() {
        val result = joinViewPath(" Movie/Nested ", " custom ")
        assertEquals("Movie/Nested/custom", result)
    }

    fun `test joinViewPath collapse repeated slashes`() {
        val result = joinViewPath("Movie//Nested", "summary")
        assertEquals("Movie/Nested/summary", result)
    }

    fun `test joinViewPath handle empty segments`() {
        val result = joinViewPath("", "index")
        assertEquals("index", result)
    }

    fun `test joinViewPath mix of issues`() {
        val result = joinViewPath(" /Admin// ", "  /Users/  ", " edit ")
        assertEquals("Admin/Users/edit", result)
    }

    fun `test joinViewPath null segments`() {
        val result = joinViewPath("Admin", null, "edit")
        assertEquals("Admin/edit", result)
    }

    fun `test joinViewPath single segment`() {
        val result = joinViewPath("index")
        assertEquals("index", result)
    }

    fun `test joinViewPath all empty`() {
        val result = joinViewPath("", "", "")
        assertEquals("", result)
    }
}