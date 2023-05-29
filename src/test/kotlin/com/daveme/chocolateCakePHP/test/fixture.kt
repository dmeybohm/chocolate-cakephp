package com.daveme.chocolateCakePHP.test

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

public fun CodeInsightTestFixture.configureByFilePathAndText(filePath: String, text: String) {
   val virtualFile = this.copyFileToProject(filePath, filePath)
   this.saveText(virtualFile, text)
   this.configureFromExistingVirtualFile(virtualFile)
}