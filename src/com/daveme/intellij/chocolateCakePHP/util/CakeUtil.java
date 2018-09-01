package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.completion.PhpVariantsUtil;
import com.jetbrains.php.completion.UsageContext;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CakeUtil {

    // TODO make this configurable
    private static final String TEMPLATE_EXT = "ctp";

    private static final HashSet<String> helperBlacklist = new HashSet<>();

    static {
        // These all have the Helper suffix removed (the ones that have it have it twice):
        helperBlacklist.add("Html5Test");
        helperBlacklist.add("OtherHelper");
        helperBlacklist.add("OptionEngine");
        helperBlacklist.add("PluggedHelper");
        helperBlacklist.add("HtmlAlias");
        helperBlacklist.add("TestHtml");
        helperBlacklist.add("TestPluginApp");
        helperBlacklist.add("TimeHelperTestObject");
        helperBlacklist.add("NumberHelperTestObject");
        helperBlacklist.add("TextHelperTestObject");
    }

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

    private static void addValueToClassProperty(PhpFile phpFile, Document document, String property, String valueToAdd) {
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
        if (lastChild instanceof ArrayCreationExpression) {
            ArrayCreationExpression expr = (ArrayCreationExpression)lastChild;
            return PsiUtil.appendToArrayCreationExpression(file, document, valueToAdd, expr);
        }
        return false;
    }

    public static void completeFromSubclasses(
            @NotNull CompletionResultSet completionResultSet,
            @NotNull Project project,
            @NotNull String parentClassName
    ) {
        PhpIndex index = PhpIndex.getInstance(project);
        for (PhpClass klass : index.getAllSubclasses(parentClassName)) {
            String helperNameAsPropertyName = StringUtil.chopFromEnd(klass.getName(), "Helper");

            // skip helpers that have "Test" to avoid tests:
            if (helperBlacklist.contains(helperNameAsPropertyName)) {
                continue;
            }
            LookupElementBuilder lookupElement = LookupElementBuilder.create(helperNameAsPropertyName)
                    .withIcon(PhpIcons.FIELD)
                    .withTypeText(klass.getType().toString());
            completionResultSet.addElement(lookupElement);
        }
    }

    public static final class CakeInsertHandler implements InsertHandler<LookupElement> {
        String type;

        CakeInsertHandler(@NotNull String type) {
            this.type = type;
        }

        @Override
        public void handleInsert(InsertionContext insertionContext, LookupElement lookupElement) {
            PsiFile file = insertionContext.getFile();
            if (!(file instanceof PhpFile)) {
                return;
            }
            PhpFile phpFile = (PhpFile)file;
            CakeUtil.addValueToClassProperty(phpFile, insertionContext.getDocument(), type, lookupElement.getLookupString());
        }
    }

    public static boolean isCakeTemplate(@NotNull String filename) {
        int last = filename.lastIndexOf('.');
        if (last > 0) {
            String ext = filename.substring(last + 1);
            return ext.equals(TEMPLATE_EXT);
        }
        return false;
    }
}
