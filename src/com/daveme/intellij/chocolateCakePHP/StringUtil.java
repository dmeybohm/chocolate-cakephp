package com.daveme.intellij.chocolateCakePHP;

import org.jetbrains.annotations.Nullable;

/**
 * Created by dmeybohm on 7/29/17.
 */
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
}
