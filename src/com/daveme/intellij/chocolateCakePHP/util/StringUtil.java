package com.daveme.intellij.chocolateCakePHP.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringUtil {

    @Nullable
    public static String lastOccurrenceOf(@NotNull String haystack, @NotNull String needle) {
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

    @NotNull
    static String allInterfaces(@NotNull Class klass) {
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

    public static boolean startsWithUppercaseCharacter(@Nullable String str) {
        return str != null && str.length() != 0 && Character.isUpperCase(str.charAt(0));
    }

}
