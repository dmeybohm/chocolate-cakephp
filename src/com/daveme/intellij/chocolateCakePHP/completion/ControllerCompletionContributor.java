package com.daveme.intellij.chocolateCakePHP.completion;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.daveme.intellij.chocolateCakePHP.util.VfsUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.NotNull;

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
//            if (!(psiElement instanceof PsiWhiteSpace)) {
//                System.out.println("Non whitespace element");
//                System.out.println("interfaces: " + StringUtil.allInterfaces(psiElement.getClass()));
//                return;
//            }
            PsiFile containingFile = psiElement.getContainingFile();
            PsiDirectory appDir = PsiUtil.getAppDirectoryFromFile(containingFile);
            if (appDir == null) {
                return;
            }
            PsiDirectory controllerDir = appDir.findSubdirectory("Controller");
            PsiElement parent = psiElement.getParent();
            if (!(parent instanceof FieldReference)) {
                System.out.println("Non field reference parent");
                return;
            }
            FieldReference fieldReference = (FieldReference)parent;
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
            if (hasController) {
                System.out.println("hasController");
                CakeUtil.completeFromFilesInDir(completionResultSet, appDir, "Model");
                if (controllerDir != null) {
                    CakeUtil.completeFromFilesInDir(completionResultSet, controllerDir, "Component");
                }
            }

        }
    }

}
