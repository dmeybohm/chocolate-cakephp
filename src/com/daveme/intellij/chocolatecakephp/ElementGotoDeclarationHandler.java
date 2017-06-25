package com.daveme.intellij.chocolatecakephp;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class ElementGotoDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        Project project = psiElement.getProject();
        Collection<VirtualFile> files = new HashSet<>();
        System.out.println("text: "+psiElement.getText());
        if (!psiElement.getText().equals("element")) {
            return new PsiElement[0];
        }
        VirtualFile relativeFile = VfsUtil.findRelativeFile(project.getBaseDir(), "goto-test/element.php".split("/"));
        System.out.println("baseDir: "+project.getBaseDir().getCanonicalPath());
        System.out.println("relativeFile: "+relativeFile);
        if (relativeFile != null) {
            files.add(relativeFile);
            return PsiUtil.convertVirtualFilesToPsiFiles(project, files).toArray(new PsiElement[files.size()]);
        }
        System.out.println("ElementGotoDeclarationHandler");
        return new PsiElement[0];
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
