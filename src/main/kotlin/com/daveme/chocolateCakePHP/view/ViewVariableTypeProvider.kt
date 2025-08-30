package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ViewVariableTypeProvider : PhpTypeProvider4 {

    private val SEPARATOR = '\u8300'

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
        val templateDir = templatesDirectoryOfViewFile(psiElement.project, settings, psiFile)
            ?: return null

        if (psiElement.textMatches("\$this")) {
            return viewType(settings)
        }

        val relativePath = VfsUtil.getRelativePath(
            psiFile.originalFile.virtualFile,
            templateDir.directory
        ) ?: return null

        val cakeVersion = when (templateDir) {
            is CakeFourTemplatesDir -> 4
            is CakeThreeTemplatesDir -> 3
            is CakeTwoTemplatesDir -> 2
        }
        val name = psiElement.name
        val incompleteType = "#${getKey()}v$cakeVersion" + SEPARATOR +
            relativePath.substringBeforeLast(".") + SEPARATOR +
            name
        return PhpType().add(incompleteType)
    }

    override fun complete(
        expression: String,
        project: Project
    ): PhpType? {
        if (expression.length < 3) {
            return null
        }
        val encoded = expression.substringOrNull(2)
            ?: return null
        val (_, relativePath, varName) = encoded.split(SEPARATOR)
        if (relativePath.contains(getKey())) {
            // ensure no recursion:
            return null
        }

        val settings = Settings.getInstance(project)

        val type = ViewVariableIndexService.lookupVariableTypeFromViewPathInSmartReadAction(
            project,
            settings,
            relativePath,
            varName
        )
        if (type.types.size > 0) {
            return type
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
