package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake4BaseTestCase : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.defaults.state.copy()
        newState.cake3Enabled = true
        newState.cake2Enabled = false
        newState.appDirectory = "src4"
        newState.pluginNamespaces = listOf("\\TestPlugin")
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}
