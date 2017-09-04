package com.daveme.intellij.chocolateCakePHP.typeProvider;

import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class ControllerTypeProvider implements PhpTypeProvider3 {

    @Override
    public char getKey() {
        return '\u1762';
    }

    @Nullable
    @Override
    public PhpType getType(PsiElement psiElement) {
        if (!PlatformPatterns.psiElement(PhpElementTypes.FIELD_REFERENCE).accepts(psiElement)) {
            System.out.println("Skipping non field reference: "+psiElement.getClass().getCanonicalName());
            return null;
        }
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            System.out.println("Null containingFile");
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            System.out.println("Null virtualfile");
            return null;
        }
        String filename = virtualFile.getNameWithoutExtension();
        if (filename == null) {
            System.out.println("Null getNameWithoutExtension");
            return null;
        }
        System.out.println("path: "+filename);
        String controllerBaseName = StringUtil.controllerBaseNameFromControllerFileName(filename);
        if (controllerBaseName == null) {
            return null;
        }
        System.out.println("Controller Name: "+controllerBaseName);
        System.out.println("Interfaces: "+StringUtil.allInterfaces(psiElement.getClass()));
        FieldReference fieldReference = (FieldReference)psiElement;
        String fieldName = fieldReference.getName();
        System.out.println("fieldName: "+fieldName);
        if (StringUtils.isEmpty(fieldName)) {
            return null;
        }
        if (!Character.isUpperCase(fieldName.charAt(0))) {
            return null;
        }

        return new PhpType().add("#" + this.getKey() + fieldReference.getName());
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String expression, Set<String> set, int i, Project project) {
        return PhpIndex.getInstance(project).getClassesByFQN(expression);
    }
}
