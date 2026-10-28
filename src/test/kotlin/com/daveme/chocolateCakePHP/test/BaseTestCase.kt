package com.daveme.chocolateCakePHP.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.PhpClass
import kotlin.collections.any
import kotlin.reflect.KClass
import org.junit.Assume
import org.junit.runner.RunWith

@RunWith(AssumptionAwareJUnit38Runner::class)
abstract class BaseTestCase : BasePlatformTestCase() {

    data class RelatedItemInfo(val filename: String, val containingDir: String)


    /**
     * The CakePHP major version the test targets, or null if it doesn't target one.
     */
    protected open val cakeMajorVersion: Int? = null

    private val realVendorDescriptor: RealCakeVendorProjectDescriptor?
        get() = cakeMajorVersion?.takeIf { RealCakeVendorProjectDescriptor.isEnabled }
            ?.let { RealCakeVendorProjectDescriptor.forVersion(it) }

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

    override fun getProjectDescriptor(): LightProjectDescriptor? =
        realVendorDescriptor ?: super.getProjectDescriptor()

    override fun setUp() {
        realVendorDescriptor?.let { descriptor ->
            VfsRootAccess.allowRootAccess(testRootDisposable, descriptor.vendorPath)
            // The PHP plugin hits recursion guards inferring self-referential field types in the
            // framework source. Loading the application enables these assertions, so load it first.
            TestApplicationManager.getInstance()
            RecursionManager.disableMissedCacheAssertions(testRootDisposable)
            RecursionManager.disableAssertOnRecursionPrevention(testRootDisposable)
        }
        super.setUp()
    }

    /**
     * Skips the test when the real framework is attached instead of the stubs.
     */
    protected fun assumeStubVendor(reason: String) {
        Assume.assumeTrue(reason, realVendorDescriptor == null)
    }

    /**
     * Copies the fixture files into the project, leaving out the stub framework when the real
     * framework is attached instead.
     */
    protected fun configureByFiles(vararg filePaths: String) {
        val paths = if (realVendorDescriptor != null) {
            filePaths.filterNot { it.endsWith("/vendor/cakephp.php") }.toTypedArray()
        } else {
            filePaths
        }
        if (paths.isNotEmpty()) {
            myFixture.configureByFiles(*paths)
        }
    }

    fun calculateLineMarkers(element: PsiElement, klass: KClass<out LineMarkerProvider>): List<LineMarkerInfo<*>> {
        return LineMarkerProviders.getInstance().allForLanguage(element.language)
            .flatMap { provider ->
                if (provider::class != klass) {
                    return@flatMap listOf<LineMarkerInfo<*>>()
                }
                val markers = arrayListOf<LineMarkerInfo<*>>()
                provider.collectSlowLineMarkers(listOf(element), markers)
                return@flatMap markers
            }
    }

    protected fun gotoRelatedItems(marker: LineMarkerInfo<*>): MutableCollection<out GotoRelatedItem> {
        val renderer = marker.createGutterRenderer() as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
        val lineMarkerInfo = renderer.lineMarkerInfo as RelatedItemLineMarkerInfo<*>
        return lineMarkerInfo.createGotoRelatedItems()
    }

    protected fun getRelatedItemInfos(collection: Collection<GotoRelatedItem>): Set<RelatedItemInfo> {
        return collection.mapNotNull { relatedItem ->
            val file = (relatedItem.element as? PsiFile) ?: return@mapNotNull null
            RelatedItemInfo(
                filename = file.name,
                containingDir = file.containingDirectory.name
            )
        }.toSet()
    }

    protected fun assertGotoDeclarationHandlerGoesToFilename(
        handler: GotoDeclarationHandler,
        filename: String
    ) {
        val offset = myFixture.editor.caretModel.offset
        val sourceElement = myFixture.file.findElementAt(offset)
        val targets = handler.getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)

        assertNotNull(targets)
        assertTrue(targets!!.size > 0)
        val target = targets.filter {
            (it as? PsiFile)?.virtualFile?.name == filename
        }
        assertNotNull(target)
        assertNotEmpty(target)
    }

    protected fun gotoDeclarationHandlerTargets(
        handler: GotoDeclarationHandler
    ): Array<PsiElement>? {
        val offset = myFixture.editor.caretModel.offset
        val sourceElement = myFixture.file.findElementAt(offset)
        val targets = handler.getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)
        return targets
    }

    protected fun assertGotoDeclarationHandlerGoesToTableClass(
        handler: GotoDeclarationHandler,
        expectedClassName: String
    ) {
        val offset = myFixture.editor.caretModel.offset
        val sourceElement = myFixture.file.findElementAt(offset)
        val targets = handler.getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)

        assertNotNull(targets)
        assertTrue(targets!!.isNotEmpty())

        val matchingClass = targets.any { target ->
            when (target) {
                is PhpClass -> target.name == expectedClassName
                else -> false
            }
        }
        assertTrue("Expected to find $expectedClassName", matchingClass)
    }

}