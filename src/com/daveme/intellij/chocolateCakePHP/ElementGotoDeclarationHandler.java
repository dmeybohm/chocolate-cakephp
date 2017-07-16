package com.daveme.intellij.chocolateCakePHP;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
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

public class ElementGotoDeclarationHandler implements GotoDeclarationHandler {

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
        String path = containingFile.getVirtualFile().getCanonicalPath();
        String elementPath = String.format("%s/View/Elements/%s.ctp", getAppDir(path), psiElement.getText());
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
    private String getAppDir(String path) {
        StringBuilder result = new StringBuilder();
        for (String part : path.split("/")) {
            result.append(part);
            if (part.equals("app")) {
                return result.toString();
            }
            result.append("/");
        }
        return null;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}