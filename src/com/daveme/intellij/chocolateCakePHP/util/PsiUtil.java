package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    @Nullable
    public static String getFileNameWithoutExtension(PsiElement psiElement) {
        PsiFile file = psiElement.getContainingFile();
        if (file == null) {
            return null;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        return virtualFile.getNameWithoutExtension();
    }

    @NotNull
    public static Collection<PhpClass> getControllerFieldClasses(String fieldName, Project project) {
        Collection<PhpClass> result = new ArrayList<>();
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Collection<PhpClass> modelClasses =  phpIndex.getClassesByFQN(fieldName);
        Collection<PhpClass> componentClasses = phpIndex.getClassesByFQN(fieldName + "Component");
        result.addAll(modelClasses);
        result.addAll(componentClasses);
        return result;
    }
}
