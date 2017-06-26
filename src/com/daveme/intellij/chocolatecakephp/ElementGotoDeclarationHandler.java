package com.daveme.intellij.chocolatecakephp;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class ElementGotoDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return new PsiElement[0];
        }
        Project project = psiElement.getProject();
        Collection<VirtualFile> files = new HashSet<>();
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression.class)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.getContext())
        ) {
            return new PsiElement[0];
        }
        String elementPath = String.format("app/View/Elements/%s.ctp", psiElement.getText());
        VirtualFile relativeFile = VfsUtil.findRelativeFile(project.getBaseDir(), elementPath.split("/"));
        if (relativeFile != null) {
            files.add(relativeFile);
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
