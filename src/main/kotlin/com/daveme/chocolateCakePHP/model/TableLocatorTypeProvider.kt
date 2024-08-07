package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.controller.ControllerFieldTypeProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class TableLocatorTypeProvider : PhpTypeProvider4 {

    companion object {
        const val TYPE_PROVIDER_CHAR = '\u8316'
        const val RECURSIVE_START = "#$TYPE_PROVIDER_CHAR"
    }

    override fun getKey(): Char {
        return TYPE_PROVIDER_CHAR
    }

    override fun getType(psiElement: PsiElement?): PhpType? {
        if (psiElement !is MethodReference) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.cake3Enabled) {
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
                    val tableClass = getTableClass(psiElement, settings)
                    if (tableClass != null) {
                        return tableClass
                    }
                }
            }
        }
        else if (name.equals("get", ignoreCase = true)) {
            //
            // Find the first argument to the method:
            //
            val classReference = psiElement.classReference ?: return null
            val result = PhpType()
            for (type in classReference.type.types) {
                if (
                    type.hasGetTableLocatorMethodCall() ||
                    (psiElement.isStatic && type.isTableRegistryClass())
                ) {
                    // $this->getTableLocator()->get("Movies")
                    // TableRegistry::get("Movies")
                    val tableClass = getTableClass(psiElement, settings)
                    if (tableClass != null) {
                        return tableClass
                    }
                }
                else if (type.startsWith("\\") && type.isAnyTableClass()) {
                    // $this->Movies->get():
                    return PhpType().add(type.tableToEntityClass())
                }
                else if (type.startsWith("#${ControllerFieldTypeProvider.TYPE_PROVIDER_CHAR}")) {
                    // $this->Movies->Articles->get():
                    val wrappedType = type.split(ControllerFieldTypeProvider.TYPE_PROVIDER_CHAR).last()
                    return PhpType().add("#${key}.${name}.${wrappedType}")
                }
            }
            return result
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
                else if (type.startsWith(RECURSIVE_START)) {
                    val wrappedType = type.split('.')[2]
                    return PhpType().add("#${key}.${name}.${wrappedType}")
                }
            }
            if (name.equals("find", ignoreCase = true)) {
                val classRefType = classReference.type.filterUnknown()

                // TODO: handle incomplete types here by deferring lookup to
                //       complete method
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

    private fun getTableClass(methodRef: MethodReference, settings: Settings): PhpType? {
        val parameters = methodRef.parameters
        return when (parameters.size) {
            0 -> null
            1 -> getTableClassFromFirstParam(parameters, settings)
            else -> getTableClassFromSecondParam(parameters) ?:
                    getTableClassFromFirstParam(parameters, settings)
        }
    }

    private fun getTableClassFromFirstParam(parameters: Array<out PsiElement>, settings: Settings): PhpType? {
        val firstParam = parameters.firstOrNull()
                as? StringLiteralExpression ?: return null
        val contents = firstParam.contents
        if (contents.length < 255) {
            // sanity check
            return PhpType().add("${settings.appNamespace}\\Model\\Table\\${contents}Table")
        }
        return null
    }

    private fun getTableClassFromSecondParam(parameters: Array<out PsiElement>): PhpType? {
        val arrayCreationExpr = parameters[1] as? ArrayCreationExpression ?: return null
        for (element in arrayCreationExpr.hashElements) {
            val key = element.key as? StringLiteralExpression ?: continue
            if (key.contents == "className") {
                val contents = element.value?.getStringifiedClassOrNull() ?: continue
                if (contents.length < 255) {
                    // sanity check
                    return PhpType().add(contents)
                }
            }
        }
        return null
    }

    override fun complete(expression: String, project: Project): PhpType {
        val (_, invokingMethodName, wrappedType) = expression.split('.')

        //
        // Check for $this->fetchTable('Movies')->find('xxx'), and
        // augment the SelectQuery return type with metainformation about
        // which table is included.
        //

        val result = PhpType()
        val settings = Settings.getInstance(project)

        //
        // For non-find methods, check the return type is "SelectQuery".
        //
        val phpIndex = PhpIndex.getInstance(project)
        if (
            invokingMethodName.equals("all", ignoreCase = true) &&
            wrappedType.isAnyTableClass()
        ) {
            val entityClass = wrappedType.tableToEntityClass()
            return PhpType().add("$entityClass[]")
        } else if (
            invokingMethodName.equals("get", ignoreCase = true)
        ) {
            val classes = phpIndex.getPossibleTableClasses(settings, wrappedType)
            classes.forEach { klass -> result.add(klass.fqn.tableToEntityClass()) }
            return result
        }


        // todo: cache method lists
        val cakeFiveClasses = phpIndex.getClassesByFQN("\\Cake\\ORM\\Query\\SelectQuery")
        val cakeFourClasses = phpIndex.getClassesByFQN("\\Cake\\ORM\\Query")

        (cakeFourClasses + cakeFiveClasses).forEach { klass ->
            val method = klass.findMethodByName(invokingMethodName) ?: return@forEach
            val returnType = method.type.lookupCompleteType(project, phpIndex, null)
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