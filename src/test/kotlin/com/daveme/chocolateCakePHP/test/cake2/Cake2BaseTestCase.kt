package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake2BaseTestCase : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val defaultSettings = Settings.defaults
        val newState = defaultSettings.state.copy()
        newState.cake2Enabled = true
        newState.cake3Enabled = false
        newState.cake2AppDirectory = "app"
        originalSettings.loadState(newState)

        prepareTest()
    }

    abstract fun prepareTest()
}