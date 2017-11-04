package com.daveme.intellij.chocolateCakePHP.typeProvider;

import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class ViewHelperTypeProvider implements PhpTypeProvider3 {
    @Override
    public char getKey() {
        return 0;
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
        PhpType types = classReference.getType();
        String fieldReferenceName = fieldReference.getName();
        if (fieldReferenceName == null) {
            return null;
        }
        if (!Character.isUpperCase(fieldReferenceName.charAt(0))) {
            return null;
        }
        for (String type : types.getTypes()) {
            if (type.contains("#Vthis")) {
                return new PhpType().add("\\" + fieldReferenceName + "Helper");
            }
        }
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String s, Set<String> set, int i, Project project) {
        return null;
    }
}
