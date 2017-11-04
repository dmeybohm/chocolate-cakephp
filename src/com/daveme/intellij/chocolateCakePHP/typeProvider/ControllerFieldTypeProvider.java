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
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.*;
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
//        String filename = PsiUtil.getFileNameWithoutExtension(psiElement);
//        if (filename == null) {
//            System.out.println("No filename");
//            return null;
//        }
//        // @todo don't do this based on filename, but on the variable type.
//        String controllerBaseName = CakeUtil.controllerBaseNameFromControllerFileName(filename);
//        if (controllerBaseName == null) {
//            System.out.println("No controller");
//            return null;
//        }

        System.out.println("psiElement: "+StringUtil.allInterfaces(psiElement.getClass()));
        if (!(psiElement instanceof FieldReference)) {
            return null;
        }
        FieldReference fieldReference = (FieldReference)psiElement;
        PhpExpression classReference = fieldReference.getClassReference();
        if (classReference != null) {
            PhpType referenceType = classReference.getType();
            Set<String> types = referenceType.getTypes();
            for (String type : types) {
                if (type.contains("Controller")) {
                    String className = fieldReference.getName();
                    System.out.println("*** found controller type");
                    return new PhpType().add("\\" + className)
                            .add("\\" + className + "Component");
                }
            }
        }
        return null;
        //System.out.println("getType: psiElement: "+StringUtil.allInterfaces(psiElement.getClass()));
        /*
        PsiElementPattern.Capture<PsiElement> fieldReferenceCapture = PlatformPatterns.psiElement(PhpElementTypes.FIELD_REFERENCE);
        if (PlatformPatterns.psiElement(PhpElementTypes.METHOD_REFERENCE).withFirstChild(fieldReferenceCapture).accepts(psiElement)) {
            FieldReference fieldReference = (FieldReference) psiElement.getFirstChild();
            System.out.println("fqn: " + fieldReference.getFQN());
            PhpExpression expression = fieldReference.getClassReference();
            MethodReference methodReference = (MethodReference)psiElement;
            System.out.println("signature: "+methodReference.getSignature());
            System.out.println("classReference: " + StringUtil.allInterfaces(expression.getClass()));
            System.out.println("has Field Reference: " + psiElement.getText());
            System.out.println("interfaces: " + StringUtil.allInterfaces(psiElement.getClass()));
//            return new PhpType().add("#" + this.getKey() + "$m" + fieldReference.getName());
            return new PhpType().add("#" + this.getKey() + methodReference.getSignature());
//            String className = fieldReference.getName();
//            String methodName = methodReference.getName();
//            System.out.println("Returning method");
//            return new PhpType().add("#C\\" + className + "." + methodName)
//                                .add("#C\\" + className + "Component." + methodName);
        }
        if (!PlatformPatterns.psiElement(PhpElementTypes.FIELD_REFERENCE).accepts(psiElement)) {
            return null;
        }
        String filename = PsiUtil.getFileNameWithoutExtension(psiElement);
        if (filename == null) {
            return null;
        }
        // @todo don't do this based on filename, but on the variable type.
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
        //return new PhpType().add("\\" + className)
        //                    .add("\\" + className + "Component");
        System.out.println("getType: returning {" + className + ", " + className + "Component}");
        return new PhpType().add("#C\\" + className)
                            .add("#C\\" + className + "Component");
        //return new PhpType().add("#" + this.getKey() + fieldReference.getName());
        */
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String expression, Set<String> set, int i, Project project) {
//        System.out.println("getBySignature: "+expression);
//        PhpIndex phpIndex = PhpIndex.getInstance(project);
//        Collection<? extends PhpNamedElement> bySignature = PsiUtil.getTypeSignature(phpIndex, expression);
//        System.out.println("getBySignature: getTypeSignature.size(): "+bySignature.size());
//        System.out.println("classes size: "+bySignature.size());
//        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
////        dumpStackTrace();
//        if (expression.contains("Foobar")) {
//            System.out.println("getBySignature: expression("+ expression+" ) -> \\Foobar");
//            return phpIndex.getClassesByFQN("\\Foobar");
//        } else {
//            System.out.println("getBySignature: expression("+ expression+" ) -> \\Other");
//            return phpIndex.getClassesByFQN("\\Other");
//        }
        /*if (bySignature.size() > 0) {
            PhpNamedElement next = bySignature.iterator().next();
            Method method = (Method)next;
            System.out.println("getBySignature: fqn: "+ next.getFQN());
            System.out.println("interfaces: "+StringUtil.allInterfaces(next.getClass()));
            PhpDocComment docComment = method.getDocComment();
            if (docComment != null) {
                System.out.println("return type: " + docComment.getReturnTag().getText());
                String returnTag = docComment.getReturnTag().getText();
                String returnType = returnTag.replace("@return ", "").trim();
                System.out.println("returnType: "+returnType);
                Collection<PhpClass> classesByFQN = phpIndex.getClassesByFQN(returnType);
                System.out.println("classesByFQN.size: "+classesByFQN.size());
                if (classesByFQN.size() > 0) {
                    return classesByFQN;
                }
            }
            if (method.getReturnType() != null) {
                // hmm...
            }
        }
        return bySignature;
        // We utilize the default signature processor exclusively, so nothing to return here:
        */
        return Collections.emptyList();
    }

//    private static void dumpStackTrace() {
//        System.out.println("stackTrace:");
//        for (StackTraceElement element : stackTrace) {
//            System.out.println(""+element.toString());
//        }
//    }

}
