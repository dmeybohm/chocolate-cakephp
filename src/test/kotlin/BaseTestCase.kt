package com.daveme.chocolateCakePHP.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class BaseTestCase : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

}