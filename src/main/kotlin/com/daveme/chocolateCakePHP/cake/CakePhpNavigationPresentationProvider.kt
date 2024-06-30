package com.daveme.chocolateCakePHP.cake

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpPresentationUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import javax.swing.Icon

class CakePhpNavigationPresentationProvider : PsiTargetPresentationRenderer<PsiElement>() {
    override fun getContainerText(element: PsiElement): String? {
        val file = element.containingFile
        if (file != null) {
            val virtualFile = file.virtualFile
            if (virtualFile != null)
                return PhpPresentationUtil.getPresentablePathForFile(
                    virtualFile,
                    element.project
                ) //virtualFile.presentableName
        }
        return super.getContainerText(element)
    }

    override fun getElementText(element: PsiElement): String {
        val file = element.containingFile
        if (file != null) {
            val virtualFile = file.virtualFile
            if (virtualFile != null) {
                val path = virtualFile.path
                if (path.contains("/Controller/")) {
                    // Get containing method if call is inside a controller:
                    val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
                    if (method != null) {
                        return super.getElementText(method)
                    }
                } else if (element is MethodReference) {
                    return virtualFile.name
                }
            }
        }
        return super.getElementText(element)
    }

    override fun getIcon(element: PsiElement): Icon {
        val path = element.containingFile.virtualFile?.path
        return if (path == null) {
            PhpIcons.FUNCTION
        } else if (path.contains("/Controller/")) {
            CakeIcons.LOGO_SVG
        } else {
            PhpIcons.PHP_FILE
        }
    }
}