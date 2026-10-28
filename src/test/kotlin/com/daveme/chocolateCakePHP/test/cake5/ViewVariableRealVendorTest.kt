package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.RealCakeVendorProjectDescriptor
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestApplicationManager

class ViewVariableRealVendorTest : ViewVariableTest() {

    override val vendorFiles = emptyList<String>()

    override fun getProjectDescriptor(): LightProjectDescriptor = RealCakeVendorProjectDescriptor.CAKE5

    override fun setUp() {
        VfsRootAccess.allowRootAccess(testRootDisposable, RealCakeVendorProjectDescriptor.CAKE5.vendorPath)
        // The PHP plugin hits recursion guards inferring self-referential field types in the framework
        // source. Loading the application enables these assertions, so it has to be loaded first.
        TestApplicationManager.getInstance()
        RecursionManager.disableMissedCacheAssertions(testRootDisposable)
        RecursionManager.disableAssertOnRecursionPrevention(testRootDisposable)
        super.setUp()
    }
}
