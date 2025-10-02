package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake3BaseTestCase : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3ForceEnabled = true
        newState.cake2Enabled = false
        newState.appDirectory = "src"
        newState.pluginConfigs = listOf(PluginConfig(
            namespace = "\\TestPlugin",
            pluginPath = "vendor/test/test_plugin"
        ))
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}