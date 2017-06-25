package com.daveme.intellij.chocolatecakephp;

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

        PsiManager psiManager = null;
        for (VirtualFile file : files) {

            if(psiManager == null) {
                psiManager = PsiManager.getInstance(project);
            }

            PsiFile psiFile = psiManager.findFile(file);
            if(psiFile != null) {
                psiFiles.add(psiFile);
            }
        }

        return psiFiles;
    }
}
