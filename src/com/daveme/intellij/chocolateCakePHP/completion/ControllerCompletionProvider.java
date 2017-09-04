package com.daveme.intellij.chocolateCakePHP.completion;

import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.completion.PhpVariantsUtil;
import com.jetbrains.php.completion.UsageContext;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class ControllerCompletionProvider extends CompletionContributor {

    public static PhpType CONTROLLER_TYPE = PhpType.builder().add("\\AppController").build();

    public ControllerCompletionProvider() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(FieldReference.class), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
                System.err.println("*** addCompletions: called!");
                PsiElement psiElement = completionParameters.getPosition().getOriginalElement();
                PsiElement parent = psiElement.getParent();

                FieldReference fieldReference = (FieldReference)parent;
                PhpExpression classReference = fieldReference.getClassReference();

                PsiElement originalElement = completionParameters.getOriginalPosition().getOriginalElement();
                PsiFile file = originalElement.getContainingFile();
                if (file == null) {
                    System.err.println("null file");
                    return;
                }
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile == null) {
                    System.err.println("null virtualfile");
                    return;

                }
                String nameWithoutExtension = virtualFile.getNameWithoutExtension();
                String controllerName = StringUtil.controllerBaseNameFromControllerFileName(nameWithoutExtension);
                if (controllerName == null) {
                    System.err.println("null controllerName");
                    return;
                }

                String className = classReference.getName();
                PhpIndex phpIndex = PhpIndex.getInstance(psiElement.getProject());
                String controllerClassName = controllerName + "Controller";
                System.err.println("controllerClassName: "+controllerClassName);
                Collection<PhpClass> controllerClasses = phpIndex.getClassesByFQN(controllerClassName);
                if (controllerClasses == null || controllerClasses.size() == 0) {
                    System.err.println("Missing controller class...?");
                    return;
                }
                Collection<PhpClass> classes = phpIndex.getClassesByFQN(className);
                UsageContext usageContext = new UsageContext(PhpModifier.State.DYNAMIC);
                usageContext.setTargetObjectClass(controllerClasses.iterator().next());
                System.err.println("classes.size: "+classes.size());
                for (PhpClass klass: classes) {
                    try {
                        List<LookupElement> lookupItems = PhpVariantsUtil.getLookupItems(klass.getMethods(), false, usageContext);
                        System.err.println("lookupItems.length: "+lookupItems.size());
                        completionResultSet.addAllElements(lookupItems);
                    } catch (Exception e) {
                        System.err.println("Exception: "+e.getMessage());
                        return;
                    }
                }
//                for (PhpClass klass : classes) {
//                    for (Method method : klass.getMethods()) {
//                        if (!method.getAccess().isPublic() || method.getName().equals("__construct")) {
//                            continue;
//                        }
//
//                        String methodName = method.getName();
//                        PsiElement returnTypeElement = method.getReturnType();
//                        String returnTypeText = returnTypeElement == null ? "" : returnTypeElement.getText();
//                        System.err.println("*** addCompletions: returnType: "+returnTypeElement);
//                        LookupElementBuilder lookupElement = LookupElementBuilder.create(methodName)
//                                .withIcon(PhpIcons.METHOD_ICON)
//                                .withTypeText(returnTypeText)
//                                .withPresentableText(methodName);
//                        completionResultSet.addElement(lookupElement);
//                    }
//
//                }
            }
        });
    }
}
