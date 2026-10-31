package com.daveme.chocolateCakePHP.test.cake2

class FrameworkNotIndexedControllerTest : ConfiguredControllerFixtureTestCase() {

    override val attachesFramework = false

    fun `test without the framework and declarations convention stays`() {
        appController()
        configureController("", "\$this->Fo<caret>o;")
        assertEquals(setOf("\\Foo", "\\FooComponent"), fieldTypeAtCaret())

        configureController("", "\$this-><caret>")
        val result = completions()
        assertTrue("Expected Unloaded in $result", result.contains("Unloaded"))
        assertTrue("Expected User in $result", result.contains("User"))
    }
}
