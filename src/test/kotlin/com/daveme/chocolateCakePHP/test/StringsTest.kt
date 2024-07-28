package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.removeImmediateParentDir

class StringsTest : BaseTestCase() {

    fun `test replace json in path`() {
        val result1 = "foo/foo.php".removeImmediateParentDir("json")
        assertEquals("foo/foo.php", result1)

        val result2 = "foo/json/bar/json/foo.php".removeImmediateParentDir("json")
        assertEquals("foo/json/bar/foo.php", result2)
    }

}