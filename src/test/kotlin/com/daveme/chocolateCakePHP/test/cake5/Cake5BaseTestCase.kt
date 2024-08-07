package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase

abstract class Cake5BaseTestCase : BaseTestCase()  {

    override fun setUp() {
        super.setUp()

        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.defaults.state.copy()
        newState.cake3ForceEnabled = true
        newState.cake2Enabled = false
        newState.appDirectory = "src5"
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

}