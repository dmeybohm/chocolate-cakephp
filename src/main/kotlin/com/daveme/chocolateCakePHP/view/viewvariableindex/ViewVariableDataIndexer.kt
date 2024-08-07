package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.cake.controllerPathFromControllerFile
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.daveme.chocolateCakePHP.isCustomizableViewMethod
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.psi.elements.*
import org.jetbrains.annotations.Unmodifiable


object ViewVariableDataIndexer : DataIndexer<ViewVariablesKey, ViewVariables, FileContent> {

    private const val FALLBACK_VIEW_VARIABLE_TYPE = "mixed"

    override fun map(inputData: FileContent): MutableMap<String, ViewVariables> {
        val result = mutableMapOf<String, ViewVariables>()
        val psiFile = inputData.psiFile

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        if (isCakeControllerFile(psiFile)) {
            indexController(result, psiFile, virtualFile)
        }

        return result
    }

    private fun isCompactCall(functionRef: FunctionReference): Boolean =
        functionRef.name.equals("compact", ignoreCase = true)

    private fun indexController(
        result: MutableMap<String, ViewVariables>,
        psiFile: PsiFile,
        virtualFile: VirtualFile
    ) {
        val controllerPath = controllerPathFromControllerFile(virtualFile) ?: return
        val publicMethods = PsiTreeUtil.findChildrenOfType(psiFile, Method::class.java)
            .filter { it.isCustomizableViewMethod() }
        if (publicMethods.isEmpty()) {
            return
        }

        // Needed for $this->set(compact('var')) support to lookup the types of the variables:
        val assignments = PsiTreeUtil.findChildrenOfType(psiFile, AssignmentExpression::class.java)

        publicMethods.forEach { method ->
            val variables = ViewVariables()
            val setCalls = PsiTreeUtil.findChildrenOfType(method, MethodReference::class.java)
                .filter {
                    it.name.equals("set", ignoreCase = true) &&
                        (it.firstChild as? Variable)?.name == "this" &&
                            it.parameters.isNotEmpty()
                }

            setCalls.forEach { setCall ->
                setVariablesFromControllerSetCall(variables, setCall, assignments, method)
            }
            val filenameAndMethodKey = controllerMethodKey(
                controllerPath,
                method.name
            )
            result[filenameAndMethodKey] = variables
        }
    }

