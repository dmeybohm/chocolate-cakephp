package com.daveme.intellij.chocolateCakePHP.completion;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ControllerCompletionContributor extends CompletionContributor {
    public ControllerCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withParent(FieldReference.class), new ControllerCompletionProvider());
        extend(CompletionType.SMART,
                PlatformPatterns.psiElement().withParent(FieldReference.class), new ControllerCompletionProvider());
    }

    private static class ControllerCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
            System.out.println("Add Completions");
            PsiElement originalPosition = completionParameters.getOriginalPosition();
            if (originalPosition == null) {
                System.out.println("null originalPosition");
                return;
            }
            PsiElement psiElement = originalPosition.getOriginalElement();
            if (psiElement == null) {
                System.out.println("null original element");
                return;
            }
            PsiFile containingFile = psiElement.getContainingFile();
            PsiDirectory appDir = PsiUtil.getAppDirectoryFromFile(containingFile);
            if (appDir == null) {
                return;
            }
            PsiDirectory controllerDir = appDir.findSubdirectory("Controller");
            PsiElement parent = psiElement.getParent();
            FieldReference fieldReference;
            if (!(parent instanceof FieldReference)) {
                parent = findSiblingFieldReference(psiElement);
                if (parent == null) {
                    System.out.println("Couldn't find childFieldReference");
                    return;
                }
            }
            fieldReference = (FieldReference)parent;
            PhpExpression classReference = fieldReference.getClassReference();
            if (classReference == null) {
                return;
            }
            boolean hasController = false;
            for (String type : classReference.getType().getTypes()) {
                if (type.contains("Controller")) {
                    hasController = true;
                }
            }
            System.out.println("hasController: "+hasController);
            if (hasController) {
                System.out.println("hasController");
                CakeUtil.completeFromFilesInDir(completionResultSet, appDir, "Model");
                if (controllerDir != null) {
                    CakeUtil.completeFromFilesInDir(completionResultSet, controllerDir, "Component", "Component");
                }
            }
        }
    }

    @Nullable
    private static PsiElement findSiblingFieldReference(PsiElement element) {
        PsiElement prevSibling = element.getPrevSibling();
        if (prevSibling == null) {
            return null;
        }
        for (PsiElement child : prevSibling.getChildren()) {
            if (child instanceof FieldReference) {
                return child;
            }
        }
        return null;
    }
}
