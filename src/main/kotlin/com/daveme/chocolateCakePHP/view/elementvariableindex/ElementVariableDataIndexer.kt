package com.daveme.chocolateCakePHP.view.elementvariableindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.psi.elements.*
import org.jetbrains.annotations.Unmodifiable


object ElementVariableDataIndexer : DataIndexer<ElementVariablesKey, ElementVariables, FileContent> {

    private const val FALLBACK_ELEMENT_VARIABLE_TYPE = "mixed"

    override fun map(inputData: FileContent): MutableMap<String, ElementVariables> {
        val result = mutableMapOf<String, ElementVariables>()
        val psiFile = inputData.psiFile
        val settings = Settings.getInstance(psiFile.project)
        val project = psiFile.project
        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        if (isCakeViewFile(project, settings, psiFile)) {
            indexViewFile(result, psiFile)
        }

        return result
    }

    private fun isCompactCall(functionRef: FunctionReference): Boolean =
        functionRef.name.equals("compact", ignoreCase = true)

    private fun indexViewFile(
        result: MutableMap<String, ElementVariables>,
        psiFile: PsiFile
    ) {
        val methodCalls = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
        val elementCalls = methodCalls
            .filter {
                it.name.equals("element", ignoreCase = true)
            }
        if (elementCalls.isEmpty()) {
            return
        }

        // Needed for $this->set(compact('var')) support to lookup the types of the variables:
        val assignments = PsiTreeUtil.findChildrenOfType(psiFile, AssignmentExpression::class.java)

        elementCalls.forEach { elementCall ->
            val variables = ElementVariables()
            setVariablesFromElementCall(variables, elementCall, assignments)

            val firstParam = elementCall.parameters.firstOrNull()
                ?: return@forEach
            val contents = (firstParam as? StringLiteralExpression)?.contents
                ?: return@forEach

            if (variables.isNotEmpty()) {
                result[contents] = variables
            }
        }
    }

    private fun setVariablesFromElementCall(
        result: ElementVariables,
        elementCall: MethodReference,
        assignments: @Unmodifiable Collection<AssignmentExpression>,
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

        val secondParam = elementCall.parameters.getOrNull(1)
            ?: return

        // case 2: $this->set(['name' => $value])
        if (
            secondParam is ArrayCreationExpression
        ) {
            setVariablesFromArrayCreationExpression(result, secondParam)
        }

        // case 3: $this->set(compact('value'))
        else if (
            secondParam is FunctionReference &&
            isCompactCall(secondParam)
        ) {
            val stringVals = secondParam.parameters.mapNotNull {
                (it as? StringLiteralExpression)?.contents
            }
            stringVals.forEach { variableName ->
                setVariablesFromCompactFunctionCall(
                    result,
                    assignments,
                    variableName,
                    secondParam,
                )
            }
        }

        // case 5: $this->set($caseFive); // where there is an assignment $caseFive = compact('var')
        // case 6: $this->set($caseSix);  // where there is an assignement $caseSix = ['key' => 'val']
        else if (
            secondParam is Variable
        ) {
            val indirectSetValueName = secondParam.name
            val relevantAssignments = assignments.filter { it.variable?.name == indirectSetValueName }
            relevantAssignments.forEach { assignment ->
                // case 5:
                val value = assignment.value
                if (value is FunctionReference && isCompactCall(value)) {
                    val stringVals = value.parameters.mapNotNull {
                        (it as? StringLiteralExpression)?.contents
                    }
                    stringVals.forEach { variableName ->
                        setVariablesFromCompactFunctionCall(
                            result,
                            assignments,
                            variableName,
                            value,
                        )
                    }
                } else if (value is ArrayCreationExpression) {
                    setVariablesFromArrayCreationExpression(
                        result,
                        value
                    )
                }
            }
        }
        //
        // todo
        //   case 7: $this->set($caseSevenKeys, $caseSevenVals) // .. or where either keys or vals is a in situ array
        //
    }

    private fun setVariablesFromArrayCreationExpression(
        result: ElementVariables,
        arrayCreationExpression: ArrayCreationExpression
    ) {
        for (hashElement in arrayCreationExpression.hashElements) {
            val key = hashElement.key
            val value = hashElement.value

            if (key is StringLiteralExpression) {
                val variableName = key.contents
                val variableType: String? = when (value) {
                    is Variable -> value.type.toString()
                    is StringLiteralExpression -> "string"
                    else -> null
                }
                if (variableType == null) {
                    continue
                }
                result[variableName] = ElementVariableValue(
                    variableType.toString(),
                    key.textRange.startOffset
                )
            }
        }
    }

    private fun setVariablesFromCompactFunctionCall(
        result: ElementVariables,
        assignments: @Unmodifiable Collection<AssignmentExpression>,
        variableName: String,
        compactCall: FunctionReference,
    ) {
        val relevantAssignments = assignments.filter { it.variable?.name == variableName }
        if (relevantAssignments.isNotEmpty()) {
            relevantAssignments.forEach { assignment ->
                val variableType = if (assignment.variable is PhpTypedElement)
                    assignment.type.toString()
                else
                    FALLBACK_ELEMENT_VARIABLE_TYPE

                result[variableName] = ElementVariableValue(
                    variableType,
                    compactCall.textRange.startOffset,
                )
            }
        } else {
            // TODO - the variable is not found - so mark it specially here.
            result[variableName] = ElementVariableValue(
                FALLBACK_ELEMENT_VARIABLE_TYPE,
                compactCall.textRange.startOffset,
            )
        }
    }

}
