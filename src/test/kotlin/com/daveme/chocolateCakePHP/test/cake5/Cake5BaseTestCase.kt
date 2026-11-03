package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake5BaseTestCase : BaseTestCase()  {

    override val cakeMajorVersion = 5

    override fun setUp() {
        super.setUp()

        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3ForceEnabled = true
        newState.cake2ForceEnabled = false
        newState.appDirectory = "src5"
        newState.pluginConfigs = listOf(CakeThreePluginConfig(
            namespace = "\\TestPlugin",
            pluginPath = "vendor/test/test_plugin"
        ))
        newState.composerJsonPath = "cake5/composer.json"
        originalSettings.loadState(newState)
        myFixture.copyFileToProject("cake5/composer.json")

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}