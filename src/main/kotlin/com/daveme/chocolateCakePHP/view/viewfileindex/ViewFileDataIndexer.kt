package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.daveme.chocolateCakePHP.isCustomizableViewMethod
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import org.jetbrains.annotations.Unmodifiable

object ViewFileDataIndexer : DataIndexer<String, List<ViewReferenceData>, FileContent> {

    override fun map(inputData: FileContent): MutableMap<String, List<ViewReferenceData>> {
        val result = mutableMapOf<String, List<ViewReferenceData>>()
        val psiFile = inputData.psiFile
        val project = psiFile.project
        val projectDir = project.guessProjectDir() ?: return result
        val settings = Settings.getInstance(project)

        if (!settings.enabled) {
            return result
        }

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        val methodCalls = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
        val renderCalls = methodCalls
            .filter {
                it.name.equals("render", ignoreCase = true)
            }
        val elementCalls = methodCalls
            .filter {
                it.name.equals("element", ignoreCase = true)
            }

        val isController = isCakeControllerFile(psiFile)
        if (
            elementCalls.isEmpty() &&
            renderCalls.isEmpty() &&
            !isController
        ) {
            return result
        }

        indexRenderCalls(result, projectDir, renderCalls, virtualFile)
        indexElementCalls(result, projectDir, elementCalls, virtualFile)

        if (isController) {
            val methods = PsiTreeUtil.findChildrenOfType(psiFile, Method::class.java)
            indexImplicitRender(result, projectDir, settings, methods, virtualFile)
        }

        return result
    }

    private fun indexRenderCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        renderCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = filterRenderOrElementCalls(renderCalls)
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(withThis, viewPathPrefix, result)
    }

    private fun indexElementCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        elementCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = filterRenderOrElementCalls(elementCalls)
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = elementPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(withThis, viewPathPrefix, result)
    }

    private fun filterRenderOrElementCalls(methodCalls: List<MethodReference>): List<MethodReference> {
        val withThis = methodCalls.filter { method ->
            val variable = method.firstChild as? Variable ?: return@filter false
            variable.name == "this" &&
                    method.parameters.isNotEmpty() &&
                    method.parameters.first() is StringLiteralExpression
        }
        return withThis
    }

    private fun indexImplicitRender(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        settings: Settings,
        methods: @Unmodifiable Collection<Method>,
        controllerFile: VirtualFile
    ) {
        // todo check for $this->autoRender = false
        val relevantMethods = methods.filter { it.isCustomizableViewMethod() }
        if (relevantMethods.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, controllerFile)
            ?: return

        val controllerInfo = lookupControllerFileInfo(controllerFile, settings)

        relevantMethods.forEach { method ->
            val fullViewPath = fullImplicitViewPath(
                viewPathPrefix,
                controllerInfo,
                method.name
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = method.name,
                elementType = "Method",
                offset = method.textOffset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

    private fun setViewPath(
        withThis: List<MethodReference>,
        viewPathPrefix: ViewPathPrefix,
        result: MutableMap<String, List<ViewReferenceData>>
    ) {
        for (method in withThis) {
            val parameterName = method.parameters.first() as StringLiteralExpression
            val content = RenderPath(parameterName.contents)

            if (content.path.isEmpty()) {
                continue
            }
            val fullViewPath = fullExplicitViewPath(
                viewPathPrefix,
                content
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = method.name ?: "render",
                elementType = "MethodReference",
                offset = method.textOffset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

}
