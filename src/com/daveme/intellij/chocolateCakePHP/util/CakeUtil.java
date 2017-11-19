package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.completion.PhpVariantsUtil;
import com.jetbrains.php.completion.UsageContext;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        completeFromFilesInDir(completionResultSet, appDir, subDir, "");
    }

    public static void completeFromFilesInDir(@NotNull CompletionResultSet completionResultSet,
                                               @NotNull PsiDirectory appDir,
                                               @NotNull String subDir,
                                               @NotNull String replaceName) {
        PsiDirectory psiSubdirectory = appDir.findSubdirectory(subDir);
        if (psiSubdirectory == null) {
            return;
        }
        // @todo we only need a single instance of these.
        CakeInsertHandler handler = new CakeInsertHandler(replaceName.isEmpty() ? "uses" : "components");
        for (PsiFile file : psiSubdirectory.getFiles()) {
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                continue;
            }
            String name = virtualFile.getNameWithoutExtension();
            String replaceText = StringUtil.chopFromEnd(name, replaceName);
            LookupElementBuilder lookupElement = LookupElementBuilder.create(replaceText)
                    .withIcon(PhpIcons.FIELD)
                    .withTypeText(name)
                    .withInsertHandler(handler);
            completionResultSet.addElement(lookupElement);
        }
    }

    public static void addValueToClassProperty(PhpFile phpFile, Document document, String property, String valueToAdd) {
        for (Map.Entry<String, Collection<PhpNamedElement>> entry: phpFile.getTopLevelDefs().entrySet()) {
            for (PhpNamedElement topLevelDef : entry.getValue()) {
                // todo handle adding to namespaced classes
                if (topLevelDef instanceof PhpClass) {
                    PhpClass klass = (PhpClass)topLevelDef;
                    Field field = klass.findOwnFieldByName(property, false);
                    if (field == null) {
                        continue;
                    }
                    if (appendToProperty(phpFile, document, valueToAdd, field)) {
                        return;
                    }
                }
            }
        }
    }

    private static boolean appendToProperty(PhpFile file, Document document, String valueToAdd, Field field) {
        PsiElement lastChild = field.getLastChild();
        if (lastChild != null && lastChild instanceof ArrayCreationExpression) {
            ArrayCreationExpression expr = (ArrayCreationExpression)lastChild;
            return PsiUtil.appendToArrayCreationExpression(file, document, expr, valueToAdd);
        }
        return false;
    }

    public static final class CakeInsertHandler implements InsertHandler<LookupElement> {
        String type;

        CakeInsertHandler(@NotNull String type) {
            this.type = type;
        }

        @Override
        public void handleInsert(InsertionContext insertionContext, LookupElement lookupElement) {
            System.out.println("handleInsert: "+lookupElement);
            PsiFile file = insertionContext.getFile();
            if (!(file instanceof PhpFile)) {
                return;
            }
            PhpFile phpFile = (PhpFile)file;
            CakeUtil.addValueToClassProperty(phpFile, insertionContext.getDocument(), type, lookupElement.getLookupString());
        }
    }
}
