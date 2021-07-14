package com.daveme.chocolateCakePHP.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Before

abstract class PluginTestCase : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

}