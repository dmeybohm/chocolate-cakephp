package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
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

    @Nullable
    public static PsiFile convertVirtualFileToPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
        PsiManager psiManager = PsiManager.getInstance(project);
        return psiManager.findFile(file);
    }

    @Nullable
    public static String getFileNameWithoutExtension(@NotNull PsiElement psiElement) {
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
    public static Collection<PhpClass> getControllerFieldClasses(@NotNull String fieldName, @NotNull Project project) {
        Collection<PhpClass> result = new ArrayList<>();
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Collection<PhpClass> modelClasses =  phpIndex.getClassesByFQN(fieldName);
        Collection<PhpClass> componentClasses = phpIndex.getClassesByFQN(fieldName + "Component");
        result.addAll(modelClasses);
        result.addAll(componentClasses);
        return result;
    }

    @NotNull
    public static Collection<PhpClass> getViewHelperClasses(@NotNull String fieldName, @NotNull Project project) {
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        return phpIndex.getClassesByFQN("\\" + fieldName + "Helper");
    }

    @NotNull
    public static PsiElement[] getClassesAsArray(@NotNull PsiElement psiElement, String className) {
        Collection<PhpClass> helperClasses = getClasses(psiElement, className);
        return helperClasses.toArray(new PsiElement[helperClasses.size()]);
    }

    @NotNull
    private static Collection<PhpClass> getClasses(@NotNull PsiElement psiElement, String className) {
        PhpIndex phpIndex = PhpIndex.getInstance(psiElement.getProject());
        return phpIndex.getClassesByFQN(className);
    }

    @Nullable
    public static PsiElement findParent(@NotNull PsiElement element, @NotNull Class<? extends PsiElement> clazz) {
        while (true) {
            PsiElement parent = element.getParent();
            if (parent == null) {
                break;
            }
            if (PlatformPatterns.psiElement(clazz).accepts(parent)) {
                return parent;
            }
            element = parent;
        }
        return null;
    }

    @Nullable
    private static PsiElement findFirstChild(@NotNull PsiElement element, @NotNull Class<? extends PsiElement> clazz) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
            if (PlatformPatterns.psiElement(clazz).accepts(child)) {
               return child;
            }
            PsiElement grandChild = findFirstChild(child, clazz);
            if (grandChild != null) {
                return grandChild;
            }
        }
        return null;
    }

    @Nullable
    public static PsiDirectory getAppDirectoryFromFile(@NotNull PsiFile file) {
        PsiDirectory dir = file.getContainingDirectory();
        // @todo determine what happens here when app directory doesn't exist
        while (dir != null) {
            if (dir.getName().equals("app")) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    public static void dumpAllParents(@NotNull PsiElement element) {
        System.out.print("element: "+element.getClass()+" {");
        while (true) {
            PsiElement parent = element.getParent();
            if (parent == null) {
                break;
            }
            System.out.print("(" + StringUtil.allInterfaces(parent.getClass()));
            System.out.print("), ");
            element = parent;
        }
        System.out.println("}");
    }

    @NotNull
    public static String dumpAllChildren(@NotNull PsiElement element) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("[ ");
        for (PsiElement child : element.getChildren()) {
            buffer.append(StringUtil.allInterfaces(child.getClass()));
            buffer.append(",");
        }
        buffer.append("]");
        return buffer.toString();
    }

    @Nullable
    public static StringLiteralExpression createLiteralString(Project project, CharSequence str) {
        return PhpPsiElementFactory.createFromText(project,
                StringLiteralExpression.class,
                String.format("'%s'", str));
    }

    public static boolean appendToArrayCreationExpression(PhpFile file, Document document, String valueToAdd, ArrayCreationExpression expr) {
        PsiElement lastElement = expr.getLastChild();
        for (PsiElement child : expr.getChildren()) {
            if (!(child instanceof PhpPsiElement)) {
                continue;
            }
            PhpPsiElement element = (PhpPsiElement)child;
            PhpPsiElement firstPsiChild = element.getFirstPsiChild();
            if (firstPsiChild instanceof StringLiteralExpression) {
                StringLiteralExpression stringValue = (StringLiteralExpression)firstPsiChild;
                if (valueToAdd.equals(stringValue.getContents())) {
                    // already exists:
                    System.out.println("Model property already exists");
                    return true;
                }
            }
        }
        Project project = expr.getProject();
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        StringLiteralExpression fromText = PsiUtil.createLiteralString(project, valueToAdd);
        if (fromText == null) {
            return false;
        }
        PsiElement prevSibling = getPrevSiblingSkippingWhitespaceAndComments(lastElement);
        if (prevSibling == null) {
            return false;
        }
        String prevSiblingText = prevSibling.getText();
        if (prevSiblingText.equals(",")) {
            prevSibling = getPrevSiblingSkippingWhitespaceAndComments(prevSibling);
            if (prevSibling == null) {
                return false;
            }
        }
        System.out.println("prevSibling: "+prevSibling.getText());
        int extraLen = fromText.getText().length();
        try {
            if (!prevSiblingText.equals("(") && !prevSiblingText.equals("[")) {
                PsiElement comma = PhpPsiElementFactory.createComma(project);
                prevSibling.addAfter(comma, expr);
                extraLen += comma.getText().length();
                prevSibling.addAfter(fromText, expr);
            } else {
                TextRange prevSiblingTextRange = prevSibling.getTextRange();
                document.insertString(prevSiblingTextRange.getEndOffset(), fromText.getText());
            }
        } catch (IncorrectOperationException e) {
            System.out.println("IncorrectOperationException");
            return false;
        }
        TextRange exprTextRange = expr.getTextRange();
        int end = exprTextRange.getEndOffset() + extraLen;
        codeStyleManager.reformatText(file, exprTextRange.getStartOffset(), end);
        return true;
    }

    @Nullable
    public static PsiElement getPrevSiblingSkippingWhitespaceAndComments(PsiElement element) {
        while (element != null) {
            element = element.getPrevSibling();
            if (!(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
                break;
            }
        }
        return element;
    }
}
