package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.navigation.CakeGotoRelatedItem
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Provides "Go to Related Item" navigation from element files to views/elements that use them.
 *
 * This contributes to the Navigate → Related Symbol popup (Ctrl+Alt+Home / ⌃⌘↑)
 * WITHOUT showing gutter icons in element files.
 *
 * Groups results into:
 * - "Views" - Regular views that call $this->element()
 * - "Elements" - Other elements that nest this element
 */
class ElementToUsagesGotoRelatedProvider : GotoRelatedProvider() {

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

        // Only process element files
        if (!isElementFile(file, settings)) {
            return emptyList()
        }

        val virtualFile = file.virtualFile ?: return emptyList()
        val templatesDir = templatesDirectoryOfViewFile(project, settings, file)
            ?: return emptyList()

        val templateDirVirtualFile = templatesDir.directory
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile)
            ?: return emptyList()

        val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
            templatesDir,
            settings,
            relativePath
        )

        // Get all references to this element file
        val referencingElements = ViewFileIndexService
            .referencingElementsInSmartReadAction(project, filenameKey)
            .mapNotNull { it.psiElement }
            .filter { it.isValid }

        // Convert to GotoRelatedItems with appropriate grouping
        return referencingElements.map { element ->
            createGotoRelatedItem(element, settings)
        }
    }

    /**
     * Check if the file is an element file (located in element/Element/Elements directory)
     */
    private fun isElementFile(file: PsiFile, settings: Settings): Boolean {
        val project = file.project
        val templatesDir = templatesDirectoryOfViewFile(project, settings, file)
            ?: return false

        val virtualFile = file.virtualFile ?: return false
        val relativePath = VfsUtil.getRelativePath(virtualFile, templatesDir.directory)
            ?: return false

        // Check if the file is in the element directory
        return relativePath.startsWith("${templatesDir.elementDirName}/")
    }

    /**
     * Create a GotoRelatedItem with appropriate grouping based on whether the
     * referencing file is a view or an element.
     */
    private fun createGotoRelatedItem(element: PsiElement, settings: Settings): GotoRelatedItem {
        // Pre-compute all data from PSI elements to avoid EDT access violations
        val containingFile = element.containingFile
        val group = if (containingFile != null && isElementFile(containingFile, settings)) {
            "Elements"
        } else {
            "Views"
        }

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
        // Show the file name and the element() call context
        val file = element.containingFile
        return file?.name ?: element.text.take(50)
    }

    private fun computeContainerName(element: PsiElement): String? {
        // Show the directory path
        val file = element.containingFile
        val vFile = file?.virtualFile ?: return null

        // Get a meaningful path (e.g., "Movie" for "templates/Movie/index.php")
        val parent = vFile.parent ?: return null
        val grandParent = parent.parent

        return if (grandParent != null && (parent.name == "element" || parent.name == "Element" || parent.name == "Elements")) {
            // Element in root: just show "element"
            parent.name
        } else {
            // View or nested element: show parent directory
            parent.name
        }
    }
}
