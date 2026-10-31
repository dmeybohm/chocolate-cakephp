package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
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
        templatesDirectoryOfViewFile(psiElement.project, settings, psiFile)
            ?: return null

        if (psiElement.textMatches("\$this")) {
            return viewType(settings)
        }

        val file = psiFile.originalFile.virtualFile ?: return null
        val viewFile = viewFileOf(psiElement.project, settings, file) ?: return null
        val incompleteType = "#${getKey()}" + viewFile.encode(SEPARATOR) + SEPARATOR + psiElement.name
        return PhpType().add(incompleteType)
    }

    override fun complete(
        expression: String,
        project: Project
    ): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }

        if (expression.length < 3) {
            return null
        }
        val encoded = expression.substringOrNull(2)
            ?: return null
        if (encoded.contains(getKey())) {
            // ensure no recursion:
            return null
        }
        val viewFile = ViewFile.decode(encoded.substringBeforeLast(SEPARATOR), SEPARATOR)
            ?: return null
        val varName = encoded.substringAfterLast(SEPARATOR)

        val settings = Settings.getInstance(project)

        val type = ViewVariableIndexService.lookupVariableTypeFromViewPathInSmartReadAction(
            project,
            settings,
            viewFile,
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
