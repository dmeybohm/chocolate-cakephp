package com.daveme.intellij.chocolateCakePHP.navigation;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class TemplateGotoDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        Project project = psiElement.getProject();
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression.class)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.getContext())
        ) {
            return PsiElement.EMPTY_ARRAY;
        }
        PsiFile containingFile = psiElement.getContainingFile();
        VirtualFile virtualFile = containingFile.getVirtualFile();
        String filename = virtualFile.getNameWithoutExtension();
        String controllerName = CakeUtil.controllerBaseNameFromControllerFileName(filename);
        if (controllerName == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        String canonicalPath = virtualFile.getCanonicalPath();
        if (canonicalPath == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        String appDir = StringUtil.lastOccurrenceOf(canonicalPath, "app");
        if (appDir == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        String elementPath = String.format("%s/View/%s/%s.ctp", appDir, controllerName, psiElement.getText());
        VirtualFileManager vfManager = VirtualFileManager.getInstance();
        VirtualFile fileByUrl = vfManager.findFileByUrl(VirtualFileManager.constructUrl("file", elementPath));
        if (fileByUrl != null) {
            Collection<VirtualFile> files = new HashSet<>();
            files.add(fileByUrl);
            return PsiUtil.convertVirtualFilesToPsiFiles(project, files).toArray(new PsiElement[files.size()]);
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}

