package com.daveme.chocolateCakePHP

import com.daveme.chocolateCakePHP.cake.AllViewPaths
import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.daveme.chocolateCakePHP.controller.CreateViewFileAction
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpPresentationUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.SwingUtilities


fun showErrorDialog(error: String, dialogTitle: String) {
    ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(error, dialogTitle)
    }
}

fun MouseEvent.getScreenPoint(): Point? {
    // Convert the point relative to the component to a screen point
    val point = this.point
    val component = this.component
    return if (component != null) {
        SwingUtilities.convertPointToScreen(point, component)
        point
    } else {
        null
    }
}

fun showPsiFilePopup(
    files: List<PsiFile>,
    project: Project,
    point: RelativePoint
) {
    val elements = files
        .sortedBy { it.virtualFile.path }
        .toTypedArray<PsiElement>()
    PsiTargetNavigator(elements)
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
        .show(point)
}

fun showPsiFilePopupFromEditor(
    files: List<PsiFile>,
    project: Project,
    editor: Editor
) {
    val elements = files
        .sortedBy { it.virtualFile.path }
        .toTypedArray<PsiElement>()
    PsiTargetNavigator(elements)
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
        .showInBestPositionFor(editor)
}

fun showPsiElementPopupFromEditor(
    places: List<PsiElement>,
    project: Project,
    editor: Editor,
    relativePoint: RelativePoint?
) {
    val popup = PsiTargetNavigator(places.toTypedArray())
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
    if (relativePoint != null) {
        popup.show(relativePoint)
    } else {
        popup.showInBestPositionFor(editor)
    }
}

fun createViewActionPopupFromAllViewPaths(
    allViewPaths: AllViewPaths,
    useAltLabel: Boolean = false,
): DefaultActionGroup {
    val defaultActionGroup = DefaultActionGroup()
    val defaultOptionTitle = if (useAltLabel)
        "Create ${allViewPaths.defaultViewPath.altLabel}"
    else
        "Create ${allViewPaths.defaultViewPath.label}"
    defaultActionGroup.add(
        CreateViewFileAction(
            title = defaultOptionTitle,
            destinationPath = allViewPaths.defaultViewPath.fullPath,
            allowEdit = false
        )
    )
    allViewPaths.otherViewPaths.map { otherViewPath ->
        defaultActionGroup.add(
            CreateViewFileAction(
                title = "Create ${otherViewPath.label}",
                destinationPath = otherViewPath.fullPath,
                allowEdit = false
            )
        )
    }
    defaultActionGroup.addSeparator()
    defaultActionGroup.add(
        CreateViewFileAction(
            title = "Create Custom View File",
            destinationPath = allViewPaths.defaultViewPath.fullPath,
            allowEdit = true
        )
    )
    allViewPaths.dataViewPaths.map { dataViewPath ->
        defaultActionGroup.add(
            CreateViewFileAction(
                title = "Create ${dataViewPath.label} View File",
                destinationPath = dataViewPath.fullPath,
                allowEdit = false
            )
        )
    }
    return defaultActionGroup
}

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