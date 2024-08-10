package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.view.AssetGotoDeclarationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.reflect.KClass

abstract class BaseTestCase : BasePlatformTestCase() {

    data class RelatedItemInfo(val filename: String, val containingDir: String)


    override fun getTestDataPath(): String {
        return "src/test/fixtures"
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

    protected fun assertCurrentCaretNavigatesToFilename(filename: String) {
        val offset = myFixture.editor.caretModel.offset
        val sourceElement = myFixture.file.findElementAt(offset)
        val myHandler = AssetGotoDeclarationHandler()
        val targets = myHandler.getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)

        assertNotNull(targets)
        assertTrue(targets!!.size > 0)
        val target = targets.filter {
            (it as? PsiFile)?.virtualFile?.name == filename
        }
        assertNotNull(target)
        assertNotEmpty(target)
    }
}