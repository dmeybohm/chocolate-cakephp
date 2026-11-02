package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.daveme.chocolateCakePHP.navigation.CakeGotoRelatedItem
import com.daveme.chocolateCakePHP.view.viewfileindex.ElementType
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method

/**
 * Provides "Go to Related Item" navigation from view files to controller methods.
 *
 * This contributes to the Navigate → Related Symbol popup (Ctrl+Alt+Home / ⌃⌘↑)
 * WITHOUT showing gutter icons in view files.
 */
class ViewToControllerGotoRelatedProvider : GotoRelatedProvider() {

    override fun getItems(psiElement: PsiElement): List<GotoRelatedItem> {
        val project = psiElement.project

        // Skip during indexing
        if (DumbService.getInstance(project).isDumb) {
            return emptyList()
        }

        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return emptyList()
        }

        val file = psiElement.containingFile ?: return emptyList()

        // Only process view files
        if (!isCakeViewFile(project, settings, file)) {
            return emptyList()
        }

        val virtualFile = file.virtualFile ?: return emptyList()
        val viewFile = viewFileOf(project, settings, virtualFile)
            ?: return emptyList()

        // Get all controller references to this view file
        val referencingElements = directReferencesTo(project, settings, viewFile)

        // Convert to GotoRelatedItems
        return referencingElements.mapNotNull { elementAndPath ->
            val element = elementAndPath.psiElement?.takeIf { it.isValid } ?: return@mapNotNull null
            val group = when {
                elementAndPath.elementType == ElementType.CELL_RENDER -> "Cell usages"
                elementAndPath.cellName != null -> "Cells"
                else -> {
                    val containingFile = element.containingFile
                    when {
                        containingFile == null || !isCakeViewFile(project, settings, containingFile) -> "Controllers"
                        isElementFile(containingFile, settings) -> "Elements"
                        else -> "Views"
                    }
                }
            }
            createGotoRelatedItem(element, group)
        }
    }

    private fun createGotoRelatedItem(element: PsiElement, group: String): GotoRelatedItem {
        // Pre-compute all data from PSI elements to avoid EDT access violations
        val customName = computeCustomName(element)
        val containerName = computeContainerName(element)
        val iconPath = element.containingFile?.virtualFile?.path

        return CakeGotoRelatedItem(
            element = element,
            group = group,
            customName = customName,
            containerName = containerName,
            iconPath = iconPath
        )
    }

    private fun computeCustomName(element: PsiElement): String {
        // A field names the class that declares it
        (element as? Field)?.containingClass?.name?.let { return it }

        // Try to get method name
        val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
        if (method != null) {
            val containingClass = method.containingClass
            return if (containingClass != null) {
                "${containingClass.name}::${method.name}()"
            } else {
                "${method.name}()"
            }
        }

        // Fallback to containing file name
        val file = element.containingFile
        return file?.name ?: element.text.take(50)
    }

    private fun computeContainerName(element: PsiElement): String? {
        // Show the controller file's parent directory
        val file = element.containingFile
        return file?.virtualFile?.parent?.name
    }
}
