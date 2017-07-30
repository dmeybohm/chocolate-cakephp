package com.daveme.intellij.chocolateCakePHP;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringUtil {

    @Nullable
    public static String lastOccurrenceOf(String haystack, String needle) {
        StringBuilder result = new StringBuilder();
        String path = null;
        for (String part : haystack.split("/")) {
            result.append(part);
            if (part.equals(needle)) {
                path = result.toString();
            }
            result.append("/");
        }
        return path;
    }

    @Nullable
    public static String controllerBaseNameFromControllerFileName(String controllerClass) {
        if (!controllerClass.endsWith("Controller")) {
            return null;
        }
        return controllerClass.substring(0, controllerClass.length() - "Controller".length());
    }

    @NotNull
    public static String allInterfaces(Class klass) {
        Class<?>[] interfaces = klass.getInterfaces();
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        for (Class<?> intface : interfaces) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(intface.getCanonicalName());
        }
        builder.append("}");
        return builder.toString();
    }
}
