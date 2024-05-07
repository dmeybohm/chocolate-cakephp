package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake3BaseTestCase : BaseTestCase() {
    override fun setUp() {
        super.setUp()

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.cake3Enabled = true
        newState.cake2Enabled = false
        newState.appDirectory = "src"
        newState.pluginNamespaces = listOf("\\TestPlugin")
        originalSettings.loadState(newState)

        prepareTest()
    }

    abstract fun prepareTest()

}