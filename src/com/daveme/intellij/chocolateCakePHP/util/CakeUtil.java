package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.completion.PhpVariantsUtil;
import com.jetbrains.php.completion.UsageContext;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CakeUtil {

    public static void complete(
            @NotNull Collection<PhpClass> classes,
            @NotNull PhpClass fromClass,
            @NotNull CompletionResultSet completionResultSet) {
        if (classes.size() == 0) {
            return;
        }
        UsageContext usageContext = new UsageContext(PhpModifier.State.DYNAMIC);
        usageContext.setTargetObjectClass(fromClass);
        for (PhpClass klass: classes) {
            try {
                List<LookupElement> lookupItems = PhpVariantsUtil.getLookupItems(klass.getMethods(), false, usageContext);
                completionResultSet.addAllElements(lookupItems);
            } catch (Exception e) {

            }
            try {
                List<LookupElement> lookupElements = PhpVariantsUtil.getLookupItems(klass.getFields(), false, usageContext);
                completionResultSet.addAllElements(lookupElements);
            } catch (Exception e) {

            }
        }
    }

    @Nullable
    public static String controllerBaseNameFromControllerFileName(@NotNull String controllerClass) {
        if (!controllerClass.endsWith("Controller")) {
            return null;
        }
        return controllerClass.substring(0, controllerClass.length() - "Controller".length());
    }

    public static void completeFromFilesInDir(@NotNull CompletionResultSet completionResultSet,
                                               @NotNull PsiDirectory appDir,
                                               @NotNull String subDir) {
        PsiDirectory modelDir = appDir.findSubdirectory(subDir);
        if (modelDir == null) {
            return;
        }
        for (PsiFile file : modelDir.getFiles()) {
            // psi tree is different when the user has typed something after the arrow ->
            // vs not having typed anything:
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                continue;
            }
            String name = virtualFile.getNameWithoutExtension();
            LookupElementBuilder lookupElement = LookupElementBuilder.create(name)
                    .withIcon(PhpIcons.FIELD)
                    .withTypeText(name)
                    .withPresentableText(name);
            completionResultSet.addElement(lookupElement);
        }
    }

}