    private fun setVariablesFromControllerSetCall(
        result: ViewVariables,
        setCall: MethodReference,
        assignments: @Unmodifiable Collection<AssignmentExpression>,
        method: Method
    ) {
        //
        // There are a lot of different possible uses to handle of $this->set(), but these are the ones
        // at most we're going to support:
        //
        //   case 1: $this->set('name', $value)
        //   case 2: $this->set(['name' => $value])
        //   case 3: $this->set(compact('value'))
        //   case 4: $this->set(['name1', 'name2'], [$value1, $value2])
        //   case 5: $this->set($caseFive); // where there is an assignment $caseFive = compact('var')
        //   case 6: $this->set($caseSix);  // where there is an assignment $caseSix = ['key' => 'val']
        //   case 7: $this->set($caseSevenKeys, $caseSevenVals) // .. or where either keys or vals is a in situ array
        //

        val firstParam = setCall.parameters.getOrNull(0)
        val secondParam = setCall.parameters.getOrNull(1)

        // case 1: $this->set('name', $value)
        if (
            firstParam is StringLiteralExpression &&
            secondParam is Variable
        ) {
            val variableName = firstParam.contents
            val variableType = secondParam.type

            result[variableName] = ViewVariableValue(
                variableType.toString(),
                firstParam.textRange.startOffset,
            )
        }

        // case 2: $this->set(['name' => $value])
        else if (
            firstParam is ArrayCreationExpression &&
            secondParam == null
        ) {
            setVariablesFromArrayCreationExpressionInController(result, firstParam)
        }

        // case 3: $this->set(compact('value'))
        else if (
            firstParam is FunctionReference &&
            isCompactCall(firstParam) &&
            secondParam == null
        ) {
            val stringVals = firstParam.parameters.mapNotNull {
                (it as? StringLiteralExpression)?.contents
            }
            stringVals.forEach { variableName ->
                setVariablesFromCompactFunctionCallInController(
                    result,
                    assignments,
                    variableName,
                    firstParam,
                    method
                )
            }
        }

        // case 5: $this->set($caseFive); // where there is an assignment $caseFive = compact('var')
        // case 6: $this->set($caseSix);  // where there is an assignement $caseSix = ['key' => 'val']
        else if (
            firstParam is Variable &&
            secondParam == null
        ) {
            val indirectSetValueName = firstParam.name
            val relevantAssignments = assignments.filter { it.variable?.name == indirectSetValueName }
            relevantAssignments.forEach { assignment ->
                // case 5:
                val value = assignment.value
                if (value is FunctionReference && isCompactCall(value)) {
                    val stringVals = value.parameters.mapNotNull {
                        (it as? StringLiteralExpression)?.contents
                    }
                    stringVals.forEach { variableName ->
                        setVariablesFromCompactFunctionCallInController(
                            result,
                            assignments,
                            variableName,
                            value,
                            method
                        )
                    }
                } else if (value is ArrayCreationExpression) {
                    setVariablesFromArrayCreationExpressionInController(
                        result,
                        value
                    )
                }
            }
        }
        // case 4: $this->set(['name1', 'name2'], [$value1, $value2])
        else if (
            firstParam is ArrayCreationExpression &&
            secondParam is ArrayCreationExpression
        ) {
            val keys = firstParam.children.mapNotNull {
                it.firstChild as? StringLiteralExpression
            }
        val vals = secondParam.children.mapNotNull {
                it.firstChild
            }
            val combined = keys.zip(vals)
            combined.forEach { (keyElement, valueElement) ->
                val variableName = (keyElement as? StringLiteralExpression)?.contents
                    ?: return@forEach
                val variableType = if (valueElement is PhpTypedElement)
                    valueElement.type.toString()
                else
                    FALLBACK_VIEW_VARIABLE_TYPE
                result[variableName] = ViewVariableValue(
                    variableType,
                    firstParam.textRange.startOffset
                )
            }
        }
        //
        // todo
        //   case 7: $this->set($caseSevenKeys, $caseSevenVals) // .. or where either keys or vals is a in situ array
        //
    }

    private fun setVariablesFromArrayCreationExpressionInController(
        result: ViewVariables,
        arrayCreationExpression: ArrayCreationExpression
    ) {
        for (hashElement in arrayCreationExpression.hashElements) {
            val key = hashElement.key
            val value = hashElement.value

            if (key is StringLiteralExpression) {
                val variableName = key.contents
                val variableType: String? = if (value is Variable)
                    value.type.toString()
                else if (value is StringLiteralExpression)
                    "string"
                else
                    null
                if (variableType == null) {
                    continue
                }
                result[variableName] = ViewVariableValue(
                    variableType.toString(),
                    key.textRange.startOffset
                )
            }
        }
    }

    private fun setVariablesFromCompactFunctionCallInController(
        result: ViewVariables,
        assignments: @Unmodifiable Collection<AssignmentExpression>,
        variableName: String,
        compactCall: FunctionReference,
        controllerMethod: Method
    ) {
        val relevantAssignments = assignments.filter { it.variable?.name == variableName }
        if (relevantAssignments.isNotEmpty()) {
            relevantAssignments.forEach { assignment ->
                val variableType = if (assignment.variable is PhpTypedElement)
                    assignment.type.toString()
                else
                    FALLBACK_VIEW_VARIABLE_TYPE

                result[variableName] = ViewVariableValue(
                    variableType,
                    compactCall.textRange.startOffset,
                )
            }
        } else {
            // Check for parameters in the controller method definition itself:
            val relevantParams = controllerMethod.parameters.filter { it.name == variableName }
            val param = relevantParams.firstOrNull()
            if (param != null) {
                result[variableName] = ViewVariableValue(
                    param.type.toString(),
                    param.textRange.startOffset
                )
            }
        }
    }

}
