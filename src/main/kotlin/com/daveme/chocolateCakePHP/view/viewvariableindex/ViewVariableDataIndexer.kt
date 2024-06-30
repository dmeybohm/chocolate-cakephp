package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable


object ViewVariableDataIndexer : DataIndexer<ViewVariablesKey, ViewVariables, FileContent> {

    override fun map(inputData: FileContent): MutableMap<String, ViewVariables> {
        val result = mutableMapOf<String, ViewVariables>()
        val psiFile = inputData.psiFile
//        val project = psiFile.project

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

//        val projectDir = project.guessProjectDir() ?: return result
        if (isCakeControllerFile(psiFile)) {
            indexController(result, psiFile)
        }
        // todo views

        return result
    }

    private fun indexController(
        result: MutableMap<String, ViewVariables>,
        psiFile: PsiFile
    ) {
        val publicMethodCalls = PsiTreeUtil.findChildrenOfType(psiFile, Method::class.java)
            .filter { it.access.isPublic }
        if (publicMethodCalls.isEmpty()) {
            return
        }
        val virtualFile = psiFile.virtualFile

        // Might need this for compact() support
//        val assignments = PsiTreeUtil.findChildrenOfType(psiFile, AssignmentExpression::class.java)
//            .associateBy({ it.variable.name }, { it })

        publicMethodCalls.forEach { methodCall ->
            val variables = ViewVariables()
            val setCalls = PsiTreeUtil.findChildrenOfType(methodCall, MethodReference::class.java)
                .filter {
                    it.name.equals("set", ignoreCase = true) &&
                        (it.firstChild as? Variable)?.name == "this" &&
                            it.parameters.isNotEmpty()
                }

            setCalls.forEach { setCall ->
                //
                // There are a lot of different possible uses to handle of $this->set(), but these are the ones
                // at most we're going to support:
                //   case 1: $this->set('name', $value)
                //   case 2: $this->set(['name' => $value])
                //   case 3: $this->set(compact('value'))
                //   case 4: $this->set(['name1', 'name2'], [$value1, $value2])
                //   case 5: $this->set($caseFive); // where there is an assignment $caseFive = compact('var')
                //   case 6: $this->set($caseSix);  // where there is an assignement $caseSix = ['key' => 'val']
                //   case 7: $this->set($caseSevenKeys, $caseSevenVals) // .. or where either keys or vals is a in situ array
                //
                val firstParam = setCall.parameters.getOrNull(0)
                val secondParam = setCall.parameters.getOrNull(1)

                // case 1:
                if (firstParam is StringLiteralExpression &&
                    secondParam is Variable
                ) {
                    val variableName = firstParam.contents
                    val variableType = secondParam.type

                    variables[variableName] = ViewVariableValue(
                        variableType.toString(),
                        firstParam.textRange.startOffset,
                    )
                }
                // case 2:
                else if (firstParam is ArrayCreationExpression &&
                    secondParam == null
                ) {
                    for (hashElement in firstParam.hashElements) {
                        val key = hashElement.key
                        val value = hashElement.value

                        if (key is StringLiteralExpression) {
                            val variableName = key.contents
                            val variableType : String? = if (value is Variable)
                                value.type.toString()
                            else if (value is StringLiteralExpression)
                                "string"
                            else
                                null
                            if (variableType == null) {
                                continue
                            }
                            variables[variableName] = ViewVariableValue(
                                variableType.toString(),
                                key.textRange.startOffset
                            )
                        }
                    }
                }

                // todo other cases
            }
            val filenameAndMethodKey = controllerMethodKey(
                virtualFile,
                methodCall
            )
            result[filenameAndMethodKey] = variables
        }
    }

}
