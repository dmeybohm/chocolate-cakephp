package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
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

    public static void addValueToClassProperty(Document document, PhpFile phpFile, String property, String valueToAdd) {
        for (Map.Entry<String, Collection<PhpNamedElement>> entry: phpFile.getTopLevelDefs().entrySet()) {
            System.out.println("entry: "+entry.getKey());
            for (PhpNamedElement topLevelDef : entry.getValue()) {
                // todo handle adding to namespaced classes
                if (topLevelDef instanceof PhpClass) {
                    PhpClass klass = (PhpClass)topLevelDef;
                    Field field = klass.findOwnFieldByName(property, false);
                    if (field == null) {
                        continue;
                    }
                    PsiElement[] children = field.getChildren();
                    if (children.length > 0 && children[0] instanceof ArrayCreationExpression) {
                        ArrayCreationExpression expr = (ArrayCreationExpression)children[0];
                        for (PsiElement child : expr.getChildren()) {
                            PhpPsiElement element = (PhpPsiElement)child;
                            if (element != null) {
                                PhpPsiElement firstPsiChild = element.getFirstPsiChild();
                                if (firstPsiChild instanceof StringLiteralExpression) {
                                    StringLiteralExpression stringValue = (StringLiteralExpression)firstPsiChild;
                                    if (stringValue.getContents().equals(valueToAdd)) {
                                        // already exists:
                                        System.out.println("Model property already exists");
                                        return;
                                    }
                                }
                            }
                        }
                        PsiElement lastChild = expr.getLastChild();
                        if (lastChild != null) {
                            PsiElement prevLastChild = lastChild.getPrevSibling();
                            if (prevLastChild != null) {
                                TextRange textRange = prevLastChild.getTextRange();
                                document.insertString(textRange.getEndOffset(), ", '" + valueToAdd + "'");
                            }
                        }
                    }
                }
            }
        }
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
            System.out.println("lookupString: "+lookupElement.getLookupString());
            CakeUtil.addValueToClassProperty(insertionContext.getDocument(), phpFile, type, lookupElement.getLookupString());
        }
    }
}
