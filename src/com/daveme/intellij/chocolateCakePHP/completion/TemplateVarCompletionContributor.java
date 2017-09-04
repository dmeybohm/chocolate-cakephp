package com.daveme.intellij.chocolateCakePHP.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.lang.psi.elements.Variable;
import org.jetbrains.annotations.NotNull;

public class TemplateVarCompletionContributor extends CompletionContributor {

    public TemplateVarCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withParent(Variable.class), new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
                        PsiElement psiElement = completionParameters.getOriginalPosition().getOriginalElement();
                        System.out.println("psiElement: "+psiElement.getClass());
                        PsiFile file = psiElement.getContainingFile();
                        String name = file.getVirtualFile().getName();
                        System.out.println("name: "+name);
                        if (name.endsWith(".ctp")) {
                            LookupElementBuilder lookupElement = LookupElementBuilder.create("$FooBar").withIcon(PhpIcons.VARIABLE).withTypeText("FooBar").withPresentableText("FooBar");
                            completionResultSet.addElement(lookupElement);
                            completionResultSet.addElement(LookupElementBuilder.create("$Foobaz").withTypeText("Foobar"));

//                            completionResultSet.addElement(LookupElementBuilder.create("Foobar").withTypeText("Foobar"));
//                            completionResultSet.stopHere();
                        }
                    }
                });
    }

}
