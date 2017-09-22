package com.daveme.intellij.chocolateCakePHP.typeProvider;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ControllerFieldTypeProvider implements PhpTypeProvider3 {

    @Override
    public char getKey() {
        return '\u1762';
    }

    @Nullable
    @Override
    public PhpType getType(PsiElement psiElement) {
//        PsiElementPattern.Capture<PsiElement> fieldReferenceCapture = PlatformPatterns.psiElement(PhpElementTypes.FIELD_REFERENCE);
//        if (PlatformPatterns.psiElement(PhpElementTypes.METHOD_REFERENCE).withFirstChild(fieldReferenceCapture).accepts(psiElement)) {
//            FieldReference fieldReference = (FieldReference) psiElement.getFirstChild();
//            System.out.println("fqn: " + fieldReference.getFQN());
//            PhpExpression expression = fieldReference.getClassReference();
//            MethodReference methodReference = (MethodReference)psiElement;
//            System.out.println("signature: "+methodReference.getSignature());
//            System.out.println("classReference: " + StringUtil.allInterfaces(expression.getClass()));
//            System.out.println("has Field Reference: " + psiElement.getText());
//            System.out.println("interfaces: " + StringUtil.allInterfaces(psiElement.getClass()));
//            return new PhpType().add("#" + this.getKey() + "$m" + fieldReference.getName());
//        }
        if (!PlatformPatterns.psiElement(PhpElementTypes.FIELD_REFERENCE).accepts(psiElement)) {
            return null;
        }
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        String filename = virtualFile.getNameWithoutExtension();
        String controllerBaseName = CakeUtil.controllerBaseNameFromControllerFileName(filename);
        if (controllerBaseName == null) {
            return null;
        }
        FieldReference fieldReference = (FieldReference)psiElement;
        String fieldName = fieldReference.getName();
        if (StringUtils.isEmpty(fieldName)) {
            return null;
        }
        if (!Character.isUpperCase(fieldName.charAt(0))) {
            return null;
        }
        String className = fieldReference.getName();
        return new PhpType().add("#C\\" + className)
                            .add("#C\\" + className + "Component");
        //return new PhpType().add("#" + this.getKey() + fieldReference.getName());
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String expression, Set<String> set, int i, Project project) {
        // We utilize the default signature processor exclusively, so nothing to return here:
        return Collections.emptyList();
    }

}
