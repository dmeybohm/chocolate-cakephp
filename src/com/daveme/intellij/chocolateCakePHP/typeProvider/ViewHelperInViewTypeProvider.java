package com.daveme.intellij.chocolateCakePHP.typeProvider;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
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

public class ViewHelperInViewTypeProvider implements PhpTypeProvider3 {
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
        if (!CakeUtil.isCakeTemplate(psiElement.getContainingFile().getName())) {
            return null;
        }
        FieldReference fieldReference = (FieldReference)psiElement;
        PhpExpression classReference = fieldReference.getClassReference();
        if (classReference == null) {
            return null;
        }
        String fieldReferenceName = fieldReference.getName();
        if (!StringUtil.startsWithUppercaseCharacter(fieldReferenceName)) {
            return null;
        }
        if (classReference.getText().equals("$this")) {
            return new PhpType().add("\\" + fieldReferenceName + "Helper");
        }
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String s, Set<String> set, int i, Project project) {
        return null;
    }
}
