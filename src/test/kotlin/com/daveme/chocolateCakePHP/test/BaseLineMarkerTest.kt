package com.daveme.chocolateCakePHP.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiElement
import kotlin.reflect.KClass


abstract class BaseLineMarkerTest : BaseTestCase() {

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

}