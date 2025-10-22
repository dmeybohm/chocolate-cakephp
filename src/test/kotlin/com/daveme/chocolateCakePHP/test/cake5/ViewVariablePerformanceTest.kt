package com.daveme.chocolateCakePHP.test.cake5

/**
 * Simple smoke tests to verify the optimized fast-path checking is working.
 * These tests just verify the code paths execute without errors - they don't
 * measure actual performance since that's environment-dependent.
 */
class ViewVariablePerformanceTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/vendor/cakephp.php"
        )
    }

    /**
     * Verifies static pattern suppression works (Phase 1).
     * Static patterns use direct map lookup without PSI loading.
     * Example: $this->set(compact('metadata'))
     */
    fun `test static pattern suppression works`() {
        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)

        myFixture.addFileToProject("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}metadata;
        """.trimIndent())

        myFixture.configureByFile("cake5/templates/Movie/artist.php")
        myFixture.checkHighlighting(true, false, false)

        // If we get here without exceptions, the fast path is working
        assertTrue(true)
    }

    /**
     * Verifies dynamic pattern suppression works (Phase 2).
     * Dynamic patterns require PSI loading and extraction.
     * Example: $vars = ['movie' => ...]; $this->set($vars)
     */
    fun `test dynamic pattern suppression works`() {
        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)

        myFixture.addFileToProject("cake5/templates/Movie/variable_array_test.php", """
        <?php
        echo ${'$'}movie;
        """.trimIndent())

        myFixture.configureByFile("cake5/templates/Movie/variable_array_test.php")
        myFixture.checkHighlighting(true, false, false)

        // If we get here without exceptions, the dynamic pattern extraction is working
        assertTrue(true)
    }
}
