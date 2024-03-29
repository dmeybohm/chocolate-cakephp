package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class TableLocatorTypeProvider : PhpTypeProvider4 {

    companion object {
        const val typeProviderChar = '\u8316'
        const val recursiveStart = "#" + typeProviderChar
    }

    override fun getKey(): Char {
        return typeProviderChar
    }

    override fun getType(psiElement: PsiElement?): PhpType? {
        if (psiElement !is MethodReference) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return null
        }

        // If the method name is "fetchTable" or "get", queue a lookup:
        val name = psiElement.name
        if (name.equals("fetchTable", ignoreCase = true)) {
            //
            // Find the first argument to the method:
            //
            val classReference = psiElement.classReference ?: return null
            val referenceType = classReference.type.filterUnknown()
            for (type in referenceType.types) {
                if (type.isAnyControllerClass()) {
                    val firstParam = psiElement.parameters.firstOrNull() as? StringLiteralExpression ?: return null
                    val contents = firstParam.contents
                    if (contents.length < 255) {
                        // sanity check
                        return PhpType().add("${settings.appNamespace}\\Model\\Table\\${contents}Table")
                    }
                }
            }
        }
        else if (name.equals("get")) {
            //
            // Find the first argument to the method:
            //
            val classReference = psiElement.classReference ?: return null
            for (type in classReference.type.types) {
                if (type.hasGetTableLocatorMethodCall()) {
                    val firstParam = psiElement.parameters.firstOrNull()
                            as? StringLiteralExpression ?: return null
                    val contents = firstParam.contents
                    if (contents.length < 255) {
                        // sanity check
                        return PhpType().add("${settings.appNamespace}\\Model\\Table\\${contents}Table")
                    }
                }
            }
        }
        else {

            //
            // Encode the find
            //
            val classReference = psiElement.classReference ?: return null

            //
            // If we already matched the parent of the find expression,
            // return it.
            //
            for (type in classReference.type.types) {
                if (type.isPluginSpecificTypeForQueryBuilder()) {
                    val unwrappedType = type.unwrapFromPluginSpecificTypeForQueryBuilder()
                    return PhpType().add("#${key}.${name}.${unwrappedType}")
                }
                else if (type.startsWith(recursiveStart)) {
                    val wrappedType = type.split('.')[2]
                    return PhpType().add("#${key}.${name}.${wrappedType}")
                }
            }
            if (name.equals("find")) {
                val classRefType = classReference.type.filterUnknown()
                if (!classRefType.isComplete) {
                    return null
                }
                val result = PhpType()
                for (eachClassRefType in classRefType.types) {
                    if (eachClassRefType.startsWith("\\") &&
                        eachClassRefType.isAnyTableClass()
                    ) {
                        result.add(eachClassRefType.wrapInPluginSpecificTypeForQueryBuilder())
                    }
                }
                return result
            }
        }

        return null
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val (_, invokingMethodName, wrappedType) = expression.split('.')

        //
        // Check for $this->fetchTable('Movies')->find('xxx'), and
        // augment the SelectQuery return type with metainformation about
        // which table is included.
        //

        val result = PhpType()

        //
        // For non-find methods, check the return type is "SelectQuery".
        //
        val phpIndex = PhpIndex.getInstance(project)

        // todo: cache method lists
        val cakeFiveClasses = phpIndex.getClassesByFQN("\\Cake\\ORM\\Query\\SelectQuery");
        val cakeFourClasses = phpIndex.getClassesByFQN("\\Cake\\ORM\\Query");

        (cakeFourClasses + cakeFiveClasses).forEach { klass ->
            val method = klass.findMethodByName(invokingMethodName) ?: return@forEach
            val returnType = if (method.type.isComplete)
                method.type
            else
                phpIndex.completeType(project, method.type, null)
            if (returnType == null) {
                return@forEach
            }
            if (returnType.types.any { it.contains("Query", ignoreCase = true) }) {
                result.add(wrappedType.wrapInPluginSpecificTypeForQueryBuilder())
            }
        }

        return result
    }

    override fun getBySignature(
        p0: String?,
        p1: MutableSet<String>?,
        p2: Int,
        p3: Project?
    ): MutableCollection<out PhpNamedElement> {
        return mutableListOf()
    }
}