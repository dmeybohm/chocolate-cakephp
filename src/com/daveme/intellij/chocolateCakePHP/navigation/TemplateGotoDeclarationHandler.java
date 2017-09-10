package com.daveme.intellij.chocolateCakePHP.navigation;

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
            return new PsiElement[0];
        }
        Project project = psiElement.getProject();
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression.class)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.getContext())
        ) {
            return new PsiElement[0];
        }
        PsiFile containingFile = psiElement.getContainingFile();
        VirtualFile virtualFile = containingFile.getVirtualFile();
        String filename = virtualFile.getNameWithoutExtension();
        String controllerName = StringUtil.controllerBaseNameFromControllerFileName(filename);
        if (controllerName == null) {
            return new PsiElement[0];
        }
        String appDir = StringUtil.lastOccurrenceOf(virtualFile.getCanonicalPath(), "app");
        if (appDir == null) {
            return new PsiElement[0];
        }
        String elementPath = String.format("%s/View/%s/%s.ctp", appDir, controllerName, psiElement.getText());
        VirtualFileManager vfManager = VirtualFileManager.getInstance();
        VirtualFile fileByUrl = vfManager.findFileByUrl(VirtualFileManager.constructUrl("file", elementPath));
        if (fileByUrl != null) {
            Collection<VirtualFile> files = new HashSet<>();
            files.add(fileByUrl);
            return PsiUtil.convertVirtualFilesToPsiFiles(project, files).toArray(new PsiElement[files.size()]);
        }
        return new PsiElement[0];
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
