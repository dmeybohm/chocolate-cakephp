package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

public class PsiUtil {

    @NotNull
    public static Collection<PsiFile> convertVirtualFilesToPsiFiles(@NotNull Project project, @NotNull Collection<VirtualFile> files) {

        Collection<PsiFile> psiFiles = new HashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            if(psiFile != null) {
                psiFiles.add(psiFile);
            }
        }

        return psiFiles;
    }

    public static PsiFile convertVirtualFileToPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
        PsiManager psiManager = PsiManager.getInstance(project);
        return psiManager.findFile(file);
    }
}
