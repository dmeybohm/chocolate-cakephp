package com.daveme.intellij.chocolateCakePHP.completion;

import com.daveme.intellij.chocolateCakePHP.util.*;
import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ControllerCompletionContributor extends CompletionContributor {

    public static PhpType CONTROLLER_TYPE = PhpType.builder().add("\\AppController").build();

    public ControllerCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(FieldReference.class), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
                PsiElement psiElement = completionParameters.getPosition().getOriginalElement();
                PsiElement parent = psiElement.getParent();

                FieldReference fieldReference = (FieldReference)parent;
                PhpExpression classReference = fieldReference.getClassReference();

                PsiElement originalPosition = completionParameters.getOriginalPosition();
                if (originalPosition == null) {
                    return;
                }
                PsiElement originalElement = originalPosition.getOriginalElement();
                PsiFile file = originalElement.getContainingFile();
                if (file == null) {
                    return;
                }
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile == null) {
                    return;
                }
                String nameWithoutExtension = virtualFile.getNameWithoutExtension();
                String controllerName = StringUtil.controllerBaseNameFromControllerFileName(nameWithoutExtension);
                if (controllerName == null) {
                    return;
                }
                if (classReference == null) {
                    return;
                }
                String fieldName = classReference.getName();
                PhpIndex phpIndex = PhpIndex.getInstance(psiElement.getProject());
                String controllerClassName = controllerName + "Controller";
                Collection<PhpClass> controllerClasses = phpIndex.getClassesByFQN(controllerClassName);
                if (controllerClasses.size() == 0) {
                    return;
                }

                Collection<PhpClass> modelClasses = phpIndex.getClassesByFQN(fieldName);
                PhpClass controllerClass = controllerClasses.iterator().next();
                CakeCompletionUtil.complete(modelClasses, controllerClass, completionResultSet);
                Collection<PhpClass> componentClasses = phpIndex.getClassesByFQN(fieldName + "Component");
                CakeCompletionUtil.complete(componentClasses, controllerClass, completionResultSet);
            }
        });
    }
}
