package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake2BaseTestCase : BaseTestCase() {

    override val cakeMajorVersion = 2

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake2Enabled = true
        newState.autoDetect = false
        newState.cake2AppDirectory = "app"
        newState.composerJsonPath = "cake2/composer.json"
        originalSettings.loadState(newState)
        myFixture.copyFileToProject("cake2/composer.json")

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()
}