package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake4BaseTestCase : BaseTestCase() {

    override val cakeMajorVersion = 4

    override fun setUp() {
        super.setUp()

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3ForceEnabled = true
        newState.cake2Enabled = false
        newState.appDirectory = "src4"
        newState.pluginConfigs = listOf(CakeThreePluginConfig(
            pluginName = "TestPlugin",
            namespace = "\\TestPlugin",
            pluginPath = "vendor/test/test_plugin"
        ))
        newState.composerJsonPath = "cake4/composer.json"
        originalSettings.loadState(newState)
        myFixture.copyFileToProject("cake4/composer.json")

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}
