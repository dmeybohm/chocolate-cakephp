package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import javax.swing.Icon

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

        // Get all controller references to this view file
        val referencingElements = ViewFileIndexService
            .referencingElementsInSmartReadAction(project, filenameKey)
            .mapNotNull { it.psiElement }
            .filter { it.isValid }

        // Convert to GotoRelatedItems
        return referencingElements.map { element ->
            createGotoRelatedItem(element)
        }
    }

    private fun createGotoRelatedItem(element: PsiElement): GotoRelatedItem {
        return object : GotoRelatedItem(element, "Controllers") {
            override fun getCustomName(): String {
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

            override fun getCustomContainerName(): String? {
                // Show the controller file's parent directory
                val file = element.containingFile
                return file?.virtualFile?.parent?.name
            }

            override fun getCustomIcon(): Icon? {
                // No custom icon - use default PSI element icon
                return null
            }
        }
    }
}
