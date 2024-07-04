package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.daveme.chocolateCakePHP.cake.templatesDirWithPath
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.daveme.chocolateCakePHP.viewType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ThisVariableInViewTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '\u8313'
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is Variable) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return null
        }
        val psiFile = psiElement.containingFile ?: return null
        val templateDir = templatesDirectoryFromViewFile(psiElement.project, settings, psiFile)
            ?: return null

        if (psiElement.textMatches("\$this")) {
            return viewType(settings)
        }

        val relativePath = VfsUtil.getRelativePath(psiFile.virtualFile, templateDir.psiDirectory.virtualFile)
            ?: return null
        return PhpType().add("#${getKey()}.${relativePath}")
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val relativePath = expression.substring(2)
        if (relativePath.contains(getKey())) {
            // ensure no recursion:
            return null
        }

        return null
    }

    override fun getBySignature(
        expression: String,
        set: Set<String>,
        i: Int,
        project: Project
    ): Collection<PhpNamedElement> {
        // We use the default signature processor exclusively:
        return emptyList()
    }

}
