package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake3BaseTestCase : BaseTestCase() {

    override val cakeMajorVersion = 3

    override fun setUp() {
        super.setUp()

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3ForceEnabled = true
        newState.cake2ForceEnabled = false
        newState.appDirectory = "src"
        newState.pluginConfigs = listOf(CakeThreePluginConfig(
            pluginName = "TestPlugin",
            namespace = "\\TestPlugin",
            pluginPath = "vendor/test/test_plugin"
        ))
        newState.composerJsonPath = "cake3/composer.json"
        originalSettings.loadState(newState)
        myFixture.copyFileToProject("cake3/composer.json")

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}