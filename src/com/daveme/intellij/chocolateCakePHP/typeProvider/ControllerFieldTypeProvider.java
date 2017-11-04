package com.daveme.intellij.chocolateCakePHP.typeProvider;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3;
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
        if (!(psiElement instanceof FieldReference)) {
            return null;
        }
        FieldReference fieldReference = (FieldReference)psiElement;
        PhpExpression classReference = fieldReference.getClassReference();
        if (classReference == null) {
            return null;
        }
        PhpType referenceType = classReference.getType();
        String fieldReferenceName = fieldReference.getName();
        if (fieldReferenceName == null) {
            return null;
        }
        if (!Character.isUpperCase(fieldReferenceName.charAt(0))) {
            return null;
        }
        for (String type : referenceType.getTypes()) {
            if (type.contains("Controller")) {
                return new PhpType().add("\\" + fieldReferenceName)
                        .add("\\" + fieldReferenceName + "Component");
            }
        }
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String expression, Set<String> set, int i, Project project) {
        // We use the default signature processor exclusively:
        return Collections.emptyList();
    }
}
